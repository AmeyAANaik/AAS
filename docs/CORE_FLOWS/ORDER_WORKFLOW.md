# Order Workflow - Complete Guide

## Overview

The Order workflow is the **core business process** in AAS. It manages the complete lifecycle from order creation through vendor assignment, bill capture, to final invoicing.

---

## Order Status Pipeline

```
DRAFT
  ↓ (Assign Vendor)
VENDOR_ASSIGNED
  ↓ (Upload Vendor PDF)
VENDOR_PDF_RECEIVED
  ↓ (Capture Bill)
VENDOR_BILL_CAPTURED
  ↓ (Create Sell Order)
SELL_ORDER_CREATED
  ↓ (Create Invoice)
INVOICED
```

---

## Phase 1: Order Creation

### Two Creation Modes

#### **Mode 1: Image-Based Order (Branch Image Upload)**

```
User navigates to: Orders > Create Order
    ↓
Selects: "Image Mode"
    ↓
Uploads: Branch order sheet photos/images
    ↓
Backend: VendorPdfService
  ├─ Converts image to PDF (if needed)
  ├─ Extracts text using OCR (PDFBox)
  ├─ AI analyzes text structure
  ├─ Generates field mapping dynamically
  └─ Extracts items, quantities, rates
    ↓
Order created in DRAFT status
├─ Items populated from image
├─ Quantities auto-extracted
└─ Rates detected from invoice
    ↓
User can:
├─ Review extracted data
├─ Correct any OCR errors
└─ Proceed to vendor assignment
```

**Files Involved:**
- Frontend: `order-page.component.ts` (upload handler)
- Backend: `VendorPdfService.java` (image processing)
- Backend: `OcrService.java` (OCR operations)

#### **Mode 2: Item Catalog Selection**

```
User navigates to: Orders > Create Order
    ↓
Selects: "Item Mode"
    ↓
Chooses:
├─ Category (required)
├─ Branch (required)
├─ Items from catalog (required)
├─ Company (required)
├─ Transaction Date
└─ Delivery Date
    ↓
For each item:
├─ Item code looked up
├─ Item margin resolved (item's margin or 7% default)
├─ UOM defaulted
└─ Line added to order
    ↓
POST /api/orders
    │
    └─ Backend: OrderService.createOrder()
        ├─ Validate inputs
        ├─ Create Sales Order in ERPNext
        ├─ Create order lines
        └─ Return created order
    ↓
Order created in DRAFT status
├─ All fields manually specified
├─ Items from catalog
└─ Ready for vendor assignment
```

**Files Involved:**
- Frontend: `order-create.component.ts`
- Backend: `OrderService.createOrder()`
- Backend: `MasterDataService` (item lookups)

### Order Data Structure

```
Sales Order (ERPNext):
├─ customer (branch)
├─ company
├─ transaction_date
├─ delivery_date
├─ aas_category (category)
├─ aas_status (DRAFT)
├─ items (order lines)
│  ├─ item_code
│  ├─ item_name
│  ├─ qty
│  ├─ rate (vendor cost per unit)
│  ├─ amount (qty × rate)
│  └─ aas_margin_percent (7% or item's margin)
└─ grand_total (sum of amounts)
```

---

## Phase 2: Vendor Assignment

### User Action

```
User navigates to: Orders > [Select Order]
    ↓
Clicks: "Assign Vendor"
    ↓
Dropdown shows: List of active vendors
    ↓
Selects: Vendor from list
    ↓
POST /api/orders/{id}/assign-vendor
```

### Backend Processing

```
VendorAssignmentService.assignVendor():
├─ Verify order exists
├─ Verify vendor exists and active
├─ Validate vendor belongs to order category
├─ Check vendor has invoice template configured
├─ Update Sales Order:
│  ├─ aas_vendor = selected vendor
│  └─ aas_status = VENDOR_ASSIGNED
└─ Return updated order
```

### Order State After Assignment

```
Sales Order:
├─ aas_vendor = "VEN001" (Vendor A)
├─ aas_status = VENDOR_ASSIGNED
└─ Ready for vendor PDF upload
```

---

## Phase 3: Vendor PDF Upload & Parsing

### User Action

```
User navigates to: Orders > [Select Order]
    ↓
Clicks: "Upload Vendor PDF"
    ↓
Selects: Vendor invoice PDF file
    ↓
POST /api/orders/{id}/vendor-pdf
```

### Backend PDF Processing

```
VendorPdfService.processVendorPdf():

1. LOAD VENDOR TEMPLATE
   ├─ Get vendor's configured invoice template
   ├─ Load field mappings (item, rate, qty, etc.)
   └─ Validate template exists

2. EXTRACT FROM PDF
   ├─ Load PDF bytes
   ├─ Use OCR to extract text
   ├─ Apply vendor's field mappings
   ├─ Extract:
   │  ├─ Item names/codes
   │  ├─ Quantities
   │  ├─ Rates (vendor cost)
   │  ├─ HSN codes
   │  ├─ GST percentages
   │  ├─ MRP (if available)
   │  └─ Total bill amount
   └─ Return ParsedItem[] (list of items)

3. RESOLVE ITEMS
   ├─ For each parsed item:
   │  ├─ Build item code: VENDOR_CATEGORY_HSN[_NAME]
   │  │
   │  ├─ Check: Does item exist in catalog?
   │  │  ├─ YES → Use existing item
   │  │  └─ NO → Create new item auto
   │  │
   │  ├─ Get item's margin percent
   │  │  ├─ If item has aas_margin_percent > 0 → Use it
   │  │  └─ Else → Use default 7%
   │  │
   │  └─ Apply pricing logic:
   │      ├─ Calculate: Selling Rate = Vendor Rate × (1 + Margin%)
   │      ├─ Check: Does item have MRP?
   │      │  ├─ If Selling Rate > MRP → Cap at MRP, recalc margin
   │      │  └─ Else → Use calculated rate
   │      └─ Return resolved item with margin
   │
   └─ Return resolved items with margins

4. CREATE PURCHASE ORDER
   ├─ Create Purchase Order in ERPNext
   ├─ Copy vendor items to PO
   ├─ Set vendor cost rates
   └─ Store PO reference (aas_po)

5. CALCULATE OVERALL MARGIN
   ├─ Sum vendor costs: Σ(rate × qty)
   ├─ Sum selling prices: Σ((rate × (1 + margin%)) × qty)
   ├─ Calculate: Overall Margin% = ((Sell Total - Cost Total) / Cost Total) × 100
   └─ Store as aas_margin_percent

6. UPDATE SALES ORDER
   ├─ aas_status = VENDOR_PDF_RECEIVED
   ├─ items = resolved items with margins
   ├─ aas_vendor_pdf = file URL
   ├─ aas_vendor_bill_total = parsed total
   ├─ aas_vendor_bill_ref = PO reference
   ├─ aas_vendor_bill_date = parsed date
   ├─ aas_transport_charge = parsed charge
   ├─ aas_po = purchase order ID
   └─ aas_margin_percent = overall margin%

7. RETURN PREVIEW
   ├─ Sell Preview:
   │  ├─ vendorTotal (sum of vendor costs)
   │  ├─ marginPercent (overall margin)
   │  └─ sellTotal (vendor total + margin)
   └─ Completeness report
```

### Order State After PDF Upload

```
Sales Order:
├─ aas_status = VENDOR_PDF_RECEIVED
├─ items = [
│   {
│     item_code: "VEN001_ELEC_854430",
│     item_name: "Controller Unit",
│     qty: 10,
│     rate: 100,           (vendor cost)
│     amount: 1000,
│     aas_margin_percent: 7,
│     aas_vendor_rate: 100
│   },
│   // ... more items
│ ]
├─ aas_vendor_bill_total = 5000
├─ aas_vendor_pdf = "file_url"
├─ aas_margin_percent = 7
└─ Ready for bill confirmation
```

---

## Phase 4: Bill Capture (Manual Confirmation)

### User Action

```
User navigates to: Orders > [Select Order]
    ↓
Clicks: "Capture Bill"
    ↓
Fills Form:
├─ Bill Total (extracted, editable)
├─ Bill Reference (extracted, editable)
├─ Bill Date (extracted, editable)
├─ Transport Charge (extracted, editable)
└─ Allow Mismatch checkbox (if needed)
    ↓
POST /api/orders/{id}/vendor-bill
```

### Backend Processing

```
OrderService.captureBill():
├─ Load order
├─ Validate bill data
├─ Check: Bill Total matches invoice items total?
│  ├─ If matches → OK
│  ├─ If mismatch → Reject unless "Allow Mismatch" checked
│  └─ Store bill total and transport charge
├─ Update Sales Order:
│  ├─ aas_vendor_bill_total = confirmed total
│  ├─ aas_vendor_bill_ref = reference
│  ├─ aas_vendor_bill_date = date
│  ├─ aas_transport_charge = charge
│  └─ aas_status = VENDOR_BILL_CAPTURED
└─ Return updated order
```

### Order State After Bill Capture

```
Sales Order:
├─ aas_status = VENDOR_BILL_CAPTURED
├─ aas_vendor_bill_total = 5000 (confirmed)
├─ aas_transport_charge = 500 (added)
└─ Ready for sell order creation
```

---

## Phase 5: Sell Order Creation (Purchase → Sales Conversion)

### User Action

```
User navigates to: Orders > [Select Order]
    ↓
Clicks: "Preview Sell Order"
    ↓
System shows:
├─ Estimated selling price (based on margin)
├─ Margin percentage
└─ Estimated profit amount
    ↓
User can:
├─ Adjust selling rates (if needed)
└─ Confirm
    ↓
Clicks: "Create Sell Order"
    ↓
POST /api/orders/{id}/sell-order
```

### Backend Processing

```
OrderService.createSellOrder():

1. PREPARE SALES SIDE
   ├─ For each order item:
   │  ├─ Get vendor rate (cost)
   │  ├─ Get margin percent
   │  ├─ Calculate: Selling Rate = Cost × (1 + Margin%)
   │  ├─ Check MRP:
   │  │  ├─ If Selling Rate > MRP → Cap at MRP
   │  │  └─ Recalculate effective margin
   │  └─ Create Sales Order Item with selling rate
   │
   └─ Create Sales Order in ERPNext
       ├─ Copies customer (branch) from original
       ├─ Copies items with selling rates
       ├─ Sets item prices (margin-calculated)
       └─ Status: Draft

2. ADD TRANSPORT & ROUNDING
   ├─ Apply transport charge to items/total
   ├─ Calculate rounding adjustment
   └─ Update order total

3. LINK DOCUMENTS
   ├─ Update original Sales Order:
   │  ├─ aas_sell_order_total = selling total
   │  ├─ aas_so_branch = sales order reference
   │  └─ aas_status = SELL_ORDER_CREATED
   │
   └─ Store references for traceability

4. RETURN ORDER DATA
   └─ Include cost, margin, selling totals
```

### Order State After Sell Order Creation

```
Sales Order:
├─ aas_status = SELL_ORDER_CREATED
├─ aas_cost_total = 5000 (vendor costs)
├─ aas_margin_total = 350 (5000 × 7%)
├─ aas_margin_percent = 7
├─ aas_sell_order_total = 5350 (cost + margin)
├─ aas_so_branch = "SO-0001" (sales order reference)
└─ Ready for invoicing
```

---

## Phase 6: Invoice Creation

### User Action

```
User navigates to: Bills > Create Invoice
    ↓
Selects: Order from dropdown
    ↓
Form auto-populates:
├─ Customer (branch)
├─ Items (from sell order)
├─ Selling rates (with margin)
└─ Total amount
    ↓
User can:
├─ Add/modify items
├─ Toggle GST (creates Item Tax Template if needed)
├─ Adjust due date (calculated from customer credit days)
└─ Review total
    ↓
POST /api/invoices
```

### Backend Processing

```
InvoiceService.createInvoice():

1. VALIDATE ORDER
   ├─ Verify order exists
   ├─ Verify all items resolved
   └─ Check no duplicate invoice

2. PREPARE INVOICE
   ├─ Copy customer (branch)
   ├─ Copy items with selling rates
   ├─ Calculate due date:
   │  └─ Invoice Date + Customer.credit_days
   ├─ Copy dates from order
   └─ Prepare line items

3. HANDLE GST (if enabled)
   ├─ Create Item Tax Template in ERPNext
   ├─ Set GST rate per item
   ├─ Calculate tax per line:
   │  └─ Line Amount × (GST% / 100)
   └─ Add tax to total

4. CREATE SALES INVOICE
   ├─ Create in ERPNext with all fields
   ├─ Link to order for traceability
   └─ Return invoice reference

5. UPDATE ORDER
   ├─ aas_status = INVOICED
   ├─ aas_si_branch = invoice reference
   └─ Order marked complete
```

### Final Order State

```
Sales Order (Final):
├─ aas_status = INVOICED
├─ aas_cost_total = 5000
├─ aas_margin_total = 350
├─ aas_margin_percent = 7
├─ aas_sell_order_total = 5350
├─ aas_so_branch = "SO-0001" (link to sales order)
├─ aas_si_branch = "SI-0001" (link to sales invoice)
└─ Order complete

Sales Invoice (Created):
├─ customer = "Branch A"
├─ items = order items
├─ selling_amount = 5350
├─ gst (if applied)
├─ due_date = invoice date + credit days
└─ Outstanding = 5350 (until paid)
```

---

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│ ORDER CREATION (DRAFT)                                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Image Mode OR Item Catalog Mode                                 │
│        ↓                                                         │
│ Create Sales Order with:                                        │
│ ├─ Customer (branch)                                            │
│ ├─ Company                                                       │
│ ├─ Category                                                      │
│ └─ Items (with quantity, vendor cost, default 7% margin)        │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ VENDOR ASSIGNMENT (VENDOR_ASSIGNED)                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ User selects vendor                                              │
│ ↓                                                                │
│ Validation:                                                      │
│ ├─ Vendor exists & active                                       │
│ ├─ Vendor belongs to category                                   │
│ └─ Vendor has invoice template                                  │
│ ↓                                                                │
│ Update order: aas_vendor = selected vendor                      │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ PDF UPLOAD & PARSING (VENDOR_PDF_RECEIVED)                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ User uploads vendor invoice PDF                                 │
│ ↓                                                                │
│ Backend:                                                         │
│ ├─ Extract items, qty, rate, HSN from PDF                       │
│ ├─ Resolve/create items in catalog                              │
│ ├─ Get margin: item's margin OR 7% default                      │
│ ├─ Apply pricing: Selling = Cost × (1 + margin%)                │
│ ├─ Cap at MRP if needed                                         │
│ ├─ Calculate overall margin                                     │
│ └─ Create Purchase Order (vendor cost side)                      │
│ ↓                                                                │
│ Update order:                                                    │
│ ├─ items = resolved items with margins                          │
│ ├─ aas_vendor_bill_total = parsed total                         │
│ ├─ aas_vendor_pdf = file URL                                    │
│ └─ aas_margin_percent = overall margin%                         │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ BILL CAPTURE (VENDOR_BILL_CAPTURED)                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ User confirms bill details:                                      │
│ ├─ Bill total (may adjust from parsed value)                    │
│ ├─ Bill reference                                               │
│ ├─ Bill date                                                     │
│ └─ Transport charge                                              │
│ ↓                                                                │
│ Validation: Bill total matches items or force confirm            │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ SELL ORDER CREATION (SELL_ORDER_CREATED)                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Backend converts to selling side:                                │
│ ├─ For each item:                                               │
│ │  ├─ Cost = vendor rate                                        │
│ │  ├─ Margin = item margin OR 7%                                │
│ │  └─ Selling = cost × (1 + margin%)                            │
│ ├─ Cap at MRP if applicable                                     │
│ ├─ Create Sales Order with selling rates                         │
│ └─ Calculate totals:                                             │
│    ├─ Cost Total                                                │
│    ├─ Margin Total                                              │
│    └─ Selling Total                                             │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ INVOICE CREATION (INVOICED)                                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Create Sales Invoice from order:                                 │
│ ├─ Customer (branch)                                            │
│ ├─ Items with selling rates                                     │
│ ├─ Apply GST if needed                                          │
│ ├─ Due Date = Invoice Date + Customer.credit_days               │
│ └─ Link to order for traceability                               │
│ ↓                                                                │
│ Order marked INVOICED                                            │
│ Invoice ready for payment tracking                              │
└──────────────────────────────────────────────────────────────────┘
```

---

## Key Business Rules

### Margin Rules
- **Default:** 7% (applied to items without specific margin)
- **Item Margin:** If item has aas_margin_percent > 0, use it
- **MRP Override:** If calculated selling > MRP, cap at MRP and recalc margin
- **Effective Margin:** May differ from input if MRP capped

### Item Resolution
- **Catalog Match:** item_code built as VENDOR_CATEGORY_HSN
- **Missing Items:** Auto-created with normalized HSN code
- **Soft Delete:** Old items can be re-enabled if soft-deleted

### Bill Validation
- **Mismatch Check:** Bill total vs items total (configurable)
- **Transport Charge:** Optional, added to final total
- **Rounding:** Adjustments handled (not forced to match exactly)

---

## Error Scenarios

| Scenario | Error | Resolution |
|----------|-------|-----------|
| No vendor template | Template Required | Configure template for vendor |
| Item not in catalog | Auto-create with HSN | System creates item automatically |
| Bill total mismatch | Mismatch Detected | Check "Allow Mismatch" or correct total |
| MRP exceeded | Vendor rate > MRP | Cannot proceed (pricing error) |
| Missing category | Category Required | Specify category at creation |
| Vendor inactive | Vendor Inactive | Select active vendor only |

---

## Summary

The Order Workflow is a **6-stage pipeline** that transforms a purchase order (vendor cost) into a sales invoice (customer selling price) with margin calculation and price capping at MRP. Each stage validates data, resolves items/margins, and prepares for the next stage, culminating in an invoice ready for payment tracking.
