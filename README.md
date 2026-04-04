# AAS - Automated Accounting System

**Complete Documentation Hub**

---

## 📋 What is AAS?

AAS is a **three-tier procurement and financial management platform** built on:
- **Frontend:** Angular 17 + Material Design
- **Middleware:** Spring Boot 3.4 with REST APIs
- **Backend:** ERPNext as the system of record

The system manages the complete **order-to-invoice-to-payment lifecycle** for multi-branch businesses with vendor management, inventory tracking, and comprehensive financial reporting.

---

## 🎯 Key Features

```
┌─────────────────────────────────────────────────────────────┐
│ ORDER WORKFLOW                                              │
│ ├─ Dual creation modes (image/catalog)                      │
│ ├─ Vendor assignment & PDF upload                           │
│ ├─ AI-powered bill parsing                                  │
│ └─ Margin calculation with MRP constraints                  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ VENDOR OPERATIONS                                           │
│ ├─ Vendor-centric KPI dashboard                             │
│ ├─ Order & ledger tracking                                  │
│ ├─ Settlement state management                              │
│ └─ Exception alerts & follow-ups                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ BRANCH OPERATIONS                                           │
│ ├─ Branch-centric receivables tracking                      │
│ ├─ Payment collection analytics                             │
│ ├─ Outstanding receivables analysis                         │
│ └─ Financial health scoring                                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ BILLING & INVOICING                                         │
│ ├─ Invoice creation (from order or manual)                  │
│ ├─ GST tax handling                                         │
│ ├─ Payment recording & allocation                           │
│ └─ Outstanding tracking                                     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ INVENTORY MANAGEMENT                                        │
│ ├─ Stock quantity tracking                                  │
│ ├─ Low-stock threshold alerts                               │
│ ├─ Item margin configuration (7% default)                   │
│ └─ Vendor-wise inventory grouping                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ MASTER DATA                                                 │
│ ├─ Vendor management                                        │
│ ├─ Branch/Customer setup                                    │
│ ├─ Item catalog                                             │
│ └─ Category organization                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 📚 Documentation Structure

### **Core Workflows** (Start Here!)

These documents explain the main business processes:

1. **[ORDER_WORKFLOW.md](CORE_FLOWS/ORDER_WORKFLOW.md)**
   - Complete order lifecycle: creation → vendor assignment → PDF upload → invoicing
   - Margin calculation with MRP constraints
   - Two creation modes (image-based OCR or item catalog)
   - Status pipeline and data flow

2. **[VENDOR_OPERATIONS.md](CORE_FLOWS/VENDOR_OPERATIONS.md)**
   - Vendor-centric operational dashboard
   - Summary view, drill-down, orders, ledger, settlement
   - KPI tracking and exception alerts
   - Payment status and aging analysis

3. **[BRANCH_OPERATIONS.md](CORE_FLOWS/BRANCH_OPERATIONS.md)**
   - Branch-centric receivables tracking
   - Order pipeline, invoices, outstanding receivables
   - Ledger with running balance
   - Financial health assessment

4. **[BILLING_INVOICE.md](CORE_FLOWS/BILLING_INVOICE.md)**
   - Invoice creation (from order or manual)
   - GST taxation and Item Tax Templates
   - Due date calculation with credit days
   - Payment recording and allocation

### **System Design** (Deep Dives)

Technical documentation of key systems:

5. **[ARCHITECTURE.md](ARCHITECTURE.md)** ⭐ **START HERE for Technical Overview**
   - Three-tier architecture with detailed diagrams
   - All 17 backend controllers and APIs
   - Service layer organization
   - Complete order-to-invoice data flow
   - Security, authentication, and guard patterns

6. **[STOCK_ITEM_MAINTENANCE.md](SYSTEM_DESIGN/STOCK_ITEM_MAINTENANCE.md)**
   - Item creation, retrieval, updates, deletion
   - Vendor linkage and categorization
   - Item code generation (VENDOR-CATEGORY-HSN format)
   - Catalog storage in ERPNext

7. **[ITEM_MARGIN_FLOW.md](SYSTEM_DESIGN/ITEM_MARGIN_FLOW.md)**
   - Item margin system (7% default)
   - Margin resolution priority
   - MRP capping logic (if calculated price > MRP)
   - Per-vendor pricing (localStorage)
   - Complete margin calculation examples

8. **[CATALOG_STORAGE_SYSTEM.md](SYSTEM_DESIGN/CATALOG_STORAGE_SYSTEM.md)**
   - How items are stored (ERPNext Item doctype)
   - Item creation flow (manual vs. PDF auto-creation)
   - Item code generation strategies
   - Retrieval, search, filtering, pagination
   - Item updates and soft-deletion

9. **[STOCK_PATTERN_ANALYSIS.md](SYSTEM_DESIGN/STOCK_PATTERN_ANALYSIS.md)**
   - Hybrid state pattern (API + localStorage)
   - Quantity field fallback mechanism
   - Stock threshold configuration
   - Low-stock detection and alerts
   - Vendor grouping and summarization

### **Quick Reference**

Supporting documentation:

- **[DOCUMENTATION_AUDIT.md](DOCUMENTATION_AUDIT.md)** - How docs are organized and maintained

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  FRONTEND (Angular 17)                                          │
│  ├─ Orders, Vendor Ops, Branch Ops                              │
│ ├─ Bills, Stock, Master Data                                    │
│ └─ Dashboard with KPIs                                          │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP REST API
┌──────────────────────▼──────────────────────────────────────────┐
│  MIDDLEWARE (Spring Boot 3.4)                                   │
│ ├─ Authentication & Session Management                          │
│ ├─ Business Logic Services                                      │
│ ├─ Order Processing & Margin Calculation                        │
│ ├─ PDF Parsing (OCR, AI templates)                              │
│ ├─ Ledger & Reporting                                           │
│ └─ ERPNext Integration (Feign clients)                           │
└──────────────────────┬──────────────────────────────────────────┘
                       │ ERPNext API
┌──────────────────────▼──────────────────────────────────────────┐
│  ERPNEXT (System of Record)                                     │
│ ├─ Sales Order (orders)                                         │
│ ├─ Purchase Order (vendor costs)                                │
│ ├─ Sales Invoice (customer invoices)                            │
│ ├─ Payment Entry (payments)                                     │
│ ├─ Supplier (vendors)                                           │
│ ├─ Customer (branches)                                          │
│ ├─ Item (catalog)                                               │
│ └─ Company, GL Accounts, Tax, etc.                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Core Data Flows

### Order-to-Invoice Flow

```
DRAFT
  ↓ (Select Vendor)
VENDOR_ASSIGNED
  ↓ (Upload PDF → Parse with AI)
VENDOR_PDF_RECEIVED
  ↓ (Confirm Bill Details)
VENDOR_BILL_CAPTURED
  ↓ (Convert to Sales Order with Margin)
SELL_ORDER_CREATED
  ↓ (Create Customer Invoice)
INVOICED
  ↓ (Record Payment)
PAID
```

### Margin Calculation Flow

```
Item has margin?
├─ YES → Use item's aas_margin_percent
└─ NO → Use default 7%
    ↓
Selling Rate = Vendor Rate × (1 + Margin% / 100)
    ↓
Check MRP:
├─ If Calculated > MRP → Cap at MRP, recalc margin
└─ Else → Use calculated rate
```

---

## 🚀 Getting Started

### For Users

1. Read [ORDER_WORKFLOW.md](CORE_FLOWS/ORDER_WORKFLOW.md) to understand order creation
2. Read [BILLING_INVOICE.md](CORE_FLOWS/BILLING_INVOICE.md) to understand invoicing
3. Read [VENDOR_OPERATIONS.md](CORE_FLOWS/VENDOR_OPERATIONS.md) for operational tracking
4. Read [BRANCH_OPERATIONS.md](CORE_FLOWS/BRANCH_OPERATIONS.md) for receivables

### For Developers

1. Start with [ARCHITECTURE.md](ARCHITECTURE.md) for system overview
2. Read [STOCK_ITEM_MAINTENANCE.md](SYSTEM_DESIGN/STOCK_ITEM_MAINTENANCE.md) for item operations
3. Read [ITEM_MARGIN_FLOW.md](SYSTEM_DESIGN/ITEM_MARGIN_FLOW.md) for pricing logic
4. Read [CATALOG_STORAGE_SYSTEM.md](SYSTEM_DESIGN/CATALOG_STORAGE_SYSTEM.md) for data persistence
5. Reference [ORDER_WORKFLOW.md](CORE_FLOWS/ORDER_WORKFLOW.md) for complete data flows

### For Operations

1. Read [VENDOR_OPERATIONS.md](CORE_FLOWS/VENDOR_OPERATIONS.md) for vendor management
2. Read [BRANCH_OPERATIONS.md](CORE_FLOWS/BRANCH_OPERATIONS.md) for receivables
3. Check docs/SETUP.md for environment configuration

---

## 🔑 Key Concepts

### Order Status Pipeline
- **DRAFT** → Not confirmed
- **VENDOR_ASSIGNED** → Vendor selected, awaiting PDF
- **VENDOR_PDF_RECEIVED** → Invoice uploaded, items parsed
- **VENDOR_BILL_CAPTURED** → Bill confirmed, margins calculated
- **SELL_ORDER_CREATED** → Internal documents created for sales
- **INVOICED** → Customer invoice created

### Margin System
- **Default:** 7% on all items without specific margin
- **Item Margin:** If item has `aas_margin_percent` > 0, use it
- **MRP Cap:** If calculated selling price > MRP, cap at MRP

### Settlement States
- **SETTLED** → Balance = 0, fully paid
- **OPEN** → Outstanding balance within terms
- **OVERDUE** → Outstanding balance > 30 days old

### Stock Management
- **Threshold-Based:** Per-device thresholds stored in localStorage
- **Quantity Fields:** Resilient parsing (stock_qty → actual_qty → quantity → qty)
- **Low Stock:** Alert when quantity ≤ threshold

---

## 📊 API Endpoints (Summary)

See [ARCHITECTURE.md](ARCHITECTURE.md) for complete endpoint reference.

**Key Controllers:**
- `AuthController` - Authentication & session
- `OrdersController` - Order CRUD & lifecycle
- `InvoiceController` - Invoice management
- `PaymentsController` - Payment recording
- `MasterDataController` - Items, vendors, branches, categories
- `VendorOpsController` - Vendor operational view
- `BranchOpsController` - Branch operational view

---

## 📁 Project Structure

```
AAS/
├─ README.md (this file)
├─ ARCHITECTURE.md (system overview)
├─ DOCUMENTATION_AUDIT.md (doc management)
│
├─ CORE_FLOWS/ (main business processes)
│  ├─ ORDER_WORKFLOW.md
│  ├─ VENDOR_OPERATIONS.md
│  ├─ BRANCH_OPERATIONS.md
│  └─ BILLING_INVOICE.md
│
├─ SYSTEM_DESIGN/ (technical deep dives)
│  ├─ STOCK_ITEM_MAINTENANCE.md
│  ├─ ITEM_MARGIN_FLOW.md
│  ├─ CATALOG_STORAGE_SYSTEM.md
│  └─ STOCK_PATTERN_ANALYSIS.md
│
├─ ui/ (Angular frontend)
├─ mw/ (Spring Boot middleware)
├─ erpmodule/ (ERPNext stack)
├─ scripts/ (utilities & setup)
│
└─ docs/ (supporting docs)
   ├─ SETUP.md (environment setup)
   └─ archived/ (historical docs)
```

---

## 🔗 External Resources

- **ERPNext Documentation:** https://docs.erpnext.com
- **Angular Documentation:** https://angular.io/docs
- **Spring Boot Documentation:** https://spring.io/projects/spring-boot

---

## ❓ FAQ

**Q: Where is data stored?**
A: All persistent data is stored in ERPNext (the system of record). The middleware caches some data in memory, and the frontend uses localStorage for user preferences.

**Q: How are items created?**
A: Manually via the Items Master page, or automatically when a vendor PDF is parsed during order processing.

**Q: What's the difference between vendor cost and selling price?**
A: Vendor cost comes from the PDF. Selling price = vendor cost × (1 + margin%). If selling price would exceed MRP, it's capped at MRP instead.

**Q: How are margins calculated?**
A: Each item has an `aas_margin_percent` (or default 7%). Formula: `Selling Rate = Vendor Rate × (1 + Margin% / 100)`. If this exceeds MRP, the rate is capped and margin recalculated.

**Q: What happens if an invoice is overpaid?**
A: The surplus is stored as an unallocated amount that can be applied to future invoices.

**Q: How are branch due dates calculated?**
A: `Due Date = Invoice Date + Customer.aas_credit_days`. Credit days are configured per branch/customer.

**Q: Can items be deleted?**
A: Items are soft-deleted (disabled flag set to 1) but never physically removed, preserving audit trail and historical orders.

---

## 📝 Document Status

| Document | Status | Last Updated |
|----------|--------|--------------|
| README.md | ✅ Current | 2026-04-04 |
| ARCHITECTURE.md | ✅ Current | 2026-04-04 |
| ORDER_WORKFLOW.md | ✅ Current | 2026-04-04 |
| VENDOR_OPERATIONS.md | ✅ Current | 2026-04-04 |
| BRANCH_OPERATIONS.md | ✅ Current | 2026-04-04 |
| BILLING_INVOICE.md | ✅ Current | 2026-04-04 |
| STOCK_ITEM_MAINTENANCE.md | ✅ Current | 2026-04-04 |
| ITEM_MARGIN_FLOW.md | ✅ Current | 2026-04-04 |
| CATALOG_STORAGE_SYSTEM.md | ✅ Current | 2026-04-04 |
| STOCK_PATTERN_ANALYSIS.md | ✅ Current | 2026-04-04 |

---

## 🤝 Contributing

When updating documentation:
1. Ensure changes are reflected in relevant docs
2. Update the status table above
3. Test all code examples
4. Validate data flow diagrams

---

## 📞 Support

For questions about:
- **Architecture:** See [ARCHITECTURE.md](ARCHITECTURE.md)
- **Order workflow:** See [ORDER_WORKFLOW.md](CORE_FLOWS/ORDER_WORKFLOW.md)
- **Items & pricing:** See [ITEM_MARGIN_FLOW.md](SYSTEM_DESIGN/ITEM_MARGIN_FLOW.md)
- **Operations:** See [VENDOR_OPERATIONS.md](CORE_FLOWS/VENDOR_OPERATIONS.md) or [BRANCH_OPERATIONS.md](CORE_FLOWS/BRANCH_OPERATIONS.md)

---

**Last Updated:** 2026-04-04
**Documentation Status:** ✅ Complete
**Total Documents:** 10 active guides
