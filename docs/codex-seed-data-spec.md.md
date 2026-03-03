# AAS – Codex Seed Data Tasks (Mature, Non‑Dummy)

Goal: Create realistic, production‑like seed data for AAS so reports, flows, and OCR tests feel like a real Indian hotel/restaurant operation. Avoid lorem ipsum or "Test 1/2/3".

AAS architecture (for reference):
- Angular UI → Spring Boot middleware (MW) → ERPNext/Frappe → MariaDB/Redis
- UI never calls ERPNext directly.
- ERPNext is the system of record.
- Existing mappings:
  - Orders → Sales Order
  - Order items → Sales Order Item
  - Invoices → Sales Invoice
  - Payments → Payment Entry
  - Vendors → Supplier
  - Branches/Shops → Customer
  - Categories → Item Group
  - Items → Item

Global constraints:
- Use realistic Indian hospitality procurement context (Pune, Mumbai, Lonavala, etc.).
- Data must be internally consistent across all entities.
- Respect the multi‑company, single‑site ERPNext model as described in STARTUP_COMMANDS.md.
- Do NOT break existing APIs or flows; only add data using existing models.

---

## Task 1 – Companies, Warehouses, Cost Centers

Create in ERPNext:

1. Companies (at least 3):
   - "Hotel Sahyadri Pune Pvt Ltd" (abbr HSP)
   - "Blue Lagoon Restaurant Mumbai LLP" (abbr BLR)
   - "Fortune Hills Resort Lonavala Pvt Ltd" (abbr FHR)

2. For each company:
   - Warehouses: "Main Store", "Kitchen Store", "Bar Store" (where applicable).
   - Cost Centers: "F&B Operations", "Banquets", "Room Service".

3. Ensure:
   - Fiscal year aligned with 2026 defaults.
   - Currency set to INR for these companies if base site is US‑oriented.

---

## Task 2 – Vendors (Suppliers)

Create 8–10 realistic suppliers, e.g.:

- FreshHarvest Agro Foods (vegetables, fruits, herbs)
- SpiceRoute Traders (spices, dry goods)
- PrimeDairy Distributors (milk, cheese, butter)
- OceanCatch Seafood Supplies (fish, prawns)
- CrystalClean Housekeeping Supplies (cleaners, chemicals)
- LinenLux Textiles (bedsheets, pillow covers)
- BrewMasters Beverages Pvt Ltd (soft drinks, packaged juices)
- Regal Spirits Distributors (alcoholic beverages)

For each Supplier:
- Add GSTIN‑style tax ID (e.g., 27ABCDE1234F1Z5).
- Realistic Indian phone numbers and emails.
- Payment terms (7/15/30 days), credit limits.
- Map at least one supplier to the default vendor user:
  - `vendor@example.com` / `VendorAAS!2026` → Supplier "Vendor A" must continue to exist.

---

## Task 3 – Shops / Branches (Customers)

Create Customers representing hotel outlets and external B2B customers, e.g.:

- Sahyadri Rooftop Bar (HSP)
- Sahyadri All‑Day Dining (HSP)
- Blue Lagoon Takeaway – Bandra (BLR)
- Fortune Hills Poolside Bar (FHR)
- Fortune Hills Banquet Hall (FHR)
- External: "Cafe Sunrise – Koregaon Park", "Bistro Central – Andheri"

Requirements:
- Keep existing "Shop A" mapped to `shop@example.com` / `ShopAAS!2026`.
- Add realistic billing & shipping addresses (Indian addresses, PIN codes).
- Mark GST registration where realistic.

---

## Task 4 – Item Groups (Categories)

Define a hierarchical Item Group tree with at least 15–20 leaf groups, for example:

- Food
  - Vegetables – Fresh
  - Fruits – Fresh
  - Meat & Poultry
  - Seafood
  - Dairy & Eggs
  - Dry Goods & Staples
- Beverages
  - Soft Drinks
  - Packaged Juices
  - Bottled Water
  - Beer
  - Spirits
- Housekeeping
  - Linen
  - Cleaning Chemicals
  - Guest Amenities
- Miscellaneous
  - Kitchen Equipment (Smallware)
  - Packaging Material

Every Item created in Task 5 must belong to one of these groups.

---

## Task 5 – Items with Vendor Rates and Margins

Create 80–120 Items representing real hotel consumption. Examples:

- "Potato – 50 kg sack"
- "Onion – 50 kg sack"
- "Broiler Chicken – 1 kg"
- "Basmati Rice 1121 – 25 kg bag"
- "Sunflower Oil – 15 L tin"
- "Amul Butter – 500 g pack"
- "Full Cream Milk – 1 L tetra pack"
- "Kingfisher Premium Beer – 650 ml"
- "Coca‑Cola PET – 1.25 L"
- "Cotton Bed Sheet – 300TC – King"
- "Microfiber Pillow – Standard"
- "Glass Cleaner – 5 L can"

For each Item:
- Set `aas_vendor_rate` to realistic wholesale INR rates.
- Set `aas_margin_percent` to realistic margins (8–40%).
- Set UOM, HSN/SAC where applicable, tax template if used.
- Link a primary Supplier where relevant so vendor reports are meaningful.

---

## Task 6 – Historical Orders (Sales Orders)

Generate 3–6 months of historical Orders per company:

- Use multiple Customers (shops/branches) from Task 3.
- Use Items from Task 5 and Suppliers from Task 2.
- Quantity patterns:
  - Daily vegetable orders (small but frequent).
  - Weekly meat/seafood.
  - Monthly linens and housekeeping bulk orders.
- Status mix:
  - Draft, Submitted, Delivered, Partially Delivered.
- Include:
  - Weekend spikes for resorts.
  - Event spikes for banquets/weddings (larger quantities, specific dates).

Ensure:
- Dates and company/shop links are consistent.
- Total values line up with item counts and rates.

---

## Task 7 – Invoices (Sales Invoices) and Payments (Payment Entries)

For a large subset of Orders:

- Create corresponding Sales Invoices:
  - Invoice dates close to delivery dates.
  - Apply GST slabs typical for F&B (5%, 12%, 18% as appropriate).
  - Use realistic rounding and occasional discounts.

- Create Payment Entries:
  - Some invoices fully paid on time.
  - Some paid late (simulate 7–30 days overdue).
  - A few with partial payments.

Goal:
- Make vendor and shop reports show real‑looking billing, collections, and aging.
- Ensure reconciliation between Orders, Invoices, and Payments is valid.

---

## Task 8 – OCR Test Artifacts

Prepare a small realistic set of documents for OCR flow:

1. Vendor PDFs:
   - Layout similar to typical Indian vendor invoices:
     - Header with vendor details, GSTIN, invoice number/date.
     - Line items with item descriptions that map to Items created in Task 5.
     - Quantities, rates, taxes, totals.

2. Branch images:
   - Simulated WhatsApp‑style photos of printed/handwritten order lists for branches.

Constraints:
- Filenames/paths must match expectations in `scripts/test-ocr-flow.sh`.
- Environment variables like `CUSTOMER`, `COMPANY`, `VENDOR`, `BRANCH_IMAGE`, `VENDOR_PDF` should map to actual customers, companies, vendors, and file paths created here.

---

## Task 9 – Users and Roles

Users to preserve:

- Vendor User:
  - Email: `vendor@example.com`
  - Password: `VendorAAS!2026`
  - Supplier: `Vendor A`

- Shop User:
  - Email: `shop@example.com`
  - Password: `ShopAAS!2026`
  - Customer: `Shop A`

- Helper User:
  - Email: `helper@example.com`
  - Password: `HelperAAS!2026`

Additional users:

- Create 3–5 extra users per role (ADMIN, VENDOR, SHOP, HELPER) with Indian names and emails.
- Map vendor users to specific Suppliers and shop users to specific Customers.
- Ensure roles and permissions remain compatible with existing MW and UI behavior.

---

## Acceptance Criteria

- All `/api/reports/*/export` endpoints produce non‑trivial CSVs with realistic data and trends.
- OCR test script (`scripts/test-ocr-flow.sh`) completes successfully with these entities.
- UI flows for Admin, Vendor, Shop, and Helper users show mature, consistent data: orders, invoices, payments, and stock.
- No changes to public API contracts; only added master and transactional data.