# AAS Platform - Startup Commands

#tree -L 2 -a


```
AAS/
├── erpmodule/      # ERPNext Docker stack (ERP Core)
├── middleware/     # AAS backend API (to be implemented)
├── ui/             # Custom frontend (to be implemented)
└── STARTUP_COMMANDS.md
```

## ERPNext Module (ERP Core)

### Start ERPNext

```bash
cd erpmodule
docker compose -f pwd.yml up -d
```

### Configure ERPNext Setup Defaults (optional)

Defaults are read from `mw/src/main/resources/application.properties` and applied automatically on a fresh site.

```properties
erp.setup.full-name=Administrator
erp.setup.email=admin@example.com
erp.setup.password=admin
erp.setup.company-name=AAS Core
erp.setup.company-abbr=AAS
erp.setup.country=United States
erp.setup.currency=USD
erp.setup.timezone=America/New_York
erp.setup.fy-start-date=2026-01-01
erp.setup.fy-end-date=2026-12-31
erp.setup.chart-of-accounts=Standard
```

To apply these defaults, you must recreate the ERPNext volumes (data wipe):

```bash
cd erpmodule
docker compose -f pwd.yml down -v
docker compose -f pwd.yml up -d
```

### Stop ERPNext

```bash
cd erpmodule
docker compose -f pwd.yml down
```

### Access ERPNext UI

- **URL**: http://locayelhost:8080
- **Default User**: `Administrator`
- **Default Password**: `admin`

### Check Status

```bash
cd erpmodule
docker compose -f pwd.yml ps
```

### View Logs

```bash
cd erpmodule
docker compose -f pwd.yml logs -f
```

## Multi-Company Setup

ERPNext is configured as a **single multi-company site**:

- **Site Name**: `aas.core.local`
- **Pattern**: One Company per hotel/restaurant
- **Benefits**: 
  - Consolidated reporting across all hotels
  - Shared item master, supplier master
  - Cross-company stock transfers
  - Single deployment to manage

### Adding a New Hotel/Restaurant Company

1. Login to ERPNext (http://localhost:8080)
2. Navigate to: **Setup > Company**
3. Click **New** and fill in:
   - Company Name (e.g., "Hotel Taj Pvt Ltd")
   - Abbr (e.g., "HTAJ")
   - Default Currency: INR
4. Setup Warehouses and Cost Centers for the company
5. Create users with company-specific permissions

## Middleware (To Be Implemented)

## Middleware (MW)

### Start MW locally

```bash
cd mw
./mvnw spring-boot:run
```

### Start MW via Docker

```bash
docker compose -f docker-compose.mw.yml up --build
```

**Note:** The Docker compose uses `host.docker.internal:8080` for ERPNext. If ERPNext runs elsewhere, update `ERPNEXT_BASE_URL`.

## UI

Custom frontend:

```bash
cd ui
npm install
npm start
```

## API Documentation (Swagger UI)

- **Swagger UI**: http://localhost:8083/swagger-ui.html
- **Bearer token (JWT)**:
  - `POST /api/auth/login` with ERPNext credentials (e.g., `Administrator` / `admin`)
  - Use `accessToken` from the response in Swagger Authorize (sends `Authorization: Bearer <token>`)

## Development Workflow

1. **Start ERPNext** (always first)
2. Configure companies and master data in ERPNext UI
3. **Start Middleware (MW)** to expose APIs
4. **Start UI** for custom UX
5. **Seed mock master data** (vendors/branches/items)

```bash
npm run seed:mock
```

## Full Docker Compose Flow (ERPNext + MW)

```bash
cd erpmodule
docker compose -f pwd.yml up -d

cd /workspaces/AAS
docker compose -f docker-compose.mw.yml up --build
```

## Verification (End-to-End)

1) **ERPNext UI**
- URL: http://localhoist:8080
- Login: `Administrator` / `admin`

2) **MW health (Swagger UI)**
- URL: http://localhost:8083/swagger-ui.html
- Login via `POST /api/auth/login`

3) **Default users created**
After first login (admin), MW calls `/api/setup/ensure` and creates:
- `vendor@example.com` / `vendor123` (Supplier `Vendor A`)
- `shop@example.com` / `shop123` (Customer `Shop A`)
- `helper@example.com` / `helper123`

4) **UI**
- URL: http://localhost:4200
- Login as Admin or default users above

5) **E2E flow (Admin)**
- Seed master data (Shop/Vendor/Category/Item)
- Create order
- Assign vendor + status
- Create invoice + payment
- Load invoices and download PDF

6) **CSV exports**
- Orders: `GET /api/orders/export`
- Vendor reports: `/api/reports/vendor-orders/export`, `/api/reports/vendor-billing/export`, `/api/reports/vendor-payments/export`
- Shop reports: `/api/reports/shop-billing/export`, `/api/reports/shop-payments/export`, `/api/reports/shop-category/export`

## Margin per Item

MW adds custom fields on Item:
- `aas_vendor_rate` (Vendor Rate)
- `aas_margin_percent` (Margin %)

UI uses these to auto-calculate selling rate when you pick an item.

## Seed Mock Data

Run from repo root. Use an ADMIN account.

```bash
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:mock
```

If you see a 403 during seeding, verify the authenticated role is `admin` and that MW is running.

## Default Users (created via `/api/setup/ensure`)

MW creates default users on first login (if enabled) and will not overwrite existing accounts.

Vendor User
- Email: `vendor@example.com`
- Password: `VendorAAS!2026`
- Supplier: `Vendor A`

Shop User
- Email: `shop@example.com`
- Password: `ShopAAS!2026`
- Customer: `Shop A`

Helper User
- Email: `helper@example.com`
- Password: `HelperAAS!2026`

You can override any defaults with env vars in `mw/src/main/resources/application.properties` (keys start with `APP_DEFAULTS_`).

## Notes

- ERPNext runs on port **8080**
- Reserve port 8081+ for middleware/ui services
- All data persists in Docker volumes (survives container restart)
- For fresh install, run `docker compose -f pwd.yml down -v` to wipe volumes
