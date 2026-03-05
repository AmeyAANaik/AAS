# AAS Docs Changelog

This changelog records user-visible functional changes to the AAS stack (UI + MW + ERPNext integration).

## 2026-03-05

### Vendor Invoice Templates (ERPNext-backed, OCR Auto-Detection)

Vendors can now have an invoice parsing template stored in ERPNext. Middleware uses this template during the vendor PDF OCR step to improve item extraction.

**Where it is stored (ERPNext)**
- DocType: `Supplier`
- Fields (custom fields provisioned by MW `POST /api/setup/ensure`):
  - `aas_invoice_template_enabled` (Check)
  - `aas_invoice_template_key` (Select: `heuristic_v1`, `table_v1`)
  - `aas_invoice_template_sample_pdf` (Attach)

**How templates are chosen**
- Upload a vendor’s sample invoice PDF.
- MW OCRs the sample and auto-selects a built-in template key (currently:
  - `table_v1` (best for invoices with consistent `Qty/Rate/Amount/HSN` columns)
  - `heuristic_v1` (fallback parser))
- MW stores the chosen key in `Supplier.aas_invoice_template_key`.

**Runtime behavior**
- When processing `POST /api/orders/{id}/vendor-pdf`, MW:
  1. Loads the order’s assigned vendor (`Sales Order.aas_vendor`).
  2. Loads `Supplier` by name from ERPNext.
  3. If template is enabled and a known `aas_invoice_template_key` is set, parses items using that template first.
  4. If template parsing yields no items (or key is unknown), falls back to the existing heuristic parser (`VendorPdfParser`).

### New MW Endpoints (Admin-only)

- Upload/update the vendor’s sample invoice PDF (auto-detect template key):
  - `POST /api/vendors/{id}/invoice-template/sample` (multipart form field `file`)
  - Uploads to ERPNext via `/api/method/upload_file` and stores the resulting file URL in `Supplier.aas_invoice_template_sample_pdf`.
  - Sets `Supplier.aas_invoice_template_enabled = 1`.
  - Sets `Supplier.aas_invoice_template_key` based on OCR detection.

- Clear a vendor template:
  - `DELETE /api/vendors/{id}/invoice-template`
  - Clears `enabled/key/sample` fields on `Supplier`.

### UI: Vendor Tab Enhancements

- Vendor list shows a `Template` column (template key when enabled).
- Vendor edit form includes:
  - Enable toggle
  - Upload sample PDF action (calls MW upload endpoint)
  - Clear template action (calls MW delete endpoint)

### Seed Data Updates

`npm run seed:mock` now seeds a template key for vendor `FreshHarvest Agro Foods` so the OCR fixture PDF can be parsed via template-based parsing.

### Operational Notes

- Run `POST /api/setup/ensure` after upgrading to provision the new Supplier custom fields.
- MW still stores ERPNext sessions in-memory (`ErpSessionStore`), so MW restart logs users out and multi-node MW requires a shared session strategy (out of scope of this change).
