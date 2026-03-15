# Seed Data How-To

## Purpose

`npm run seed:mock` now seeds realistic Indian hospitality data for AAS across ERPNext and middleware-compatible flows.

The seed includes:

- Companies:
  - `Hotel Sahyadri Pune Pvt Ltd`
  - `Blue Lagoon Restaurant Mumbai LLP`
  - `Fortune Hills Resort Lonavala Pvt Ltd`
- Warehouses and cost centers per company
- Suppliers, customers/shops, item groups, and 80+ items
- Historical sales orders, sales invoices, and payment entries
- Preserved default users:
  - `vendor@example.com` / `VendorAAS!2026`
  - `shop@example.com` / `ShopAAS!2026`
  - `helper@example.com` / `HelperAAS!2026`
- OCR fixtures used by the existing OCR flow scripts

## Apply The Seed

Start ERPNext and middleware first, then run:

```bash
cd /Users/roshninaik/Projects/AAS
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:mock
```

Optional:

```bash
cd /Users/roshninaik/Projects/AAS
SEED_TRANSACTIONS_ALWAYS=1 MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:mock
```

Default behavior is idempotent:

- rerunning `npm run seed:mock` now backfills missing orders, invoices, and payments
- existing seeded records are reused instead of causing whole transaction stages to be skipped
- this is the preferred mode after mock structure or ops screens change

Use `SEED_TRANSACTIONS_ALWAYS=1` only when you intentionally want to bypass payment-entry reuse checks.

## OCR Fixture Defaults

The seeded OCR scripts now target:

- Company: `Hotel Sahyadri Pune Pvt Ltd`
- Customer: `Sahyadri All-Day Dining`
- Vendor: `FreshHarvest Agro Foods`
- Branch image: `images/branch_order_sahyadri.svg`
- Vendor PDF: `images/vendor_invoice_freshharvest.pdf`

Run the end-to-end OCR flow:

```bash
cd /Users/roshninaik/Projects/AAS
BASE_URL=http://localhost:8083 \
USERNAME=Administrator \
PASSWORD=admin \
bash ./scripts/test-ocr-flow.sh
```

Direct API create-order smoke test now uses `aas_category` instead of `aas_vendor`:

```bash
cd /Users/roshninaik/Projects/AAS
BASE_URL=http://localhost:8083 \
TOKEN=<jwt> \
bash ./scripts/test-create-order.sh
```

## Verify In UI

Open:

- UI: `http://localhost:4200`
- Swagger: `http://localhost:8083/swagger-ui.html`

Verify that orders, invoices, payments, and reports show non-trivial data for:

- Pune hotel outlets
- Mumbai restaurant outlets
- Lonavala resort outlets
- Suppliers such as `FreshHarvest Agro Foods`, `SpiceRoute Traders`, and `Regal Spirits Distributors`

## Verify Report Exports

These should return populated CSV output after seeding:

```bash
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/vendor-orders/export
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/vendor-billing/export
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/vendor-payments/export
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/shop-billing/export
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/shop-payments/export
curl -H "Authorization: Bearer <JWT>" http://localhost:8083/api/reports/shop-category/export
```

## Notes

- The seed keeps legacy default entities such as `Vendor A` and `Shop A` for compatibility.
- UI and middleware API contracts are unchanged.
- ERPNext remains the system of record; the seed writes data using existing doctypes rather than introducing new core doctypes.
