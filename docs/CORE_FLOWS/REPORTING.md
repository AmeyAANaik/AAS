# Reports & Analytics - Complete Guide

## Overview

The Reports module provides **data aggregation, analysis, and export capabilities** across procurement, inventory, and financial operations. It enables stakeholders to generate insights on vendor performance, branch receivables, and operational metrics with month-scoped filtering and CSV export options.

---

## Reports Dashboard

### Access Reports

```
User navigates to: Operations > Reports
    ↓
Dashboard displays: List of available reports
├─ Vendor Reports (4 types)
├─ Branch Reports (4 types)
└─ Month/Date filters
    ↓
User selects report type and optional filters
    ↓
GET /api/reports/{reportType}
    ↓
Backend: ReportService.generate{ReportType}()
  ├─ Fetch raw transaction data
  ├─ Aggregate by grouping key (vendor/customer)
  ├─ Calculate metrics
  ├─ Filter by month (optional)
  └─ Return structured data
    ↓
Display tabular report with:
├─ Column headers matching data structure
├─ Rows grouped by entity (vendor/branch)
├─ Summary rows (totals)
└─ Export button for CSV
```

---

## Report Types

### 1. Vendor Orders Report

```
Endpoint: GET /api/reports/vendor-orders?vendor={vendorId}&month={monthLabel}

Purpose: Show all orders placed with a specific vendor (or all vendors)

Data Returned:
├─ Vendor Name
├─ Order ID
├─ Order Date
├─ Order Status (DRAFT, VENDOR_ASSIGNED, etc.)
├─ Items Count
├─ Bill Total (Vendor Cost)
├─ Sell Total (with margin)
├─ Margin %
├─ Invoice ID (if invoiced)
├─ Branch (customer/shop)
└─ Last Updated

Grouping: By vendor (one section per vendor)

Use Cases:
├─ See all orders from a specific vendor
├─ Track order pipeline (how many at each stage)
├─ Analyze order value trends
└─ Verify vendor invoice matching

Export: CSV with all rows + summary footer
```

### 2. Vendor Billing Report

```
Endpoint: GET /api/reports/vendor-billing?vendor={vendorId}&month={monthLabel}

Purpose: Show billing (invoices) summary by vendor

Data Returned:
├─ Vendor Name
├─ Total Orders Billed
├─ Total Invoice Amount (all invoices for vendor)
├─ Total Paid (sum of payments received)
├─ Outstanding Amount (unpaid)
├─ Payment Rate (% paid vs invoiced)
├─ Days Average to Pay
└─ Last Payment Date

Grouping: One row per vendor, optionally filtered by month

Use Cases:
├─ Monitor vendor payment collection
├─ Identify vendors with slow payment rates
├─ Analyze outstanding amounts by vendor
└─ Plan cash flow based on payment patterns

Export: CSV with vendor totals + grand summary
```

### 3. Vendor Payments Report

```
Endpoint: GET /api/reports/vendor-payments?vendor={vendorId}&month={monthLabel}

Purpose: Show payment transaction history with vendors

Data Returned:
├─ Vendor Name
├─ Payment Date
├─ Payment Amount
├─ Payment Mode (Cash, Check, Bank Transfer, etc.)
├─ Reference Number (Check No, Transaction ID, etc.)
├─ Invoice Reference (which invoice this paid)
├─ Status (Full/Partial)
└─ Running Balance (after payment)

Grouping: By vendor, ordered by date

Use Cases:
├─ Audit payment records
├─ Track payment modes used
├─ Verify payment allocation to invoices
├─ Analyze payment frequency patterns
└─ Support reconciliation with bank statements

Export: CSV with all payments + summary
```

### 4. Vendor Payment Aging Report

```
Endpoint: GET /api/reports/vendor-payments?vendor={vendorId}&month={monthLabel}

Purpose: Analyze payment aging (how long vendor waits for payment)

Data Returned:
├─ Vendor Name
├─ Invoice Date
├─ Payment Due Date
├─ Payment Date
├─ Days to Payment (Due - Payment)
├─ Invoice Amount
├─ Payment Status (On-time, Late, Overdue)
└─ Aging Bucket (0-7 days, 8-30 days, >30 days)

Grouping: By aging bucket, then vendor

Use Cases:
├─ Identify payment delays
├─ Calculate average payment cycles
├─ Monitor compliance with payment terms
└─ Negotiate better terms based on data

Export: CSV with aging analysis
```

### 5. Branch/Shop Billing Report

```
Endpoint: GET /api/reports/shop-billing?customer={customerId}&month={monthLabel}

Purpose: Show billing (invoices) summary by branch/customer

Data Returned:
├─ Branch/Shop Name
├─ Total Orders Invoiced
├─ Total Invoice Amount (all invoices to branch)
├─ Total Paid (sum of payments received from branch)
├─ Outstanding Amount (receivable)
├─ Payment Collection Rate (% paid vs invoiced)
├─ Days Average to Collect
├─ Last Payment Date

Grouping: One row per branch, optionally filtered by month

Use Cases:
├─ Monitor receivables by branch
├─ Identify slow-paying branches
├─ Forecast cash inflow
├─ Assess branch financial health
└─ Plan collection follow-ups

Export: CSV with branch totals + summary
```

### 6. Branch/Shop Payments Report

```
Endpoint: GET /api/reports/shop-payments?customer={customerId}&month={monthLabel}

Purpose: Show payment transaction history from branches

Data Returned:
├─ Branch/Shop Name
├─ Payment Date
├─ Payment Amount
├─ Payment Mode
├─ Reference Number
├─ Invoice Reference (which invoice this paid)
├─ Status (Full/Partial)
└─ Running Balance (after payment)

Grouping: By branch, ordered by date

Use Cases:
├─ Audit customer payment records
├─ Track payment behavior by branch
├─ Verify payment allocation
├─ Support reconciliation
└─ Analyze payment delays

Export: CSV with all payments + summary
```

### 7. Branch/Shop Category Report

```
Endpoint: GET /api/reports/shop-category?customer={customerId}&month={monthLabel}

Purpose: Show inventory/sales breakdown by category for a branch

Data Returned:
├─ Branch/Shop Name
├─ Category Name
├─ Items Sold (count)
├─ Quantity Sold (total units)
├─ Total Amount (revenue)
├─ Total Cost (vendor cost)
├─ Margin Amount (Sold - Cost)
├─ Margin % (Average margin for category)

Grouping: By branch, then category

Use Cases:
├─ Analyze category performance by branch
├─ Identify top-performing categories
├─ Monitor category margins
├─ Forecast demand by category
├─ Plan inventory allocation

Export: CSV with category breakdown + margins
```

### 8. Branch/Shop Order Pipeline Report

```
Endpoint: GET /api/reports/shop-orders?customer={customerId}&month={monthLabel}

Purpose: Show all orders going to a specific branch

Data Returned:
├─ Branch/Shop Name
├─ Order ID
├─ Order Date
├─ Vendor Name
├─ Order Status
├─ Items Count
├─ Bill Total
├─ Sell Total (with margin)
├─ Margin %
├─ Invoice Date (if invoiced)
└─ Invoice Amount

Grouping: By branch, optionally by status

Use Cases:
├─ See order pipeline for a branch
├─ Track pending orders
├─ Verify invoicing status
└─ Analyze ordering patterns

Export: CSV with all orders
```

---

## Report Features

### Month Filtering

```
All reports support optional month scoping:

GET /api/reports/{reportType}?month={monthLabel}

Month Format:
├─ "2026-04" (April 2026)
├─ "2026-01" (January 2026)
└─ Empty = All time (no filter)

Backend Logic:
├─ Parse month to get start and end dates
├─ Filter transactions within that month
├─ If no month provided, aggregate all time
└─ Return filtered results

Use Case:
├─ Analyze trends month-by-month
├─ Close and reconcile monthly accounts
├─ Compare performance across months
└─ Budget forecasting
```

### CSV Export

```
GET /api/reports/{reportType}/export?month={monthLabel}

Process:
├─ Execute same report query
├─ Convert result set to CSV format:
│  ├─ Header row (column names)
│  ├─ Data rows (one per record)
│  ├─ Summary footer (totals)
│  └─ Optional: metadata (generated date, filters used)
│
├─ Set response headers:
│  ├─ Content-Type: text/csv
│  ├─ Content-Disposition: attachment; filename="..."
│  └─ Force browser download
│
└─ Return CSV as downloadable file

File Naming:
├─ vendor-orders.csv
├─ vendor-billing.csv
├─ shop-billing.csv
├─ shop-payments.csv
└─ shop-category.csv

Excel Compatibility:
├─ Can open directly in Excel
├─ Supports formulas for further analysis
├─ Preserves numeric formatting for calculations
└─ UTF-8 encoding for special characters
```

---

## Data Flow Diagram

```
┌────────────────────────────────────────────────────────────┐
│ REPORT SELECTION                                           │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ User selects report type from Reports menu                │
│ ├─ Vendor Orders / Vendor Billing / Vendor Payments       │
│ ├─ Branch Billing / Branch Payments / Branch Category     │
│ └─ Optional: Month filter, Entity filter (vendor/branch)  │
│                                                            │
│ Selects: "View Report" or "Export to CSV"                │
└────────────────────────────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────────┐
│ REPORT GENERATION                                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ GET /api/reports/{reportType}                             │
│ ├─ Fetch transactions from backend                         │
│ ├─ Filter by month (if provided)                          │
│ ├─ Group by entity (vendor/branch/category)               │
│ ├─ Calculate aggregates (totals, percentages, averages)   │
│ └─ Return structured result set                           │
│                                                            │
│ Backend: ReportService.{reportType}()                     │
│ ├─ Query Orders/Invoices/Payments tables                  │
│ ├─ Join with Vendor/Branch/Category masters               │
│ ├─ Apply filtering logic                                  │
│ └─ Calculate KPIs (rates, aging, balances)                │
└────────────────────────────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────────┐
│ DISPLAY OR EXPORT                                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ If "View Report":                                          │
│ ├─ Render table with report data                          │
│ ├─ Column headers matching data structure                 │
│ ├─ Sortable/filterable columns (optional)                 │
│ ├─ Summary footer with totals                             │
│ └─ Export button available                                │
│                                                            │
│ If "Export to CSV":                                        │
│ ├─ GET /api/reports/{reportType}/export                   │
│ ├─ Generate CSV from same data                            │
│ ├─ Add headers and footers                                │
│ └─ Download as attachment                                 │
└────────────────────────────────────────────────────────────┘
```

---

## Report Usage Workflows

### Workflow 1: Monitor Monthly Vendor Performance

```
1. Open Reports menu
2. Select "Vendor Billing Report"
3. Set month filter (e.g., "2026-04")
4. Click "View Report"
5. Review:
   - Total invoiced per vendor
   - Payment collection rate
   - Outstanding amounts
6. Identify slow-payers (rate < 80%)
7. Optionally export to Excel for further analysis
8. Plan vendor follow-ups
```

### Workflow 2: Analyze Branch Receivables

```
1. Open Reports menu
2. Select "Branch/Shop Billing Report"
3. Optional: Filter by month
4. View report showing all branches with:
   - Total invoiced
   - Total paid
   - Outstanding receivable
   - Collection rate
5. Sort by "Outstanding" (highest first)
6. Export to CSV
7. Use Excel to further analyze aging or payment patterns
8. Plan collections strategy
```

### Workflow 3: Category Performance Analysis

```
1. Open Reports menu
2. Select "Branch Category Report"
3. Optional: Filter by branch and month
4. View breakdown:
   - Items sold per category
   - Revenue and cost per category
   - Margin % by category
5. Export to CSV
6. Analyze in Excel:
   - Which categories are most profitable
   - Which branches favor which categories
   - Trends over multiple months
7. Inform purchasing and pricing decisions
```

### Workflow 4: Payment Reconciliation

```
1. Open Reports menu
2. Select "Vendor Payments Report" (or "Branch Payments Report")
3. Filter by month
4. View all payments:
   - Date and amount
   - Mode and reference
   - Associated invoice
5. Export to CSV
6. Reconcile with:
   - Bank statements
   - Vendor statements
   - Payment ledgers
7. Verify accuracy and identify discrepancies
```

---

## Report KPIs & Calculations

### Payment Collection Rate

```
Formula:
Collection Rate = (Total Paid / Total Invoiced) × 100

Example:
├─ Total Invoiced: ₹10,000
├─ Total Paid: ₹8,000
└─ Collection Rate: 80%

Interpretation:
├─ > 90%: Excellent
├─ 70-90%: Good
├─ 50-70%: Monitor
└─ < 50%: Action needed
```

### Days Average to Payment

```
Formula:
Avg Days = Sum of (Payment Date - Invoice Date) / Count of Payments

Example:
├─ Invoice 1: 10 days to payment
├─ Invoice 2: 20 days to payment
├─ Invoice 3: 15 days to payment
└─ Average: 15 days

Interpretation:
├─ < 7 days: Prompt payment
├─ 7-30 days: Normal terms
├─ 30-60 days: Extended terms
└─ > 60 days: Slow payment pattern
```

### Margin Analysis

```
For Category Reports:

Margin % = ((Sell Total - Cost Total) / Sell Total) × 100

Example:
├─ Category: Electronics
├─ Sell Total: ₹10,000
├─ Cost Total: ₹9,300
└─ Margin %: 7%

Used to:
├─ Monitor profitability by category
├─ Identify high-margin products
├─ Ensure margins are applied correctly
└─ Adjust pricing if margins are too low
```

---

## Error Scenarios

| Scenario | Error | Resolution |
|----------|-------|-----------|
| Invalid month format | Invalid Month Format | Use YYYY-MM format (e.g., 2026-04) |
| Vendor/customer not found | Entity Not Found | Select valid vendor or branch ID |
| No data for period | No Records | Period may have no transactions; adjust dates |
| Export fails | CSV Generation Error | Check available disk space, retry |
| Timeout on large report | Request Timeout | Filter by month to reduce dataset |

---

## Summary

The Reports module provides **comprehensive analytics and export capabilities** for vendors, branches, and operational metrics. It enables stakeholders to monitor payment health, analyze category performance, reconcile transactions, and make data-driven business decisions. The month-based filtering and CSV export functionality support both operational monitoring and downstream analysis in business intelligence tools.

