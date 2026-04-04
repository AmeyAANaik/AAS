# AAS Project Context

## What This Project Is

AAS is a three-layer application:

1. `ui/`: Angular frontend
2. `mw/`: Spring Boot middleware API
3. `erpmodule/`: ERPNext/Frappe ERP core

The frontend never talks to ERPNext directly. All client traffic goes through the middleware.

Flow:

`Angular UI -> Spring Boot middleware -> ERPNext/Frappe -> MariaDB/Redis`

## Current Architecture

### Frontend

- Stack: Angular 17, Angular Material, RxJS, Reactive Forms
- Main app location: `ui/src/app`
- Major modules present:
  - `auth`
  - `dashboard`
  - `vendors`
  - `branches`
  - `categories`
  - `items`
  - `stock`
  - `orders`
  - `bills`
  - `shell`
- Routing entry: `ui/src/app/app.routes.ts`
- Auth token storage: browser `localStorage` under `aas_auth_token`

### Middleware

- Stack: Spring Boot
- Main package: `mw/src/main/java/com/aas/mw`
- Responsibilities:
  - authenticate users against ERPNext
  - issue JWTs for the UI
  - map ERP roles to app roles
  - expose app-facing REST APIs
  - call ERPNext REST APIs through Feign clients
  - normalize ERPNext data for the UI

Key middleware areas:

- Controllers: `mw/src/main/java/com/aas/mw/controller`
- Services: `mw/src/main/java/com/aas/mw/service`
- ERP client: `mw/src/main/java/com/aas/mw/client`
- Security: `mw/src/main/java/com/aas/mw/config`

### ERP Core

- Stack: ERPNext/Frappe v15, MariaDB, Redis, workers, scheduler, Nginx/WebSocket
- Location: `erpmodule/`
- ERPNext is the system of record
- Multi-company setup is expected on a single ERPNext site

## Auth Model

Login flow:

1. UI calls `POST /api/auth/login`
2. Middleware logs into ERPNext using ERP credentials
3. Middleware receives and stores the ERP session cookie
4. Middleware maps ERP roles to application roles
5. Middleware returns a JWT to the UI
6. UI sends the JWT on later requests
7. Middleware reuses the stored ERP session when calling ERPNext

Observed app roles:

- `ADMIN`
- `VENDOR`
- `SHOP`
- `HELPER`

Important caveat:

- ERP session storage is in-memory, so middleware restarts log users out and horizontal scaling is not session-safe without shared storage.

## Main API Surface

Base path: `/api`

Common endpoints:

- `POST /api/auth/login`
- `GET /api/me`
- `GET/POST /api/items`
- `GET/POST /api/vendors`
- `GET/POST /api/categories`
- `GET/POST /api/shops`
- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `PUT /api/orders/{id}`
- `POST /api/orders/{id}/status`
- `POST /api/orders/{id}/assign-vendor`
- `GET /api/orders/export`
- `POST /api/invoices`
- `GET /api/invoices`
- `GET /api/invoices/export`
- `GET /api/invoices/{id}/pdf`
- `POST /api/payments`
- `GET /api/reports/*`
- `POST /api/setup/ensure`
- OCR-related endpoints exist under `/api/ocr/*`

## ERPNext Mapping Used By Middleware

Observed mappings:

- Orders -> `Sales Order`
- Order line items -> `Sales Order Item`
- Invoices -> `Sales Invoice`
- Payments -> `Payment Entry`
- Vendors -> `Supplier`
- Branches/Shops -> `Customer`
- Categories -> `Item Group`
- Items -> `Item`
- Users -> `User`
- Custom fields -> `Custom Field`

## Features Present In Repo

### Frontend

- Login/auth guard
- Dashboard route and shell layout
- Vendors, branches, categories, items
- Orders, stock, bills
- Reports placeholder

### Middleware

- Auth and profile endpoints
- Master data APIs
- Order APIs
- Vendor assignment APIs
- Invoice and payment APIs
- Reports APIs
- Setup/provisioning API
- OCR and vendor PDF parsing support
- Order billing and state machine related services

### ERP Support

- Dockerized ERPNext stack
- Site intended for multi-company use

## Repo Layout

Top-level directories that matter:

- `ui/`: Angular app
- `mw/`: Spring Boot backend
- `erpmodule/`: ERPNext Docker stack
- `docs/`: architecture notes, task docs, agent prompts
- `scripts/`: helper scripts for seeding and test flows
- `images/`: sample assets used by OCR/order flows

## Run/Dev Basics

Typical local ports:

- ERPNext: `http://localhost:8080`
- Middleware: `http://localhost:8083`
- Angular UI: `http://localhost:4200`

Useful startup references:

- ERPNext compose: `erpmodule/pwd.yml`
- Middleware compose: `docker-compose.mw.yml`
- UI proxy config: `ui/proxy.conf.json`
- Swagger UI: `http://localhost:8083/swagger-ui.html`

## Testing

### UI

- Unit tests: Jasmine/Karma
- E2E tests: Playwright config at `ui/playwright.config.ts`

### Middleware

- Unit/integration tests under `mw/src/test/java`

### Scripts

Repo-level helper scripts exist for:

- mock data seeding
- OCR flow testing
- UI-related automation

## Known Documentation Reality

The repository docs are mixed quality.

Most reliable current-state doc:

- `docs/system-architecture-analysis.md`

Useful but partially stale:

- `STARTUP_COMMANDS.md`
- `SYSTEM_DESIGN.md`
- `erpmodule/README.md`

Mostly planning/agent docs rather than current-state docs:

- `SCM_UI_REFACTOR_GUIDE.md`
- `docs/codex-scm-ui-refactor.md`
- `docs/tasks/*`
- `docs/codex-order-flow-prompt.md`

The Angular README in `ui/README.md` is still scaffold text and should not be treated as a source of truth.

## Current Risks / Caveats

- JWT is stored in `localStorage`, so there is XSS exposure risk.
- ERP session storage is in-memory only.
- ERPNext availability is a hard dependency for most app behavior.
- Reporting may be expensive because middleware aggregates ERP data.
- Some repo docs describe target behavior rather than verified behavior.

## What An Assistant Should Assume

- This is not a greenfield app. Changes should be incremental.
- Reuse existing APIs and services where possible.
- Treat ERPNext as the source of truth for business data.
- Treat middleware as the only integration layer for the UI.
- Check code before trusting older docs.
- Prefer `docs/system-architecture-analysis.md` over older design notes when they conflict.

## Good Starting Files

Architecture and routing:

- `docs/system-architecture-analysis.md`
- `ui/src/app/app.routes.ts`

Frontend auth:

- `ui/src/app/shared/auth-token.service.ts`
- `ui/src/app/auth/auth.guard.ts`

Backend auth and security:

- `mw/src/main/java/com/aas/mw/controller/AuthController.java`
- `mw/src/main/java/com/aas/mw/service/AuthenticationService.java`
- `mw/src/main/java/com/aas/mw/config/SecurityConfig.java`
- `mw/src/main/java/com/aas/mw/service/ErpSessionStore.java`

Core backend APIs:

- `mw/src/main/java/com/aas/mw/controller/OrdersController.java`
- `mw/src/main/java/com/aas/mw/controller/MasterDataController.java`
- `mw/src/main/java/com/aas/mw/controller/InvoiceController.java`
- `mw/src/main/java/com/aas/mw/controller/PaymentsController.java`
- `mw/src/main/java/com/aas/mw/controller/ReportsController.java`
- `mw/src/main/java/com/aas/mw/controller/SetupController.java`
- `mw/src/main/java/com/aas/mw/controller/OcrController.java`

Backend services:

- `mw/src/main/java/com/aas/mw/service/OrderService.java`
- `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
- `mw/src/main/java/com/aas/mw/service/VendorAssignmentService.java`
- `mw/src/main/java/com/aas/mw/service/InvoiceService.java`
- `mw/src/main/java/com/aas/mw/service/PaymentService.java`
- `mw/src/main/java/com/aas/mw/service/ReportService.java`
- `mw/src/main/java/com/aas/mw/service/OcrService.java`

ERP integration:

- `mw/src/main/java/com/aas/mw/client/ErpNextClient.java`
- `mw/src/main/java/com/aas/mw/client/ErpNextFeignClient.java`

## Short Handoff Version

AAS is an Angular + Spring Boot + ERPNext system. The Angular UI in `ui/` talks only to the Spring Boot middleware in `mw/`, which handles auth, role mapping, business APIs, OCR-related flows, and ERPNext integration. ERPNext in `erpmodule/` is the system of record. Authentication is ERP-backed: the middleware logs into ERPNext, stores the ERP session in memory, and issues a JWT to the UI. Major domains include vendors, branches, categories, items, stock, orders, bills/invoices, payments, and reports. The best current architecture reference is `docs/system-architecture-analysis.md`; some other docs are stale and should be validated against code before use.
