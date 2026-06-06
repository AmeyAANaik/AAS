# AAS Platform - Startup Commands

```
AAS/
├── erpmodule/      # ERPNext Docker stack (ERP Core)
├── mw/             # AAS middleware API (Spring Boot)
├── ui/             # Angular frontend
└── STARTUP_COMMANDS.md
```

## System Commands (UI + MW + ERP)

Preferred local workflow:

- UI: run with `npm` from `ui/`
- MW: run with Docker Compose
- ERPNext: run with Docker Compose

## Fresh Setup Quick Start

Use this when you want a clean local setup from scratch:

```bash
cd /Users/roshninaik/Projects/AAS
cp .env.mw.example .env.mw
# Fill in APP_INVOICE_TEMPLATE_GENERATOR_API_KEY in .env.mw before starting MW

cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml down -v
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d backend websocket queue-short queue-long scheduler frontend

cd /Users/roshninaik/Projects/AAS
docker compose -f docker-compose.mw.yml up -d --build

TOKEN=$(curl -sS http://localhost:8083/api/auth/login \
  -H 'content-type: application/json' \
  --data '{"username":"Administrator","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -X POST http://localhost:8083/api/setup/ensure \
  -H "Authorization: Bearer $TOKEN"

cd /Users/roshninaik/Projects/AAS/ui
npm start -- --host 0.0.0.0 --port 4200
```

URLs after startup:

- ERPNext: `http://localhost:8080`
- MW Swagger: `http://localhost:8083/swagger-ui.html`
- UI: `http://localhost:4200`

### MW generator config

Middleware reads invoice-mapping generator settings from `.env.mw`.

```bash
cd /Users/roshninaik/Projects/AAS
cp .env.mw.example .env.mw
```

Then set at least:

- `APP_INVOICE_TEMPLATE_GENERATOR_BASE_URL`
- `APP_INVOICE_TEMPLATE_GENERATOR_MODEL`
- `APP_INVOICE_TEMPLATE_GENERATOR_API_KEY`

After updating `.env.mw`, recreate MW:

```bash
cd /Users/roshninaik/Projects/AAS
docker compose -f docker-compose.mw.yml up -d --build mw
```

### Stop all services

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml down
cd /Users/roshninaik/Projects/AAS && docker compose -f docker-compose.mw.yml down
pkill -f "ng serve" || true
```

### Clean all data

This wipes all ERPNext data by removing the ERP Docker volumes, then stops MW and UI.

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml down -v
cd /Users/roshninaik/Projects/AAS && docker compose -f docker-compose.mw.yml down
pkill -f "ng serve" || true
```

After a wipe, start the stack again and rerun setup/seed commands.

### Start all services

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml run --rm create-site
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml up -d backend websocket queue-short queue-long scheduler frontend
cd /Users/roshninaik/Projects/AAS && docker compose -f docker-compose.mw.yml up -d --build
cd /Users/roshninaik/Projects/AAS/ui && npm start -- --host 0.0.0.0 --port 4200
```

### Verify services

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule && docker compose -f pwd.yml ps
cd /Users/roshninaik/Projects/AAS && docker compose -f docker-compose.mw.yml ps
curl -I http://localhost:8080
curl -I http://localhost:8083/swagger-ui.html
curl -I http://localhost:4200
```

### Vendor invoice templates (optional)

Vendors can have a per-vendor invoice parsing template stored in ERPNext `Supplier` custom fields. MW uses this template during `POST /api/orders/{id}/vendor-pdf` when scanning vendor PDFs.

1) Ensure custom fields exist:
```bash
curl -X POST http://localhost:8083/api/setup/ensure -H "Authorization: Bearer <ADMIN_JWT>"
```

2) Upload a sample invoice PDF for a vendor (ADMIN only). MW will OCR the sample and auto-detect the best built-in template key:
```bash
curl -X POST "http://localhost:8083/api/vendors/<SUPPLIER_ID>/invoice-template/sample" \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -F "file=@images/vendor_invoice_freshharvest.pdf"
```

3) Clear a vendor template (ADMIN only):
```bash
curl -X DELETE "http://localhost:8083/api/vendors/<SUPPLIER_ID>/invoice-template" \
  -H "Authorization: Bearer <ADMIN_JWT>"
```

### Generate required setup/default data (MW -> ERP)

Use this on a fresh ERPNext site, after wiping ERP data, or after MW adds new required custom fields.

Why the login token is needed:
- `/api/setup/ensure` is an authenticated admin endpoint
- The first command logs in to MW and extracts an admin JWT
- The second command uses that JWT to run setup safely
- This avoids manually copying a bearer token from Swagger or the browser

```bash
TOKEN=$(curl -sS http://localhost:8083/api/auth/login \
  -H 'content-type: application/json' \
  --data '{"username":"Administrator","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -X POST http://localhost:8083/api/setup/ensure \
  -H "Authorization: Bearer $TOKEN"
```

What this provisions:
- ERPNext custom fields used by vendors, shops, items, sales orders, and invoice parsing
- Default master data required by MW flows
- Optional default users configured in `mw/src/main/resources/application.properties`

Why this matters:
- Login can work before setup is applied
- But vendor, item, shop, and order flows can fail until these custom fields exist
- This command is required before first end-to-end testing on a fresh environment

How to read the response:
- Keys ending in `Created` or `Ensured` report whether setup had to create something during this run
- `false` usually means that resource or custom field already existed
- A response with mostly `false` values is normal when setup has already been applied earlier
- Example: `statusFieldCreated:false` means the field was already present, not that setup failed

### Generate mock master data (Admin only)

```bash
cd /Users/roshninaik/Projects/AAS
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:mock
```

Rerunning `npm run seed:mock` is safe and now backfills missing seeded transactions after model or ops changes instead of skipping the full stage when partial seed data already exists.

### Seed Sales_3231 items (59 items, margin 10%)

```bash
cd /Users/roshninaik/Projects/AAS
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:items
```

See `scripts/seed/README.md` for tab-wise seed scripts.

### Run OCR order flow test

```bash
cd /Users/roshninaik/Projects/AAS
BASE_URL=http://localhost:8083 \
USERNAME=Administrator \
PASSWORD=admin \
COMPANY=AAS \
bash ./scripts/test-ocr-flow.sh
```

### Run UI end-to-end tests

```bash
cd /Users/roshninaik/Projects/AAS
npx playwright test -c ui/playwright.config.ts --reporter=line
```

## ERPNext Module (ERP Core)

Use Docker Compose for ERPNext changes and local runtime.

### ERP module working directory

If you are already inside `erpmodule/`, use these commands directly:

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
pwd
ls
```

Key files:

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
ls -1
cat .env
sed -n '1,240p' pwd.yml
```

### Start ERPNext (normal)

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d backend websocket queue-short queue-long scheduler frontend
```

### Start ERPNext with rebuild

Use this after ERP Dockerfile, ERP apps, or compose changes.

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml up -d --build db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d --build backend websocket queue-short queue-long scheduler frontend
```

### Start only the ERP frontend first

Useful when you only want to bring the full stack up in detached mode and then inspect service health.

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator create-site websocket queue-short queue-long scheduler backend frontend
```

### Re-run ERP site bootstrap/custom-field setup

This is useful if the ERP site exists but you want to re-apply the `create-site` step logic.
Before running it, make sure `db`, `redis-cache`, `redis-queue`, and `configurator` are already up.

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
```

### Configure ERPNext Setup Defaults (optional)

Defaults are read from `mw/src/main/resources/application.properties` and applied automatically on a fresh site.

```properties
erp.setup.full-name=Administrator
erp.setup.email=admin@example.com
erp.setup.password=admin
erp.setup.company-name=AAS Core
erp.setup.company-abbr=AAS
erp.setup.country=India
erp.setup.currency=INR
erp.setup.timezone=America/New_York
erp.setup.fy-start-date=2026-01-01
erp.setup.fy-end-date=2026-12-31
erp.setup.chart-of-accounts=Standard
```

To apply these defaults, you must recreate the ERPNext volumes (data wipe):

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml down -v
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d backend websocket queue-short queue-long scheduler frontend
```

### Stop ERPNext

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml down
```

### Restart ERPNext

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml restart
```

### Wipe ERPNext and recreate from scratch

This removes ERP volumes and recreates the site with the defaults from `mw/src/main/resources/application.properties`.

Before wiping, back up Item master data + Item Groups (includes margins):

```bash
cd /Users/roshninaik/Projects/AAS
ERP_USERNAME=Administrator ERP_PASSWORD=admin node scripts/seed/backup-items-and-groups.mjs
```

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml down -v
docker compose -f pwd.yml up -d --build db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d --build backend websocket queue-short queue-long scheduler frontend
```

### Fresh ERP reset checklist

Use this exact order after a full wipe:

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml down -v
docker compose -f pwd.yml up -d db redis-cache redis-queue configurator
docker compose -f pwd.yml run --rm create-site
docker compose -f pwd.yml up -d backend websocket queue-short queue-long scheduler frontend
docker compose -f pwd.yml ps
curl -I http://localhost:8080
```

### ERP bootstrap troubleshooting

If `create-site` fails, check these first:

- `wait-for-it: timeout ... db:3306` or Redis timeouts:
  Start `db`, `redis-cache`, `redis-queue`, and `configurator` before `create-site`.
- `sites/common_site_config.json` not found:
  `configurator` has not finished writing the shared site config yet.
- `Site aas.core.local already exists` with weird partial setup errors:
  On a broken fresh install, use `docker compose -f pwd.yml down -v` and recreate from zero.
- `safe_exec`, `__import__ not found`, or inline script errors:
  These are fixed in the current repo; rerun with the latest `pwd.yml`.

### Access ERPNext UI

- **URL**: http://localhost:8080
- **Default User**: `Administrator`
- **Default Password**: `admin`

### Check Status

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml ps
```

### View Logs

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml logs -f
```

### View logs for a specific ERP service

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml logs -f backend
docker compose -f pwd.yml logs -f frontend
docker compose -f pwd.yml logs -f create-site
```

### Verify ERPNext health quickly

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml ps
curl -I http://localhost:8080
```

### Inspect ERP Docker services

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml config --services
docker compose -f pwd.yml top
docker compose -f pwd.yml images
```

### Open a shell inside ERP containers

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml exec backend bash
docker compose -f pwd.yml exec frontend bash
```

### Run bench commands inside ERP backend

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml exec backend bench --site aas.core.local list-apps
docker compose -f pwd.yml exec backend bench --site aas.core.local migrate
docker compose -f pwd.yml exec backend bench --site aas.core.local clear-cache
```

### Common ERP admin commands from `erpmodule/`

```bash
cd /Users/roshninaik/Projects/AAS/erpmodule
docker compose -f pwd.yml exec backend bench --site aas.core.local console
docker compose -f pwd.yml exec backend bench --site aas.core.local execute frappe.utils.print_format.download_pdf --kwargs "{'doctype':'Sales Invoice','name':'ACC-SINV-2026-00001'}"
docker compose -f pwd.yml exec backend bench --site aas.core.local doctor
docker compose -f pwd.yml exec backend bench --site aas.core.local enable-scheduler
docker compose -f pwd.yml exec backend bench --site aas.core.local set-config developer_mode 1
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

## Middleware (MW)

Use Docker Compose for MW changes and local runtime.

### Start MW via Docker

```bash
docker compose -f docker-compose.mw.yml up --build
```

**Note:** The Docker compose uses `host.docker.internal:8080` for ERPNext. If ERPNext runs elsewhere, update `ERPNEXT_BASE_URL`.

### OCR (Vendor PDF Parsing)

OCR uses **Tesseract** inside the MW container.

If you run MW via Docker, the Dockerfile installs Tesseract and the compose file sets:

```bash
OCR_TESSERACT_DATAPATH=/usr/share/tesseract-ocr/5/tessdata
```

If you temporarily choose to run MW outside Docker, install Tesseract on the host and set `OCR_TESSERACT_DATAPATH` to the `tessdata` folder.

Health check:

```bash
curl -H "Authorization: Bearer <ADMIN_JWT>" http://localhost:8083/api/ocr/health
```

## UI

Use `npm` for UI development:

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

1. **Start ERPNext with Docker** (always first)
2. Configure companies and master data in ERPNext UI
3. **Start Middleware (MW) with Docker** to expose APIs
4. **Start UI with npm** for custom UX
5. **Seed mock master data** (vendors/branches/items)

```bash
npm run seed:mock
```

## Full Docker Compose Flow (ERPNext + MW)

```bash
cd erpmodule
docker compose -f pwd.yml up -d

cd /Users/roshninaik/Projects/AAS
docker compose -f docker-compose.mw.yml up --build
```

## Verification (End-to-End)

1) **ERPNext UI**
- URL: http://localhost:8080
- Login: `Administrator` / `admin`

2) **MW health (Swagger UI)**
- URL: http://localhost:8083/swagger-ui.html
- Login via `POST /api/auth/login`

3) **Default users created**
After first login (admin), MW calls `/api/setup/ensure` and creates:
- `vendor@example.com` / `VendorAAS!2026` (Supplier `FreshHarvest Agro Foods`)
- `shop@example.com` / `ShopAAS!2026` (Customer `Sukarta Aundh`)
- `helper@example.com` / `tapan@123` (helper user)

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

## OCR End-to-End Test Script

Runs the full flow:
branch image upload → vendor assign → vendor PDF OCR → PO/SO/SI create.

```bash
BASE_URL=http://localhost:8083 \
USERNAME=Administrator \
PASSWORD=admin \
./scripts/test-ocr-flow.sh
```

Optional env vars:
`CUSTOMER`, `COMPANY`, `VENDOR`, `BRANCH_IMAGE`, `VENDOR_PDF`, `TRANSACTION_DATE`, `DELIVERY_DATE`.

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
- Supplier: `FreshHarvest Agro Foods`

Shop User
- Email: `shop@example.com`
- Password: `ShopAAS!2026`
- Customer: `Sukarta Aundh`

Tapan User
- Login: `helper@example.com`
- Email: `helper@example.com`
- Password: `tapan@123`

You can override any defaults with env vars in `mw/src/main/resources/application.properties` (keys start with `APP_DEFAULTS_`).

## Notes

- ERPNext runs on port **8080**
- Reserve port 8081+ for middleware/ui services
- All data persists in Docker volumes (survives container restart)
- For fresh install, run `docker compose -f pwd.yml down -v` to wipe volumes
