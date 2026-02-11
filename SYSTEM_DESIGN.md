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
- **ERPNext Proxy**: Use ERPNext REST resources and methods via Feign client

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
- `GET /api/orders/export`
- `POST /api/invoices`, `GET /api/invoices`, `GET /api/invoices/export`, `GET /api/invoices/{id}/pdf`
- `POST /api/payments`
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

---

## 5. LOCAL DEVELOPMENT (CURRENT)

- ERPNext: `erpmodule/pwd.yml` via Docker Compose
- Middleware: Spring Boot on `http://localhost:8083`
- UI: Angular dev server on `http://localhost:4200` with proxy to middleware

---

## 6. OUTDATED / NOT IN CODEBASE

The following items appear in older architecture docs but are **not implemented**:
- Directus (Generic CRUD middleware)
- React frontend (current frontend is Angular)
- Metabase/Power BI dashboards (reports are currently middleware endpoints)
- Postgres/MySQL persistence outside ERPNext (current persistence is ERPNext + MariaDB)

---

## 7. FEASIBILITY NOTE

The current codebase implements a **middleware + ERPNext** architecture where ERPNext is the system of record and the middleware provides simplified APIs for the Angular UI. The prior React/Directus/Metabase model is not present in the repository.
