# Billing & Invoice - Complete Guide

## Overview

The Billing & Invoice module manages the **conversion of orders into customer invoices** and tracks **payment collection**. It handles invoice creation, GST taxation, payment recording, and allocation.

---

## Invoice Creation Flow

### Method 1: Create from Order

```
User navigates to: Bills > Create Invoice
    ↓
Selects: "From Order" mode
    ↓
Dropdown shows: List of non-invoiced orders
    ↓
Selects: Order from list
    ↓
Form auto-populates:
├─ Customer (branch from order)
├─ Items (from order with selling rates)
├─ Amount (selling total)
├─ Company (from order)
└─ Transaction date (from order)
    ↓
User can:
├─ Add/modify line items
├─ Toggle GST (optional)
└─ Review due date (auto-calculated)
    ↓
Clicks: "Create Invoice"
    ↓
POST /api/invoices
```

### Method 2: Create Manual Invoice

```
User navigates to: Bills > Create Invoice
    ↓
Selects: "Manual Entry" mode
    ↓
Form shows:
├─ Customer (required, searchable)
├─ Company (required, dropdown)
├─ Items section (add items manually)
│  ├─ Item code (required)
│  ├─ Item name (auto-populated)
│  ├─ Qty (required)
│  ├─ Rate (required)
│  └─ Amount (auto-calculated: qty × rate)
├─ Apply GST (optional toggle)
└─ Transaction date
    ↓
User:
├─ Selects customer
├─ Adds multiple items
├─ Toggles GST if needed
└─ Reviews total
    ↓
Clicks: "Create Invoice"
    ↓
POST /api/invoices
```

---

## Backend Invoice Processing

### InvoiceService.createInvoice()

```
1. VALIDATE INPUTS
   ├─ Verify customer exists
   ├─ Verify company exists
   ├─ Verify items are in catalog
   └─ Check for duplicate invoice (if from order)

2. RESOLVE DATES
   ├─ Invoice Date = today (or provided date)
   ├─ Get customer profile
   ├─ Look up customer.aas_credit_days
   ├─ Calculate: Due Date = Invoice Date + Credit Days
   └─ Store both dates

3. PREPARE LINE ITEMS
   ├─ For each item:
   │  ├─ Get item_code
   │  ├─ Get item_name
   │  ├─ Get quantity
   │  ├─ Get rate (selling price from order or manual entry)
   │  ├─ Calculate: amount = qty × rate
   │  └─ Check for GST
   │
   └─ Sum all amounts → Invoice Total

4. GST HANDLING (if enabled)
   ├─ Create Item Tax Template in ERPNext
   │  ├─ Template Name: Item Tax Template_{ITEMCODE}_{GSTRATE}
   │  ├─ For each item with GST:
   │  │  ├─ Get aas_gst_percent from item
   │  │  └─ Store in tax template
   │  │
   │  └─ Create GL Account for GST (if needed)
   │
   ├─ For each item:
   │  ├─ Lookup tax template
   │  ├─ Calculate: Tax = Amount × (GST% / 100)
   │  └─ Add to line item
   │
   ├─ Calculate total tax
   └─ Final Total = Line Items + Tax

5. CREATE IN ERPNEXT
   ├─ Prepare payload:
   │  ├─ doctype: "Sales Invoice"
   │  ├─ customer: branch ID
   │  ├─ company: company ID
   │  ├─ posting_date: invoice date
   │  ├─ due_date: calculated due date
   │  ├─ items: [line items]
   │  ├─ taxes: [tax entries if GST]
   │  └─ custom_fields: AAS fields
   │
   ├─ Call: erpNextClient.createResource("Sales Invoice", payload)
   └─ ERPNext returns: Invoice ID & details

6. LINK TO ORDER (if from order)
   ├─ Update Sales Order:
   │  ├─ aas_si_branch = invoice ID
   │  └─ aas_status = INVOICED
   │
   └─ Establish bidirectional link

7. RETURN INVOICE
   └─ Return invoice with ID, total, due date
```

### Invoice Data Structure

```
Sales Invoice (ERPNext):
├─ name (invoice ID, e.g., "SI-00001")
├─ posting_date (invoice date)
├─ due_date (calculated from credit days)
├─ customer (branch)
├─ company
├─ items: [
│   {
│     item_code: "VEN001_ELEC_854430",
│     item_name: "Controller Unit",
│     qty: 10,
│     rate: 107,         (selling price with margin)
│     amount: 1070,
│     aas_gst_percent: 18 (if applicable)
│   },
│   // ... more items
│ ]
├─ total_qty: (sum of quantities)
├─ net_total: (before tax)
├─ tax_total: (if GST applied)
├─ grand_total: (final invoice amount)
├─ outstanding_amount: (initially = grand_total)
└─ status: "Draft" or "Submitted"
```

---

## GST Handling

### GST Overview

```
GST = Goods and Services Tax (India)
├─ Applied per item or order-wide
├─ Standard rates: 5%, 12%, 18%, 28%
├─ Stored in item's aas_gst_percent field
├─ Calculated: Tax Amount = Line Amount × (GST% / 100)
└─ Added to invoice total
```

### Creating Item Tax Template

```
InvoiceService.ensureItemTaxTemplate():

1. CHECK IF TEMPLATE EXISTS
   └─ Look for "Item Tax Template_{ITEMCODE}_{GSTRATE}"

2. IF NOT FOUND, CREATE:
   ├─ Template Name: "Item Tax Template_{ITEMCODE}_{GSTRATE}"
   ├─ Item Code: item code
   ├─ Tax Type: "GST"
   ├─ Tax Rate: aas_gst_percent
   ├─ GL Account: "GST Receivable" (or configured account)
   └─ Save to ERPNext

3. LINK TO INVOICE
   ├─ For each invoice item:
   │  ├─ If aas_gst_percent > 0:
   │  │  ├─ Look up Item Tax Template
   │  │  └─ Apply to invoice item
   │  │
   │  └─ Else: No tax applied
```

### GST Calculation Example

```
Item: "Controller Unit"
├─ Rate: 107 (selling price)
├─ Qty: 10
├─ Amount: 1070
├─ GST: 18%
│
Calculation:
├─ GST Amount = 1070 × (18 / 100) = 192.6
├─ Final Amount = 1070 + 192.6 = 1262.6
└─ Invoice Total = Sum of all items including GST
```

---

## Due Date Calculation

### Credit Days Logic

```
Customer Profile:
├─ aas_credit_days: integer (e.g., 30, 45, 60)
└─ Specifies payment terms

Invoice Due Date Calculation:
├─ Invoice Date = posting_date (today or provided date)
├─ Credit Days = customer.aas_credit_days
├─ Due Date = Invoice Date + Credit Days
│
Example:
├─ Invoice Date: 2026-04-04
├─ Credit Days: 30
└─ Due Date: 2026-05-04 (30 days later)
```

### Different Credit Terms

```
Payment Terms by Credit Days:

Credit Days | Term Name      | Example Due Date
─────────────────────────────────────────────
0           | Cash/COD       | Same day
7           | Weekly         | 7 days
15          | Bi-weekly      | 15 days
30          | Monthly        | 30 days
45          | 1.5 months     | 45 days
60          | 2 months       | 60 days
90          | Quarterly      | 90 days
```

---

## Payment Recording

### Payment Entry Process

```
User navigates to: Bills > Record Payment
    ↓
Selects: Invoice from dropdown
    ↓
Form auto-populates:
├─ Invoice ID
├─ Customer (from invoice)
├─ Invoice Total (from invoice)
├─ Outstanding Amount (unpaid portion)
└─ Currency
    ↓
User enters:
├─ Payment Amount (can be full or partial)
├─ Payment Mode (cash, check, bank transfer, etc.)
├─ Payment Date
└─ Reference Number (check no, bank receipt, etc.)
    ↓
Clicks: "Record Payment"
    ↓
POST /api/payments
```

### Backend Payment Processing

```
PaymentService.recordPayment():

1. VALIDATE PAYMENT
   ├─ Verify invoice exists
   ├─ Verify payment amount > 0
   ├─ Verify payment ≤ (outstanding + tolerance)
   └─ Check for duplicate payment

2. CHECK FOR OVERPAYMENT
   ├─ Calculate: Surplus = Payment - Outstanding
   │
   ├─ If Surplus > 0:
   │  ├─ Show warning: "Overpayment by ₹X"
   │  ├─ Ask user to confirm
   │  └─ Store surplus as "unallocated amount"
   │
   └─ Else: Normal payment, proceed

3. ALLOCATE PAYMENT
   ├─ Amount to Allocate = min(Payment, Outstanding)
   │
   ├─ Update Invoice:
   │  ├─ amount_paid += allocated amount
   │  ├─ outstanding_amount -= allocated amount
   │  │
   │  └─ Status:
   │      ├─ If outstanding_amount = 0 → PAID ✓
   │      ├─ Else if outstanding_amount < original → PARTIAL 🟡
   │      └─ Else → PENDING ⏳
   │
   └─ If surplus > 0:
      └─ Store in "unallocated amount" account

4. CREATE PAYMENT ENTRY IN ERPNEXT
   ├─ Doctype: "Payment Entry"
   ├─ Payment Type: "Receive"
   ├─ Party Type: "Customer"
   ├─ Party: Branch/Customer
   ├─ Posting Date: payment date
   ├─ Amount: payment amount
   ├─ Mode of Payment: payment mode
   ├─ Reference Number: reference provided
   └─ Link to Invoice

5. UPDATE LEDGER
   ├─ Credit: Payment received
   ├─ Debit: Bank/Cash account
   ├─ Running balance updated
   └─ Ledger entry created

6. RETURN CONFIRMATION
   ├─ Payment ID
   ├─ Amount recorded
   ├─ Outstanding after payment
   └─ Receipt details
```

---

## Invoice Status & Outstanding

### Outstanding Amount Calculation

```
Outstanding = Invoice Total - Payments Received

Tracking:
├─ Initially: outstanding = invoice total
├─ After full payment: outstanding = 0
├─ After partial payment: outstanding = remaining amount
└─ Updated with each payment entry
```

### Invoice Status States

```
Status Determination:

If outstanding = 0:
  Status = PAID ✓
  └─ No further collection needed

Else if outstanding > 0 and today <= due_date:
  Status = PENDING ⏳
  └─ Payment expected by due date

Else if outstanding > 0 and today > due_date:
  Days Overdue = today - due_date
  Status = OVERDUE ⚠️
  └─ Payment is late, follow-up needed

If partial payment made (0 < outstanding < total):
  Status = PARTIAL 🟡
  └─ Some paid, remainder due
```

---

## Invoice PDF Download

### PDF Generation

```
User views: Invoice detail
    ↓
Clicks: "Download PDF"
    ↓
GET /api/invoices/{id}/pdf
    ↓
Backend: InvoiceService.downloadInvoicePdf()
  ├─ Load invoice from ERPNext
  ├─ Use company's print format
  │  └─ aas_sales_invoice_print_format (configurable)
  │
  ├─ Generate PDF with:
  │  ├─ Company header & logo
  │  ├─ Invoice details (ID, date, due date)
  │  ├─ Customer info (branch, address)
  │  ├─ Line items (description, qty, rate, amount)
  │  ├─ GST details (if applicable)
  │  ├─ Total amount
  │  └─ Payment terms
  │
  └─ Return: PDF file for download

User:
├─ Downloads PDF
├─ Can print for customer
└─ Can email to customer
```

---

## Invoice Deletion & Cascading

### Invoice Deletion

```
User deletes invoice (rare, careful operation)
    ↓
DELETE /api/invoices/{id}
    ↓
Backend: InvoiceService.deleteInvoice()
  ├─ Verify invoice exists
  ├─ Verify not already partially paid
  │  └─ If paid > 0: Ask for confirmation
  │
  ├─ Find related Payment Entries
  │  └─ All payments linked to this invoice
  │
  ├─ Cascade deletion:
  │  ├─ Delete Payment Entries (unallocate payments)
  │  ├─ Delete Invoice
  │  └─ Revert Order status to SELL_ORDER_CREATED
  │
  └─ Return: Deletion confirmation
```

**Warning:** Deletion is destructive and cascades. Use only for erroneous invoices.

---

## Data Flow Diagram

```
┌────────────────────────────────────────────────────────┐
│ INVOICE CREATION                                       │
├────────────────────────────────────────────────────────┤
│                                                        │
│ Method 1: From Order                                  │
│ ├─ User selects order                                 │
│ ├─ Auto-populate customer, items, rates               │
│ └─ Proceed to GST & create
│
│ Method 2: Manual Entry                                │
│ ├─ User selects customer                              │
│ ├─ User adds items manually                           │
│ └─ Proceed to GST & create
│                                                        │
│ Backend Processing:                                    │
│ ├─ Validate inputs                                    │
│ ├─ Calculate due date (invoice date + credit days)   │
│ ├─ Create Item Tax Templates (if GST)                │
│ ├─ Calculate taxes                                    │
│ └─ Create Sales Invoice in ERPNext                    │
└────────────────────────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────┐
│ INVOICE ISSUED                                         │
├────────────────────────────────────────────────────────┤
│                                                        │
│ Sales Invoice:                                         │
│ ├─ ID assigned (SI-0001, SI-0002, etc.)              │
│ ├─ Amount = line items + GST (if applicable)         │
│ ├─ Due Date calculated (invoice date + credit days)  │
│ ├─ Outstanding = Full amount (initially)              │
│ └─ Status = PENDING
│                                                        │
│ Related documents:                                     │
│ ├─ Linked to Order (if from order)                   │
│ └─ Ready for payment recording
└────────────────────────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────┐
│ PAYMENT RECORDING                                      │
├────────────────────────────────────────────────────────┤
│                                                        │
│ User records payment against invoice:                  │
│ ├─ Select invoice                                     │
│ ├─ Enter payment amount (full or partial)             │
│ ├─ Check for overpayment                              │
│ └─ POST /api/payments
│                                                        │
│ Backend Processing:                                    │
│ ├─ Validate amount                                    │
│ ├─ Allocate to invoice                                │
│ ├─ Update outstanding amount                          │
│ ├─ Determine invoice status (PAID/PARTIAL/PENDING)   │
│ ├─ Create Payment Entry in ERPNext                    │
│ └─ Update ledger
└────────────────────────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────┐
│ INVOICE SETTLED                                        │
├────────────────────────────────────────────────────────┤
│                                                        │
│ After full payment:                                    │
│ ├─ Outstanding = 0                                    │
│ ├─ Status = PAID ✓                                    │
│ ├─ Order marked complete (if linked)                  │
│ └─ Cash/Bank account updated
└────────────────────────────────────────────────────────┘
```

---

## Error Scenarios

| Scenario | Error | Resolution |
|----------|-------|-----------|
| Invalid customer | Customer Not Found | Select valid customer |
| Invalid item | Item Not Found | Item must be in catalog |
| Negative amount | Invalid Amount | Amount must be > 0 |
| Overpayment | Overpayment Detected | Confirm overpayment or adjust amount |
| Duplicate invoice | Already Invoiced | Each order can have only one invoice |
| No credit days set | Missing Credit Days | Configure credit days for customer |

---

## Summary

The Billing & Invoice module **converts orders into customer invoices** and tracks **payment collection**. It supports two creation modes (from order or manual), handles GST taxation with Item Tax Templates, auto-calculates due dates based on customer credit terms, and provides comprehensive payment tracking with outstanding amount monitoring. The module is central to converting procurement into revenue and collecting customer payments.
