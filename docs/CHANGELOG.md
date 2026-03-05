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
  - `aas_invoice_template_json` (Code, optional)
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
  - Template JSON editor (paste JSON; stored on `Supplier.aas_invoice_template_json`)
  - Clear template action (calls MW delete endpoint)

**Notes**
- Vendor Template JSON is now the preferred way to configure parsing for a specific vendor.
- The sample-PDF upload flow remains available via MW endpoint, but is no longer required for UI usage.

### Seed Data Updates

`npm run seed:mock` now seeds a template key for vendor `FreshHarvest Agro Foods` so the OCR fixture PDF can be parsed via template-based parsing.

### Operational Notes

- Run `POST /api/setup/ensure` after upgrading to provision the new Supplier custom fields.
- MW still stores ERPNext sessions in-memory (`ErpSessionStore`), so MW restart logs users out and multi-node MW requires a shared session strategy (out of scope of this change).

### Order Manage Flow: Invoice Review/Edit (PDF -> Items -> Persist)

Manage Order (Orders page modal) now supports a practical “review and fix” loop after OCR:

- After uploading vendor PDF in Step 2, UI shows **Review invoice items**:
  - editable item table (qty +/-, inline qty/rate edits, remove line)
  - totals summary: `Items total`, `Bill total`, `Diff`
  - quick action: `Set bill total = items total`
  - action: `Update order items` to persist changes back to ERPNext
- UI shows the **template key** used for parsing (when available from MW response).

**Middleware changes**
- `POST /api/orders/{id}/vendor-pdf` response now includes:
  - `orderItems`: resolved ERPNext `item_code/item_name/qty/rate/amount` list for immediate UI display
  - `template`: `{ configured, used, key }` for UI visibility
- New endpoint to persist edited item lines:
  - `PUT /api/orders/{id}/items`
  - Updates **Sales Order** items and (if linked) the **Purchase Order** items so both stay consistent.
  - Allowed when status is `VENDOR_ASSIGNED` or `VENDOR_PDF_RECEIVED`.

**Vendor JSON template parsing**
- If `Supplier.aas_invoice_template_enabled=1` and `Supplier.aas_invoice_template_json` is present, MW will:
  - attempt to parse items using the vendor JSON template (supports multi-line OCR rows by joining small windows of adjacent lines)
  - normalize bill date matches to ERPNext format (`YYYY-MM-DD`)
  - fall back to the default heuristic parser if the template yields no items

### Order Reliability Fixes

- If OCR resolves an ERPNext `Item` that exists but is **disabled**, MW best-effort re-enables it so the flow can proceed.
- The placeholder item used by branch-image order creation (`AAS-BRANCH-IMAGE`) is now also auto-re-enabled if it exists but is disabled (prevents order creation failures).
- Orders list sorting in MW was changed to `modified desc` so newly created orders reliably appear in the first page of results.

### Deletion UX: “Delete Order” Behavior

- UI still provides **Delete Order** (not “delete item”), but deletion is now:
  - blocked when the Sales Order is linked to a Purchase Order (`aas_po`), because ERPNext generally prevents deleting/cancelling linked docs
  - MW returns a clearer `409` conflict and UI disables the delete button when deletion isn’t allowed
