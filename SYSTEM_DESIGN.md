# AAS (Agri-Automation System)
## System Design & Architecture (As-Implemented)

---

## 1. HIGH-LEVEL ARCHITECTURE (CURRENT CODE)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER (Angular)                         │
├─────────────────────────────────────────────────────────────────────────┤
│  Admin / Vendor / Shop / Helper UI Modules                               │
│  - Items, Vendors, Categories                                            │
│  - Orders, Invoices, Payments                                            │
│  - Reports, Dashboard, Stock                                             │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                         HTTP REST APIs (JWT Auth)
                                   │
┌──────────────────────────────────▼──────────────────────────────────────┐
│                     MIDDLEWARE API (Spring Boot)                         │
│  - Auth (ERPNext login → JWT)                                             │
│  - Domain APIs (orders, invoices, payments, reports)                      │
│  - ERPNext REST proxy/normalization                                       │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                            ERPNext REST API
                                   │
┌──────────────────────────────────▼──────────────────────────────────────┐
│                        ERP CORE (ERPNext / Frappe)                        │
│  - Multi-company ERP instance                                             │
│  - Business data + workflows                                              │
│  - Web UI (ERPNext Desk)                                                  │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                 MariaDB
                                   │
┌──────────────────────────────────▼──────────────────────────────────────┐
│                          DATA PERSISTENCE                                │
│  - ERPNext MariaDB database                                               │
│  - Redis (cache/queues)                                                   │
│  - Workers + Scheduler (ERP background jobs)                              │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. COMPONENT RESPONSIBILITIES (CODE-BASED)

### 2.1 Angular Frontend (`ui/`)
**Responsibility**: Role-based web UI and client-side flows.

**Key Modules**:
- Orders, Invoices, Payments
- Items, Vendors, Categories, Branches
- Bills, Stock
- Dashboard + Reports placeholders

**Runtime**:
- Dev server via `ng serve`
- API proxy to middleware via `ui/proxy.conf.json` (`/api` → `http://localhost:8083`)

---

### 2.2 Middleware API (`mw/` Spring Boot)
**Responsibility**: Authentication, access control, ERPNext integration, domain API surface.

**Key Responsibilities**:
- **Auth**: ERPNext login → session cookie → JWT token
- **Role Mapping**: ERPNext roles to app roles (ADMIN, VENDOR, SHOP, HELPER)
- **Domain APIs**: Orders, invoices, payments, reports, master data
- **Order Workflow**: Branch image upload, vendor assignment, vendor PDF OCR, vendor bill capture, sell order creation
- **ERPNext Proxy**: Use ERPNext REST resources and methods via Feign client
- **Files**: Attachments to ERPNext (`/api/method/upload_file`)
- **Vendor Templates**: Optional vendor invoice parsing templates stored on ERPNext `Supplier` records (used during vendor PDF OCR).

**Key Packages**:
- Controllers: `mw/src/main/java/com/aas/mw/controller/*`
- Services: `mw/src/main/java/com/aas/mw/service/*`
- ERP Client: `mw/src/main/java/com/aas/mw/client/*`
- Security: `mw/src/main/java/com/aas/mw/config/*`

**Primary API Endpoints (selected)**:
- `POST /api/auth/login`
- `GET /api/me`
- `GET/POST /api/items`
- `GET/POST /api/vendors`
- `GET/POST /api/categories`
- `GET/POST /api/shops`
- `POST /api/orders`, `GET /api/orders`, `PUT /api/orders/{id}`
- `POST /api/orders/{id}/status`
- `POST /api/orders/{id}/assign-vendor`
- `POST /api/orders/branch-image` (create order + attach branch image)
- `POST /api/orders/{id}/image` (attach branch image)
- `POST /api/orders/{id}/vendor-pdf` (OCR + purchase order creation)
- `POST /api/orders/{id}/vendor-bill` (capture vendor bill)
- `GET /api/orders/{id}/sell-preview`
- `POST /api/orders/{id}/sell-order`
- `DELETE /api/orders/{id}`
- `GET /api/orders/export`
- `POST /api/invoices`, `GET /api/invoices`, `GET /api/invoices/export`, `GET /api/invoices/{id}/pdf`
- `POST /api/payments`
- `GET /api/ocr/health`
- `GET /api/meta/vendors/fields`
- `POST /api/vendors/{id}/invoice-template/sample`
- `DELETE /api/vendors/{id}/invoice-template`
- `GET /api/reports/*` (+ export endpoints)
- `POST /api/setup/ensure`

---

### 2.3 ERP Core (`erpmodule/` ERPNext)
**Responsibility**: System of record, workflows, data integrity, and ERP admin UI.

**Stack**:
- ERPNext (Frappe) v15.x
- MariaDB 10.6
- Redis (cache + queues)
- Workers + Scheduler
- WebSocket + Nginx

**Multi-Company Setup**:
- Single ERPNext site
- Multiple companies (one per hotel/restaurant)
- Role-based data access and consolidated reporting

---

## 3. AUTH & SECURITY FLOW (IMPLEMENTED)

1. Client submits username/password to `POST /api/auth/login`.
2. Middleware logs in to ERPNext (`/api/method/login`) and receives ERP session cookie.
3. Middleware resolves ERP roles → App role.
4. Middleware returns JWT (`Bearer`) to client.
5. Client uses JWT for subsequent API calls.
6. Middleware injects ERP session (from in-memory store) for ERPNext requests.

**Note**: ERP session cookies are stored in-memory with TTL equal to JWT expiry.

---

## 4. DATA FLOW (IMPLEMENTED)

- **UI → Middleware**: REST calls secured by JWT.
- **Middleware → ERPNext**: REST calls via Feign client, normalizes ERPNext responses.
- **ERPNext → MariaDB**: Data persistence and ERP workflows.
- **File uploads**: Middleware uploads branch images/vendor PDFs to ERPNext via `/api/method/upload_file` and links File URLs to `Sales Order`.

---

## 5. ORDER WORKFLOW (IMPLEMENTED)

**Branch Image → Vendor PDF → Vendor Bill → Sell Order**
1. `POST /api/orders/branch-image` creates a `Sales Order` with a placeholder item and attaches the branch image. Status starts at `DRAFT`.
2. `POST /api/orders/{id}/assign-vendor` sets `aas_vendor` and transitions to `VENDOR_ASSIGNED`.
3. `POST /api/orders/{id}/vendor-pdf` runs OCR, creates a `Purchase Order`, updates items, and stores `aas_vendor_pdf` and vendor bill metadata. Status → `VENDOR_PDF_RECEIVED`.
4. `POST /api/orders/{id}/vendor-bill` creates a `Purchase Invoice` and stores bill totals + ref. Status → `VENDOR_BILL_CAPTURED`.
5. `GET /api/orders/{id}/sell-preview` returns margin-based sell totals.
6. `POST /api/orders/{id}/sell-order` creates a branch `Sales Order` and `Sales Invoice`, links them back to the source order. Status → `SELL_ORDER_CREATED`.

**State Machine (enforced in middleware)**:
`DRAFT → VENDOR_ASSIGNED → VENDOR_PDF_RECEIVED → VENDOR_BILL_CAPTURED → SELL_ORDER_CREATED` (with controlled transitions and validation).

---

## 6. CUSTOM FIELDS / SETUP (PROVISIONED BY `POST /api/setup/ensure`)

**Sales Order**:
- `aas_vendor` (Link → Supplier)
- `aas_status` (Select)
- `aas_margin_percent` (Float)
- `aas_branch_image` (Attach)
- `aas_vendor_pdf` (Attach)
- `aas_po` (Link → Purchase Order)
- `aas_so_branch` (Link → Sales Order)
- `aas_si_branch` (Link → Sales Invoice)
- `aas_vendor_bill_total` (Currency)
- `aas_vendor_bill_ref` (Data)
- `aas_vendor_bill_date` (Date)
- `aas_pi_vendor` (Link → Purchase Invoice)
- `aas_sell_order_total` (Currency)

**Purchase Order / Purchase Invoice / Sales Invoice**:
- `aas_source_sales_order` (Link → Sales Order)

**Item**:
- `aas_margin_percent` (Float)
- `aas_vendor_rate` (Currency)
- `aas_packaging_unit` (Data)

**Sales Order Item**:
- `aas_margin_percent` (Float)
- `aas_vendor_rate` (Currency)

**Supplier (Vendor custom fields)**:
- `aas_branch_name`, `aas_address`, `aas_phone`, `aas_gst_no`, `aas_pan_no`, `aas_food_license_no`, `aas_priority`
- Invoice template (auto-detected from sample PDF): `aas_invoice_template_enabled`, `aas_invoice_template_key`, `aas_invoice_template_sample_pdf`

---

## 7. LOCAL DEVELOPMENT (CURRENT)

- ERPNext: `erpmodule/pwd.yml` via Docker Compose
- Middleware: Spring Boot on `http://localhost:8083`
- UI: Angular dev server on `http://localhost:4200` with proxy to middleware

---

## 8. OUTDATED / NOT IN CODEBASE

The following items appear in older architecture docs but are **not implemented**:
- Directus (Generic CRUD middleware)
- React frontend (current frontend is Angular)
- Metabase/Power BI dashboards (reports are currently middleware endpoints)
- Postgres/MySQL persistence outside ERPNext (current persistence is ERPNext + MariaDB)

---

## 7. FEASIBILITY NOTE

The current codebase implements a **middleware + ERPNext** architecture where ERPNext is the system of record and the middleware provides simplified APIs for the Angular UI. The prior React/Directus/Metabase model is not present in the repository.
