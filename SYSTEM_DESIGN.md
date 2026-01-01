# Restaurant Multi-Shop Order Management System
## System Design & Architecture

---

## 1. HIGH-LEVEL ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER (React)                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐│
│  │   Admin      │  │   Vendor     │  │   Shop       │  │   Helper     ││
│  │   Web App    │  │   Web App    │  │   Web App    │  │   Web App    ││
│  │              │  │              │  │              │  │              ││
│  │ - Masters    │  │ - Assigned   │  │ - Place      │  │ - Deliveries ││
│  │ - Orders     │  │   Orders     │  │   Orders     │  │ - Track      ││
│  │ - Invoices   │  │ - Status     │  │ - Invoices   │  │   Delivery   ││
│  │ - Payments   │  │   Updates    │  │ - Payments   │  │   Status     ││
│  │ - Reports    │  │ - Reports    │  │ - Reports    │  │              ││
│  │ - Analytics  │  │              │  │              │  │              ││
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘│
│                                                                         │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                    HTTP REST APIs (JWT Auth)
                                   │
┌──────────────────────────────────▼──────────────────────────────────────┐
│              API GATEWAY / REVERSE PROXY (Nginx)                        │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   ├─────────────────────┬──────────────────┐
                                   │                     │                  │
┌──────────────────────────────────▼─────────────────────▼──────────────────▼┐
│                       BACKEND SERVICES LAYER                              │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │  Core Business  │  │  Directus        │  │  Background Jobs     │  │
│  │  Service        │  │  (Generic CRUD)  │  │  (Spring Scheduler)  │  │
│  │  (Spring Boot)  │  │                  │  │                      │  │
│  │                 │  │  - Auto REST API │  │ - Monthly Invoicing  │  │
│  │ • Auth & RBAC   │  │  - Data Model    │  │ - Report Generation  │  │
│  │ • Orders        │  │  - Admin UI      │  │ - Email Notifications│  │
│  │ • Invoices      │  │  - Webhooks      │  │                      │  │
│  │ • Payments      │  │                  │  │                      │  │
│  │ • Ledgers       │  │ Masters:         │  │                      │  │
│  │ • Deliveries    │  │ - Categories     │  │                      │  │
│  │ • Business Logic│  │ - Items          │  │                      │  │
│  │ • Vendor Assign │  │ - Vendors        │  │                      │  │
│  │ • Invoice Gen   │  │ - Shops          │  │                      │  │
│  │                 │  │ - Helpers        │  │                      │  │
│  └─────────────────┘  └──────────────────┘  └──────────────────────┘  │
│                                                                          │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
                            JDBC / SQL
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                      DATA PERSISTENCE LAYER                              │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌────────────────────────────────────┐  ┌─────────────────────────┐   │
│  │   PostgreSQL/MySQL RDBMS           │  │   File Storage          │   │
│  │                                    │  │   (PDFs, Exports)       │   │
│  │ Tables:                            │  │                         │   │
│  │ - User, Role                       │  │ • Invoice PDFs          │   │
│  │ - Category, Item                   │  │ • Excel Reports         │   │
│  │ - Vendor, Shop, Helper             │  │                         │   │
│  │ - Order, OrderItem                 │  │                         │   │
│  │ - Invoice, InvoiceLine             │  │                         │   │
│  │ - Payment                          │  │                         │   │
│  │ - VendorLedger, ShopLedger         │  │                         │   │
│  │ - Delivery                         │  │                         │   │
│  │ - AuditLog                         │  │                         │   │
│  └────────────────────────────────────┘  └─────────────────────────┘   │
│                                                                          │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                        ANALYTICS LAYER                                   │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Metabase / Power BI (connected to same DB)                      │   │
│  │                                                                  │   │
│  │ • Vendor monthly orders per shop dashboard                      │   │
│  │ • Vendor monthly billing & payment summary                      │   │
│  │ • Shop category-wise monthly usage report                       │   │
│  │ • Outstanding balance & aging report                            │   │
│  │ • Payment tracking & reconciliation dashboard                   │   │
│  │ • Embedded in React Admin as iframes                            │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---
## 2. COMPONENT RESPONSIBILITIES

### 2.1 Core Backend Service (Spring Boot)
**Responsibility**: Business logic & domain workflows

**Modules**:
- **Auth & RBAC Module**
  - JWT-based authentication
  - Role-based access control: Admin, Vendor, Shop, Helper
  - Password reset flow
  
- **Order Management Module**
  - Create orders (Admin/Shop)
  - Auto/manual vendor assignment logic
  - Order status transitions: Accepted → Preparing → Ready → Delivered
  - Filters: date, vendor, shop, status
  - Export to Excel/PDF
  
- **Invoice & Billing Module**
  - Auto-generate invoices from orders
  - Tax & discount calculations
  - Monthly billing aggregations
  - PDF generation (iText/OpenPDF)
  
- **Payment & Ledger Module**
  - Record payments (to vendors, from shops)
  - Payment status tracking
  - Outstanding balance calculations
  - Ledger entries per vendor/shop
  
- **Delivery Management Module**
  - Track today/upcoming deliveries for Helpers
  - Mark delivery as completed
  - Link deliveries to orders
  
- **Reports Module**
  - Vendor: monthly orders, billing, payment summaries
  - Shop: category-wise usage, billing, payment reports
  - Background jobs for monthly report pre-aggregation

---

### 2.2 Directus (Generic Data Engine)
**Responsibility**: Auto CRUD APIs + Admin UI for master data

**Features**:
- Connects directly to PostgreSQL/MySQL DB
- Auto-generates REST + GraphQL APIs for all tables
- Provides data model UI for schema management
- Built-in admin interface for data entry
- Role-based permissions at data level
- Webhooks for triggering custom business logic

**Managed Tables** (CRUD only, no complex logic):
- Category
- Item (with unit, categoryId)
- Vendor
- Shop
- Helper
- User (basic)

**Optional: Can be replaced** with custom Spring Boot CRUD endpoints if preferred; Directus is purely optional middleware.

---

### 2.3 React Frontend (Multi-panel)
**Responsibility**: User-facing web applications with role-based views

#### Admin Panel
- Master data management (or delegate to Directus UI)
- Order creation & management
- Invoice & payment dashboard
- Embedded BI dashboards (Metabase iframes)
- Export reports
- System monitoring & audit logs

#### Vendor Panel
- View assigned orders
- Update order status
- View billing & payment reports
- Track delivery

#### Shop Panel
- Place new orders
- Track order status
- View & download invoices
- Manage payments & view outstanding
- View category-wise usage reports

#### Helper Panel
- View today/upcoming deliveries
- Mark deliveries completed
- Update delivery status
- (Optional) manage items/categories if assigned

---

### 2.4 Background Jobs (Spring Scheduler)
**Responsibility**: Scheduled tasks for business operations

**Jobs**:
- **Monthly Invoicing Job** (runs on 1st of month)
  - Auto-generate invoices from orders for previous month
  - Aggregate order lines by shop/vendor
  - Calculate totals & taxes
  
- **Report Pre-aggregation Job** (runs on 5th)
  - Pre-compute vendor monthly summaries
  - Compute shop category-wise totals
  - Store in a summary table or cache for fast BI queries
  
- **Email Notification Job** (on-demand or hourly)
  - Send order status notifications
  - Send payment reminders
  - Send monthly billing statements

---

### 2.5 Metabase / Power BI (Analytics & BI)
**Responsibility**: Self-service analytics & embedded dashboards

**Data Source**: Direct SQL connection to PostgreSQL/MySQL

**Dashboards**:
- **Vendor Portal**
  - Monthly orders received by shop
  - Monthly billing and payment status
  - Payment aging analysis
  
- **Shop Portal**
  - Category-wise monthly usage (quantity, amount)
  - Monthly invoice & payment tracker
  - Outstanding balance by vendor
  
- **Admin / Executive Dashboard**
  - System-wide order volume trends
  - Payment reconciliation status
  - Vendor & shop performance metrics

---

## 3. DATA PERSISTENCE LAYER

### 3.1 RDBMS (PostgreSQL / MySQL)

**Entity-Relationship Overview**:
```
User ──┬─→ Role (ADMIN, VENDOR, SHOP, HELPER)
       └─→ UserRole (many-to-many)

Shop ──→ Order (1:N)
Vendor ──→ Order (1:N)
Helper ──→ Delivery (1:N)

Order ──┬─→ OrderItem (1:N)
        ├─→ Invoice (1:1 or 1:N if split)
        └─→ Delivery (1:1)

Invoice ──→ InvoiceLine (1:N)

Category ──→ Item (1:N)

Payment ──→ Vendor or Shop (N:1)

VendorLedger ──→ Vendor (N:1)
ShopLedger ──→ Shop (N:1)

AuditLog ──→ User & Action (N:1)
```

**Key Tables**:
- `user`: id, email, password_hash, role_id, created_at
- `role`: id, name (ADMIN, VENDOR, SHOP, HELPER)
- `category`: id, name, description
- `item`: id, name, category_id, unit (kg, litre, pcs, etc), price
- `vendor`: id, name, contact_person, phone, email
- `shop`: id, name, location, phone, contact_person
- `helper`: id, name, phone, assigned_shops (JSON or link table)
- `order`: id, shop_id, vendor_id, order_date, status (PENDING/ACCEPTED/PREPARING/READY/DELIVERED), total_amount
- `order_item`: id, order_id, item_id, quantity, unit_price, line_total
- `invoice`: id, invoice_number, order_id, invoice_date, due_date, total_amount, paid_amount, status
- `invoice_line`: id, invoice_id, item_id, quantity, unit_price, line_total
- `payment`: id, payer_type (SHOP/VENDOR), payer_id, payee_type, payee_id, amount, payment_date, status
- `vendor_ledger`: id, vendor_id, transaction_type (CREDIT/DEBIT), amount, reference (invoice_id/payment_id), posting_date
- `shop_ledger`: id, shop_id, transaction_type, amount, reference, posting_date
- `delivery`: id, order_id, helper_id, scheduled_date, delivered_date, status (PENDING/IN_TRANSIT/DELIVERED), notes
- `audit_log`: id, user_id, action, table_name, record_id, changes (JSON), timestamp

---

## 4. REQUIREMENT SATISFACTION CHECKLIST

### Admin Requirements
- [ ] Login & forgot password
- [ ] Create/update/delete categories
- [ ] Create/update/delete items with units
- [ ] Create/update/delete vendors
- [ ] Create/update/delete shops
- [ ] Create/update/delete helpers
- [ ] Create orders for any shop
- [ ] Assign vendors to orders (auto/manual)
- [ ] Track order status
- [ ] List orders with filters & export
- [ ] Generate invoices
- [ ] Manage payments (to vendors, from shops)
- [ ] View payment ledgers
- [ ] View monthly reports (vendor & shop)
- [ ] Embed BI dashboards

### Vendor Requirements
- [ ] Login
- [ ] View assigned orders
- [ ] Update order status
- [ ] View order history
- [ ] View monthly billing report
- [ ] View payment status

### Shop Requirements
- [ ] Login
- [ ] Place new order
- [ ] Track order status
- [ ] View invoices & download PDF
- [ ] View category-wise monthly usage
- [ ] Manage payments & view outstanding
- [ ] View payment history

### Helper Requirements
- [ ] Login
- [ ] View today's deliveries
- [ ] View upcoming deliveries
- [ ] Mark delivery as completed
- [ ] Track delivery status
- [ ] (Optional) Manage items/categories

---

## 5. TECH STACK SUMMARY

| Layer | Technology | Purpose |
|-------|-----------|----------|
| Frontend | React, React Router, React Query, Material UI | Multi-role web apps |
| Backend | Spring Boot 3.x, Spring Security, Spring Data JPA | Core business logic |
| Generic Middleware (Optional) | Directus | Auto CRUD & admin |
| Database | PostgreSQL / MySQL | Data persistence |
| PDF/Excel | iText, Apache POI | Report & invoice generation |
| Scheduling | Spring @Scheduled | Monthly jobs |
| Analytics | Metabase (OSS) or Power BI | Self-service BI |
| API Gateway | Nginx | Reverse proxy, SSL termination |
| Auth | JWT (Spring Security) | Stateless auth |

---

## 6. DEPLOYMENT & OPERATIONS

### Development Environment
```
Docker Compose:
  - PostgreSQL service
  - Spring Boot API service
  - React dev server
  - Directus (optional) service
  - Metabase service
```

### Production Environment
```
Cloud deployment (AWS/GCP/Azure):
  - Managed PostgreSQL (RDS/Cloud SQL)
  - Spring Boot on Kubernetes or Managed Container Service
  - React static hosting (S3 + CloudFront, or CDN)
  - Metabase on separate container
  - File storage: S3 or equivalent for PDFs/exports
  - SSL/TLS at API Gateway (Nginx or Cloud LB)
```

### CI/CD
- GitHub Actions or GitLab CI
- Build & test on PR
- Deploy to staging on merge to develop
- Deploy to production on merge to main
- Automated DB migrations (Flyway/Liquibase)

---

## 7. FEASIBILITY CONCLUSION

**✓ ALL REQUIREMENTS SATISFIABLE**

- **Generic CRUD**: Directus or custom Spring Boot endpoints
- **Role-based access**: Spring Security + React routing
- **Complex workflows**: Core Spring Boot service
- **Monthly reporting**: Background jobs + pre-aggregation
- **Analytics & dashboards**: Metabase embedded in React
- **Multi-actor model**: Separate role-specific React panels
- **Payments & ledgers**: Custom Spring Boot module with SQL ledger logic
- **Deliveries & tracking**: Delivery entity + Helper panel

**Estimated effort** (Java Spring Boot + React stack):
- **6–9 calendar weeks** with 2 full-time developers
- Buffer: +15–20% for client feedback & refinements

---

## 8. OPEN-SOURCE INTEGRATION STRATEGY

**What to reuse**:
- Directus for rapid CRUD APIs (optional)
- Metabase for open-source analytics (free, self-hosted)
- Spring Boot starters for auth, data, scheduling
- Apache POI for Excel export
- iText for PDF generation

**What to build custom**:
- Vendor assignment logic
- Ledger & outstanding balance calculations
- Invoice generation with business rules
- Helper delivery workflows
- Monthly report pre-aggregation logic
- React role-specific UIs

**License compliance**:
- Check Directus & Metabase licenses (both OSS-friendly)
- Ensure client's deployment model aligns with license terms

---

*Last updated: Jan 1, 2026*
*Architecture designed for Spring Boot + React + PostgreSQL stack*
