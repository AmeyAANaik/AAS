# Codex Repo Context

Last updated: 2026-03-12

This file captures the current repository context so future Codex sessions can start from a stable baseline instead of repeating the same codebase discovery work.

## Repository Shape

- `ui/`
  - Angular 17 frontend.
  - Standalone bootstrap with feature modules for orders, stock, bills, vendors, branches, categories, items, dashboard.
- `mw/`
  - Spring Boot 3.4 middleware.
  - JWT auth, ERPNext integration, domain APIs, OCR-backed order flow.
- `erpmodule/`
  - ERPNext/Frappe docker stack and site config.
- `docs/`
  - Architecture notes, Codex prompts, seed instructions, task breakdowns.
- `scripts/`
  - Seed, OCR-flow, and UI helper scripts.

## Runtime Convention

- `ui/` is the local development surface intended to run with `npm`.
- `mw/` is expected to run through Docker Compose for local integration work.
- `erpmodule/` is expected to run through Docker Compose.

## As-Implemented Architecture

The system is a 3-layer stack:

1. Angular UI calls the middleware only.
2. Spring Boot middleware owns auth, access control, ERPNext normalization, and workflow orchestration.
3. ERPNext is the system of record, backed by MariaDB and Redis.

Data flow:

`UI -> /api -> MW -> ERPNext REST -> ERPNext doctypes`

The UI does not call ERPNext directly.

## Main Runtime Entry Points

### UI

- Bootstrap: `ui/src/main.ts`
- App config: `ui/src/app/app.config.ts`
- Top-level routes: `ui/src/app/app.routes.ts`
- API proxy: `ui/proxy.conf.json`

### Middleware

- App entry: `mw/src/main/java/com/aas/mw/MwApplication.java`
- Security rules: `mw/src/main/java/com/aas/mw/config/SecurityConfig.java`
- Auth flow: `mw/src/main/java/com/aas/mw/service/AuthenticationService.java`
- ERP session cache: `mw/src/main/java/com/aas/mw/service/ErpSessionStore.java`
- ERP client: `mw/src/main/java/com/aas/mw/client/ErpNextClient.java`

### Order Flow

- Controller: `mw/src/main/java/com/aas/mw/controller/OrdersController.java`
- Core service: `mw/src/main/java/com/aas/mw/service/OrderService.java`
- Billing step: `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
- OCR/vendor PDF step: `mw/src/main/java/com/aas/mw/service/VendorPdfService.java`

## Important Business Flows

### Auth

1. UI posts credentials to `POST /api/auth/login`.
2. MW logs into ERPNext and extracts the ERP session cookie.
3. MW resolves ERP roles to app roles.
4. MW returns a JWT to the UI.
5. MW stores the ERP session in memory for later ERPNext calls.

### Order Lifecycle

Implemented order path:

`DRAFT -> VENDOR_ASSIGNED -> VENDOR_PDF_RECEIVED -> VENDOR_BILL_CAPTURED -> SELL_ORDER_CREATED`

Main APIs:

- `POST /api/orders`
- `POST /api/orders/branch-image`
- `POST /api/orders/{id}/assign-vendor`
- `POST /api/orders/{id}/vendor-pdf`
- `PUT /api/orders/{id}/items`
- `POST /api/orders/{id}/vendor-bill`
- `GET /api/orders/{id}/sell-preview`
- `POST /api/orders/{id}/sell-order`

## Current Strengths

- Clear separation between UI, middleware, and ERP system of record.
- Access control is explicit in middleware security config.
- Order flow is modeled as a real workflow instead of ad hoc status writes.
- Docs already reflect the current Angular + Spring Boot + ERPNext architecture better than older legacy notes.
- Local startup and seed/OCR scripts give the repo a usable development story.

## Current Risks And Follow-Ups

### Security / Operations

- ERPNext sessions are stored in memory only.
  - Middleware restart clears the ERP session cache and effectively forces re-login.
  - Multi-node deployment would need shared session storage.
- UI JWT is stored in `localStorage`.
  - Convenient, but exposed to XSS risk.
- `mw/src/main/resources/application.properties` contains insecure defaults for local convenience.
  - Fallback JWT secret.
  - Default passwords for seeded users.
  - Verbose Spring Security debug and trace logging.

### Test Coverage

- Backend automated coverage is very light.
- `mw/src/test/java/com/aas/mw/MwApplicationTests.java` is currently only a context-load test.
- UI has more spec files, but most confidence still comes from manual flows and Playwright/OCR scripts.

### Frontend Maintainability

- Auth headers are built manually in many Angular services instead of through a single auth-token interceptor.
- The app already uses an interceptor for auth-expiry handling, so token injection could be centralized later.

### Repo Hygiene

Tracked repository artifacts currently include local/generated material that should usually stay out of source control:

- `node_modules/`
- `.m2/`
- `package-lock.json`
- `erpmodule/.env`

This increases repo noise and can create accidental config leakage.

### Documentation State

- `docs/README.md` is the docs entry point.
- `STARTUP_COMMANDS.md` and `ui/README.md` are aligned with the current repository layout and local commands.
- This file should be treated as the fast-start context, while `system-architecture-analysis.md` remains the deeper architecture reference.

## Recommended Starting Order For Future Sessions

1. Read this file.
2. Read `docs/system-architecture-analysis.md` if architectural depth is needed.
3. Read the task-specific doc in `docs/tasks/` if the work maps to UI core, master data, or transactions.
4. Open the relevant code entry points listed above before proposing changes.

## Suggested Next Maintenance Tasks

1. Add a root `.gitignore` and remove tracked generated or secret-bearing files.
2. Replace repetitive Angular auth-header code with a request interceptor.
3. Add backend tests for auth, order state transitions, and ERP client behavior.
4. Keep docs aligned with runtime behavior as order, billing, and OCR flows evolve.
