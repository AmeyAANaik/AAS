# Branch Operations - Complete Guide

## Overview

Branch Operations provides a **branch-centric view** of all order and receivables activities. It tracks branch order pipelines, outstanding amounts, payment collection rates, and financial health.

---

## Branch Operations Dashboard

### Summary View (All Branches)

```
User navigates to: Operations > Branch Ops
    ↓
GET /api/branch-ops/summary
    ↓
Backend: BranchOpsService.getSummary()
  ├─ Fetch all active branches (customers)
  ├─ For each branch, calculate:
  │  ├─ Pending order count (status != INVOICED)
  │  ├─ Open receivable amount (outstanding invoices)
  │  ├─ Payment collection rate (% of invoices paid vs issued)
  │  ├─ Average payment days (how long to collect)
  │  ├─ Last activity date
  │  └─ Settlement status (SETTLED/OPEN/OVERDUE)
  │
  └─ Return: BranchSummaryRow[] (one row per branch)
    ↓
Display table:
├─ Branch Name
├─ Pending Orders (count)
├─ Open Receivable Amount (₹)
├─ Payment Collection Rate (%)
├─ Average Payment Days
├─ Settlement Status (badge)
└─ Last Activity (timestamp)
```

### Summary Data Structure

```
BranchSummaryRow {
  branchId: string;
  branchName: string;
  location: string;
  pendingOrders: number;          // Count of non-invoiced orders
  openReceivable: number;         // Total outstanding invoices
  paymentCollectionRate: number;  // % of invoices paid
  averagePaymentDays: number;     // Days to collect payment
  totalOrdersLifetime: number;    // All orders ever
  totalRevenueLifetime: number;   // Total sales
  settlementStatus: 'SETTLED' | 'OPEN' | 'OVERDUE';
  lastActivityDate: Date;
}
```

---

## Per-Branch Drill-Down

### Branch Detail View

```
User clicks on branch name in summary table
    ↓
GET /api/branch-ops/{branchId}
    ↓
Backend: BranchOpsService.getBranchDetail()
  ├─ Load branch profile
  ├─ Load all orders (in all statuses)
  ├─ Load all invoices
  ├─ Calculate KPIs:
  │  ├─ Total orders
  │  ├─ Pending orders
  │  ├─ Total revenue
  │  ├─ Outstanding receivable
  │  ├─ Payment collection rate
  │  ├─ Average order value
  │  ├─ Average days to payment
  │  └─ Overdue invoices count
  │
  ├─ Load branch ledger
  │  ├─ Double-entry format
  │  ├─ Debit: invoices issued to branch
  │  ├─ Credit: payments received from branch
  │  └─ Running balance (receivable)
  │
  └─ Return: BranchDetailView
    ↓
Display:
├─ Branch Profile:
│  ├─ Name
│  ├─ Location
│  ├─ Contact info
│  ├─ Credit days
│  └─ Payment terms
│
├─ KPI Cards:
│  ├─ Total Pending Orders
│  ├─ Open Receivable
│  ├─ Payment Collection Rate (%)
│  ├─ Average Payment Days
│  ├─ Overdue Invoices
│  └─ Average Order Value
│
├─ Billing Summary:
│  ├─ Total Invoiced
│  ├─ Total Paid
│  ├─ Outstanding
│  └─ Last Invoice Date
│
└─ Tabs:
   ├─ Orders Tab
   ├─ Invoices Tab
   ├─ Ledger Tab
   └─ Exceptions Tab
```

### Branch Detail Data

```
BranchDetailView {
  branchId: string;
  branchName: string;
  
  profile: {
    location: string;
    contactPerson: string;
    email: string;
    phone: string;
    creditDays: number;
    whatsappGroup: string;
  };
  
  kpis: {
    totalOrders: number;
    pendingOrders: number;
    invoicedOrders: number;
    totalRevenue: number;
    openReceivable: number;
    paymentCollectionRate: number;  // %
    averagePaymentDays: number;
    overdueInvoices: number;
    averageOrderValue: number;
  };
  
  billingSummary: {
    totalInvoiced: number;
    totalPaid: number;
    outstanding: number;
    lastInvoiceDate: Date;
  };
}
```

---

## Branch Orders View

### Order Listing for Branch

```
In branch detail, user clicks: "Orders" tab
    ↓
GET /api/branch-ops/{branchId}/orders
    ↓
Backend: BranchOpsService.getBranchOrders()
  ├─ Fetch all orders for branch
  ├─ For each order, include:
  │  ├─ Order ID & date
  │  ├─ Vendor assigned
  │  ├─ Current status
  │  ├─ PDF status (vendor invoice)
  │  ├─ Bill status (captured)
  │  ├─ Cost total (vendor costs)
  │  ├─ Sell total (with margin)
  │  ├─ Margin %
  │  └─ Invoice link (if invoiced)
  │
  └─ Return: BranchOrderRow[]
    ↓
Display table:
├─ Order ID (clickable)
├─ Date
├─ Vendor
├─ Status (color-coded badge)
├─ Cost Total (₹)
├─ Sell Total (₹)
├─ Margin %
├─ Invoice ID (if invoiced)
└─ Actions (drill-down)
```

---

## Branch Invoices View

### Invoice Listing for Branch

```
In branch detail, user clicks: "Invoices" tab
    ↓
GET /api/branch-ops/{branchId}/invoices
    ↓
Backend: BranchOpsService.getBranchInvoices()
  ├─ Fetch all invoices for branch
  ├─ For each invoice, include:
  │  ├─ Invoice ID & date
  │  ├─ Amount
  │  ├─ Due date (based on credit days)
  │  ├─ Payment status
  │  ├─ Amount paid
  │  ├─ Outstanding (amount - paid)
  │  ├─ Days overdue (if past due)
  │  └─ GST amount (if applicable)
  │
  └─ Return: BranchInvoiceRow[]
    ↓
Display table:
├─ Invoice ID (clickable)
├─ Date
├─ Amount (₹)
├─ Due Date
├─ Paid (₹)
├─ Outstanding (₹)
├─ Status (Paid/Partial/Pending)
├─ Days Overdue (if overdue)
└─ Actions (payment, view details)
```

### Invoice Status Logic

```
Invoice Status Determination:

If Outstanding = 0:
  Status = PAID ✓

Else if Outstanding > 0 and Today <= Due Date:
  Status = PENDING ℹ️

Else if Outstanding > 0 and Today > Due Date:
  Days Overdue = Today - Due Date
  Status = OVERDUE ⚠️
```

---

## Branch Ledger

### Double-Entry Ledger Format

```
In branch detail, user clicks: "Ledger" tab
    ↓
GET /api/branch-ops/{branchId}/ledger
    ↓
Backend: BranchOpsService.getBranchLedger()
  ├─ Fetch all transactions for branch:
  │  ├─ Invoices created (debit = money owed by branch)
  │  └─ Payments received (credit = money paid by branch)
  │
  ├─ Sort by date
  ├─ Calculate running balance
  │  └─ Balance = Previous + Debit - Credit (receivable)
  │
  └─ Return: LedgerEntry[]
    ↓
Display ledger table:
├─ Date
├─ Voucher Type (Invoice/Payment)
├─ Voucher Number
├─ Description
├─ Debit (Invoices - money owed) (₹)
├─ Credit (Payments - money received) (₹)
└─ Balance (receivable outstanding) (₹)

Example:
  Date       | Type      | Voucher    | Description    | Debit  | Credit | Balance
  2026-01-10 | Invoice   | INV-0001   | Order from Jan | 5000   |        | 5000
  2026-01-20 | Payment   | PAY-0001   | Partial rcvd   |        | 2000   | 3000
  2026-02-05 | Invoice   | INV-0002   | February order | 6000   |        | 9000
  2026-02-15 | Payment   | PAY-0002   | Full settlement|        | 9000   | 0
```

### Ledger Data Structure

```
LedgerEntry {
  date: Date;
  voucherType: 'Invoice' | 'Payment';
  voucherNumber: string;
  description: string;
  debit: number;          // Money owed by branch
  credit: number;         // Money paid by branch
  balance: number;        // Running outstanding receivable
  referenceId: string;    // Invoice/payment ID
}
```

### Balance Interpretation

```
Balance = 0:    SETTLED ✓
  └─ All invoices paid, no outstanding
  
Balance > 0:    OPEN ℹ️
  └─ Outstanding receivable (money owed by branch)
  
Balance > 0 and
Oldest entry > DueDate:  OVERDUE ⚠️
  └─ Payment overdue, follow-up needed
```

---

## Ledger Export

### CSV Export

```
In branch detail, user clicks: "Export Ledger"
    ↓
GET /api/branch-ops/{branchId}/ledger/export
    ↓
Backend: BranchOpsService.exportLedger()
  ├─ Generate CSV with all ledger entries
  ├─ Include summary footer:
  │  ├─ Total Debits (total invoiced)
  │  ├─ Total Credits (total payments received)
  │  └─ Final Balance (outstanding)
  │
  └─ Download as: BRANCH_LEDGER_{BRANCHID}_{DATE}.csv
    ↓
User opens in Excel/Sheets
├─ Can analyze collection patterns
├─ Can filter by date range
├─ Can calculate payment delays
└─ Can track growth trends
```

---

## Outstanding Analysis

### Calculate Outstanding Amount

```
BranchOpsService.calculateOutstanding():

For each invoice:
  ├─ Amount = Invoice Total
  ├─ Paid = Sum of payments received
  ├─ Outstanding = Amount - Paid
  │
  └─ If Outstanding > 0:
      └─ Add to "Open Receivables"

Total Outstanding = Sum of all open invoice amounts
```

### Outstanding by Age

```
Bucket outstanding by days unpaid:

┌─────────────────────────────────┐
│ Outstanding Aging Analysis      │
├─────────────────────────────────┤
│ Current (0-30 days)  : ₹ 2,000  │
│ 30-60 days           : ₹ 3,500  │
│ 60-90 days           : ₹ 1,500  │
│ > 90 days            : ₹ 500    │
├─────────────────────────────────┤
│ Total Outstanding    : ₹ 7,500  │
└─────────────────────────────────┘
```

---

## Settlement States & Health

### Settlement Status Calculation

```
BranchOpsService.determineSettlementStatus():

1. GET FINAL BALANCE
   └─ Last entry in ledger (outstanding amount)

2. CHECK BALANCE
   ├─ If Balance = 0:
   │  └─ Status = SETTLED ✓
   │
   └─ If Balance > 0:
      ├─ Get oldest unpaid invoice date
      ├─ Due Date = Invoice Date + Branch.credit_days
      ├─ Days Overdue = Today - Due Date
      │
      ├─ If days overdue > 0:
      │  └─ Status = OVERDUE ⚠️
      │
      └─ Else:
         └─ Status = OPEN ℹ️
```

### Branch Health Score

```
Health Assessment Based on:

1. Outstanding Amount vs Lifetime Revenue
   ├─ If Outstanding < 10% Revenue → Healthy
   ├─ If Outstanding 10-30% Revenue → Caution
   └─ If Outstanding > 30% Revenue → Risk

2. Payment Collection Rate
   ├─ If Rate > 90% → Excellent
   ├─ If Rate 70-90% → Good
   └─ If Rate < 70% → Poor

3. Overdue Invoices Count
   ├─ If 0 overdue → Good
   ├─ If 1-2 overdue → Monitor
   └─ If > 2 overdue → Action needed

Overall Health = Weighted score of above factors
```

---

## Exceptions & Alerts

### Exception Types

```
BranchOpsService.getExceptions():

1. HIGH OUTSTANDING
   ├─ Condition: Outstanding > 30% of lifetime revenue
   ├─ Issue: Branch owes significant amount
   └─ Alert: "High receivables, consider follow-up"

2. OVERDUE INVOICES
   ├─ Condition: Invoice is past due date
   ├─ Issue: Payment overdue
   └─ Alert: "Invoice overdue by X days"

3. SLOW PAYMENT RATE
   ├─ Condition: Collection rate < 70%
   ├─ Issue: Branch has slow payment history
   └─ Alert: "Consider tightening credit terms"

4. PENDING ORDERS
   ├─ Condition: Branch has > 5 pending orders
   ├─ Issue: Large pipeline awaiting invoicing
   └─ Alert: "Large pending order pipeline"

5. ZERO ACTIVITY
   ├─ Condition: No activity for > 60 days
   ├─ Issue: Customer may be inactive
   └─ Alert: "Customer inactive, check relationship"
```

---

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│ BRANCH OPS SUMMARY VIEW                                  │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ GET /api/branch-ops/summary                              │
│ ├─ Fetch all branches                                    │
│ ├─ For each branch:                                      │
│ │  ├─ Count pending orders                              │
│ │  ├─ Sum open receivable (unpaid invoices)             │
│ │  ├─ Calculate payment collection rate                 │
│ │  └─ Determine settlement status                       │
│ │                                                        │
│ └─ Return: Summary table with KPIs                      │
│                                                           │
│ Display:                                                  │
│ ├─ All branches in list                                  │
│ ├─ Health indicators (colors)                            │
│ ├─ Quick metrics (receivables, rates)                    │
│ └─ Action buttons (drill-down)                           │
└──────────────────────────────────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────────┐
│ PER-BRANCH DRILL-DOWN                                    │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ GET /api/branch-ops/{branchId}                           │
│ ├─ Load branch profile                                   │
│ ├─ Calculate all KPIs                                    │
│ ├─ Load orders list                                      │
│ ├─ Load invoices list                                    │
│ ├─ Load ledger (all transactions)                        │
│ └─ Detect exceptions                                     │
│                                                           │
│ Display tabs:                                             │
│ ├─ Profile & KPIs                                        │
│ ├─ Orders (status, amounts, vendors)                     │
│ ├─ Invoices (due date, payment status)                   │
│ ├─ Ledger (double-entry, running balance)                │
│ └─ Exceptions (alerts, action items)                     │
└──────────────────────────────────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────────┐
│ OUTSTANDING RECEIVABLES ANALYSIS                         │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ Calculate for each branch:                               │
│ ├─ Total invoiced (all invoices)                         │
│ ├─ Total paid (all payments)                             │
│ ├─ Outstanding = Invoiced - Paid                         │
│ ├─ Aging: Bucket by days past due                        │
│ └─ Health score based on ratio & trends                  │
└──────────────────────────────────────────────────────────┘
```

---

## User Workflows

### Workflow 1: Monitor Branch Health

```
1. Open Branch Ops Dashboard
2. Review summary table
3. Identify branches with:
   - High open receivable
   - Low payment collection rate
   - Overdue status
4. Click branch for details
5. Review KPIs and outstanding amounts
6. Take action (follow-up, adjust terms, etc.)
```

### Workflow 2: Collections Follow-Up

```
1. Open Branch Ops Dashboard
2. Filter branches with OVERDUE status
3. Click branch for details
4. Go to Invoices tab
5. Identify overdue invoices
6. Note amount and days overdue
7. Send payment reminder
8. Track payment through Payments module
```

### Workflow 3: Outstanding Analysis

```
1. Open Branch Ops Dashboard
2. Click branch with high outstanding
3. Go to Ledger tab
4. Review payment history
5. Identify slowest-paying invoices
6. Export ledger to Excel
7. Analyze patterns and trends
8. Adjust credit terms or payment schedule
```

---

## Summary

Branch Operations is a **comprehensive financial dashboard** that gives a branch-centric view of order pipeline and receivables. It tracks **pending orders, outstanding amounts, payment collection rates, and financial health**. The combination of summary view, drill-down detail, orders, invoices, and ledger enables finance and sales teams to monitor cash flow, collect payments efficiently, and assess branch financial health.
