# Vendor Operations - Complete Guide

## Overview

Vendor Operations provides a **vendor-centric view** of all procurement activities. It tracks vendor performance, pending orders, financial settlement, and operational exceptions.

---

## Vendor Operations Dashboard

### Summary View (All Vendors)

```
User navigates to: Operations > Vendor Ops
    ↓
GET /api/vendor-ops/summary
    ↓
Backend: VendorOpsService.getSummary()
  ├─ Fetch all active vendors
  ├─ For each vendor, calculate:
  │  ├─ Pending order count (status != INVOICED)
  │  ├─ Awaiting PDF count (VENDOR_ASSIGNED, not yet VENDOR_PDF_RECEIVED)
  │  ├─ Awaiting Bill count (VENDOR_PDF_RECEIVED, not yet VENDOR_BILL_CAPTURED)
  │  ├─ Pending amount (sum of uninvoiced order totals)
  │  ├─ Settlement status (SETTLED/OPEN/OVERDUE)
  │  └─ Last activity date
  │
  └─ Return: VendorSummaryRow[] (one row per vendor)
    ↓
Display table:
├─ Vendor Name
├─ Pending Orders (count)
├─ Awaiting PDF (count)
├─ Awaiting Bill (count)
├─ Pending Amount (₹)
├─ Settlement Status (badge)
└─ Last Activity (timestamp)
```

### Summary Data Structure

```
VendorSummaryRow {
  vendorId: string;
  vendorName: string;
  pendingOrders: number;           // Count of non-invoiced orders
  awaitingPdfCount: number;        // Assigned but no PDF
  awaitingBillCount: number;       // PDF uploaded but not captured
  pendingAmount: number;           // Sum of uninvoiced totals
  settlementStatus: 'SETTLED' | 'OPEN' | 'OVERDUE';
  lastActivityDate: Date;
  totalOrdersLifetime: number;     // All orders ever
  totalAmountLifetime: number;     // Total procurement
}
```

---

## Per-Vendor Drill-Down

### Vendor Detail View

```
User clicks on vendor name in summary table
    ↓
GET /api/vendor-ops/{vendorId}
    ↓
Backend: VendorOpsService.getVendorDetail()
  ├─ Load vendor profile
  ├─ Load all orders (in all statuses)
  ├─ Calculate KPIs:
  │  ├─ Total orders
  │  ├─ Pending orders
  │  ├─ Invoiced orders
  │  ├─ Total amount spent
  │  ├─ Pending amount
  │  ├─ Average order value
  │  └─ Payment settlement rate
  │
  ├─ Load vendor ledger
  │  ├─ Double-entry format
  │  ├─ Debit: invoices issued to vendor
  │  ├─ Credit: payments made to vendor
  │  └─ Running balance
  │
  └─ Return: VendorDetailView
    ↓
Display:
├─ Vendor Profile:
│  ├─ Name
│  ├─ Contact info
│  ├─ Category assigned
│  └─ Invoice template status
│
├─ KPI Cards:
│  ├─ Total Pending Orders
│  ├─ Awaiting PDF
│  ├─ Awaiting Bill
│  ├─ Pending Amount
│  ├─ Payment Settlement Rate (%)
│  └─ Average Order Value
│
└─ Tabs:
   ├─ Orders Tab
   ├─ Ledger Tab
   └─ Exceptions Tab
```

### Vendor Detail Data

```
VendorDetailView {
  vendorId: string;
  vendorName: string;
  vendorProfile: {
    contactPerson: string;
    email: string;
    phone: string;
    address: string;
    category: string;
  };
  
  kpis: {
    totalOrders: number;
    pendingOrders: number;
    invoicedOrders: number;
    totalSpent: number;
    pendingAmount: number;
    averageOrderValue: number;
    paymentSettlementRate: number; // % of invoiced vs pending
  };
  
  templateStatus: {
    configured: boolean;
    templateKey: string;
    lastUpdated: Date;
  };
}
```

---

## Vendor Orders View

### Order Listing for Vendor

```
In vendor detail, user clicks: "Orders" tab
    ↓
GET /api/vendor-ops/{vendorId}/orders
    ↓
Backend: VendorOpsService.getVendorOrders()
  ├─ Fetch all orders for vendor
  ├─ For each order, include:
  │  ├─ Order ID & date
  │  ├─ Current status
  │  ├─ PDF status (received/pending)
  │  ├─ Bill status (captured/pending)
  │  ├─ Parsed items count
  │  ├─ Bill total (vendor cost)
  │  ├─ Sell total (with margin)
  │  ├─ Margin %
  │  └─ Invoice link (if invoiced)
  │
  └─ Return: VendorOrderRow[]
    ↓
Display table:
├─ Order ID (clickable)
├─ Date
├─ Status (color-coded badge)
├─ PDF Status (icon)
├─ Bill Status (icon)
├─ Items Count
├─ Cost Total (₹)
├─ Sell Total (₹)
├─ Margin %
└─ Actions (drill-down)
```

### Order Status Indicators

```
Status Badge Color Codes:
├─ DRAFT → Gray (not started)
├─ VENDOR_ASSIGNED → Blue (vendor picked)
├─ VENDOR_PDF_RECEIVED → Yellow (PDF uploaded)
├─ VENDOR_BILL_CAPTURED → Orange (awaiting sell order)
├─ SELL_ORDER_CREATED → Cyan (ready for invoice)
└─ INVOICED → Green (complete)

PDF Status Icons:
├─ ✓ Received (green)
└─ ⏳ Pending (gray)

Bill Status Icons:
├─ ✓ Captured (green)
└─ ⏳ Pending (gray)
```

---

## Vendor Ledger

### Double-Entry Ledger Format

```
In vendor detail, user clicks: "Ledger" tab
    ↓
GET /api/vendor-ops/{vendorId}/ledger
    ↓
Backend: VendorOpsService.getVendorLedger()
  ├─ Fetch all transactions for vendor:
  │  ├─ Invoices created (debit = money owed to vendor)
  │  └─ Payments made (credit = money paid to vendor)
  │
  ├─ Sort by date
  ├─ Calculate running balance
  │  └─ Balance = Previous Balance + Debit - Credit
  │
  └─ Return: LedgerEntry[]
    ↓
Display ledger table:
├─ Date
├─ Voucher Type (PO/Invoice/Payment)
├─ Voucher Number
├─ Description
├─ Debit (Invoices - money owed) (₹)
├─ Credit (Payments - money paid) (₹)
└─ Balance (running total) (₹)

Example:
  Date       | Type      | Voucher   | Description      | Debit  | Credit | Balance
  2026-01-15 | PO        | PO-0001   | Order from Jan   | 5000   |        | 5000
  2026-01-20 | Payment   | P-0001    | Partial payment  |        | 2000   | 3000
  2026-02-10 | Invoice   | INV-0002  | February order   | 6000   |        | 9000
  2026-02-15 | Payment   | P-0002    | Full settlement  |        | 9000   | 0
```

### Ledger Data Structure

```
LedgerEntry {
  date: Date;
  voucherType: 'PO' | 'Invoice' | 'Payment';
  voucherNumber: string;
  description: string;
  debit: number;          // Money owed to vendor
  credit: number;         // Money paid to vendor
  balance: number;        // Running balance
  referenceId: string;    // Order/invoice/payment ID
}
```

### Balance Interpretation

```
Balance = 0:    SETTLED
  └─ Vendor has been fully paid
  
Balance > 0:    OPEN (Outstanding)
  └─ Money still owed to vendor
  
Balance > 0 and
Oldest entry > 30 days:  OVERDUE
  └─ Payment is overdue, follow-up needed
```

---

## Ledger Export

### CSV Export

```
In vendor detail, user clicks: "Export Ledger"
    ↓
GET /api/vendor-ops/{vendorId}/ledger/export
    ↓
Backend: VendorOpsService.exportLedger()
  ├─ Generate CSV with all ledger entries
  ├─ Include summary footer:
  │  ├─ Total Debits
  │  ├─ Total Credits
  │  └─ Final Balance
  │
  └─ Download as: VENDOR_LEDGER_{VENDORID}_{DATE}.csv
    ↓
User opens in Excel/Sheets
├─ Can analyze transaction patterns
├─ Can filter by date range
├─ Can calculate payment delays
└─ Can review settlement history
```

---

## Settlement States & Analysis

### Settlement Status Calculation

```
VendorOpsService.determineSettlementStatus():

1. GET FINAL BALANCE
   └─ Last entry in ledger

2. CHECK BALANCE
   ├─ If Balance = 0:
   │  └─ Status = SETTLED ✓
   │
   └─ If Balance > 0:
      ├─ Get oldest unpaid debit entry date
      ├─ Calculate days overdue = Today - Old Entry Date
      │
      ├─ If days overdue > 30 days:
      │  └─ Status = OVERDUE ⚠️
      │
      └─ Else:
         └─ Status = OPEN ℹ️
```

### Status Meanings

| Status | Meaning | Action |
|--------|---------|--------|
| **SETTLED** | All bills paid, balance = 0 | No action needed |
| **OPEN** | Money owed but within terms | Monitor for payment |
| **OVERDUE** | Money owed > 30 days | Follow up on payment |

---

## Exceptions & Alerts

### Exception Types

```
VendorOpsService.getExceptions():

1. ORDERS WAITING TOO LONG
   ├─ Condition: Order in VENDOR_ASSIGNED for > 7 days
   ├─ Issue: No PDF uploaded
   └─ Alert: "Follow up on PDF upload"

2. PDF NOT CAPTURED
   ├─ Condition: Order in VENDOR_PDF_RECEIVED for > 3 days
   ├─ Issue: Bill not confirmed
   └─ Alert: "Confirm bill details"

3. PENDING PAYMENTS
   ├─ Condition: Balance > 0 and > 30 days old
   ├─ Issue: Payment overdue
   └─ Alert: "Process payment immediately"

4. TEMPLATE ISSUES
   ├─ Condition: Vendor has no invoice template
   ├─ Issue: Cannot process PDFs automatically
   └─ Alert: "Configure invoice template"

5. MISMATCH DETECTED
   ├─ Condition: Bill total != items total
   ├─ Issue: Possible invoice error
   └─ Alert: "Verify invoice total"
```

---

## Performance Metrics

### Key Metrics Tracked

```
VendorOpsService.getMetrics():

1. ORDER TURNAROUND TIME
   └─ Avg days from DRAFT to INVOICED

2. PDF PROCESSING TIME
   └─ Avg days from VENDOR_ASSIGNED to VENDOR_PDF_RECEIVED

3. BILL CAPTURE TIME
   └─ Avg days from VENDOR_PDF_RECEIVED to VENDOR_BILL_CAPTURED

4. PAYMENT SETTLEMENT RATE
   └─ % of invoices paid vs total issued

5. PDF PARSE SUCCESS RATE
   └─ % of PDFs parsed with < 10% items missing

6. MISMATCH FREQUENCY
   └─ % of orders requiring "Allow Mismatch"
```

---

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│ VENDOR OPS SUMMARY VIEW                                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│ GET /api/vendor-ops/summary                                  │
│ ├─ Fetch all vendors                                         │
│ ├─ For each vendor:                                          │
│ │  ├─ Count pending orders (not INVOICED)                   │
│ │  ├─ Count awaiting PDF                                    │
│ │  ├─ Count awaiting bill                                   │
│ │  ├─ Sum pending amount                                    │
│ │  └─ Determine settlement status                           │
│ │                                                            │
│ └─ Return: Summary table with KPIs                          │
│                                                               │
│ Display:                                                      │
│ ├─ All vendors in list                                       │
│ ├─ Status indicators (colors)                                │
│ ├─ Quick metrics (counts, amounts)                           │
│ └─ Action buttons (drill-down)                               │
└──────────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────────┐
│ PER-VENDOR DRILL-DOWN                                        │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│ GET /api/vendor-ops/{vendorId}                               │
│ ├─ Load vendor profile                                       │
│ ├─ Calculate all KPIs                                        │
│ ├─ Load orders list                                          │
│ ├─ Load ledger (all transactions)                            │
│ └─ Detect exceptions                                         │
│                                                               │
│ Display tabs:                                                 │
│ ├─ Profile & KPIs                                            │
│ ├─ Orders (status, dates, amounts)                           │
│ ├─ Ledger (double-entry, running balance)                    │
│ └─ Exceptions (alerts, action items)                         │
└──────────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────────┐
│ VENDOR ORDERS VIEW                                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│ GET /api/vendor-ops/{vendorId}/orders                        │
│ ├─ Fetch all orders for vendor                              │
│ ├─ Include status, dates, amounts, items count              │
│ └─ Link to order detail if clicked                          │
└──────────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────────┐
│ VENDOR LEDGER VIEW                                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│ GET /api/vendor-ops/{vendorId}/ledger                        │
│ ├─ Fetch all transactions (invoices, payments)              │
│ ├─ Sort by date                                              │
│ ├─ Calculate running balance                                 │
│ ├─ Display in double-entry format                            │
│ └─ Allow CSV export                                          │
│                                                               │
│ GET /api/vendor-ops/{vendorId}/ledger/export                 │
│ └─ Download CSV file                                         │
└──────────────────────────────────────────────────────────────┘
```

---

## User Workflows

### Workflow 1: Monitor Vendor Performance

```
1. Open Vendor Ops Dashboard
2. Review summary table
3. Identify vendors with:
   - High pending orders
   - Awaiting PDF count
   - Overdue status
4. Click vendor for details
5. Review KPIs and exceptions
6. Take action (follow-up, payment, etc.)
```

### Workflow 2: Process Pending Bills

```
1. Open Vendor Ops Dashboard
2. Filter vendors with "Awaiting Bill" count > 0
3. Click vendor
4. Go to Orders tab
5. Identify orders in VENDOR_PDF_RECEIVED status
6. Click order to capture bill
7. Mark bill as captured
```

### Workflow 3: Settlement & Payment

```
1. Open Vendor Ops Dashboard
2. Identify vendors with OVERDUE status
3. Click vendor for details
4. Review Ledger tab
5. Analyze payment history
6. Process payment via Payments module
7. Verify balance updates to SETTLED
```

---

## Summary

Vendor Operations is a **comprehensive operational dashboard** that gives a vendor-centric view of procurement activities. It tracks **pending orders, PDF status, bill capture, ledger balance, and settlement state**. The combination of summary view, drill-down detail, orders, and ledger enables operations teams to manage vendor relationships, track payment obligations, and resolve exceptions efficiently.
