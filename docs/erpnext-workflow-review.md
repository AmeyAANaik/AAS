# ERPNext Workflow Review

## 2026-04-10 Pricing And GST Review

### Scope Reviewed
- Order capture to vendor bill validation
- Sell-order pricing and sales invoice generation
- Sales invoice PDF summary presentation
- UI bill-mismatch and GST inference logic

### Files Inspected
- `mw/src/main/java/com/aas/mw/service/OrderPricingService.java`
- `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
- `mw/src/main/java/com/aas/mw/service/VendorPdfService.java`
- `mw/src/main/java/com/aas/mw/service/InvoiceService.java`
- `mw/src/main/java/com/aas/mw/service/SetupService.java`
- `ui/src/app/orders/order-page/order-page.component.ts`

### Current Implementation Summary
- Margin is applied on vendor rate before GST.
- Sales invoice GST is applied through ERPNext item tax templates using `aas_gst_percent`.
- The generated sales invoice stores pre-tax line `rate`, `amount`, `net_rate`, and `net_amount`; ERPNext computes tax separately into `total_taxes_and_charges` and `grand_total`.
- Transport can be added as a separate invoice item and is currently non-GST in the reviewed case.
- The UI and middleware both use a heuristic to infer whether parsed vendor-PDF line amounts are tax-inclusive, so they do not add GST twice during vendor bill validation.

### ERPNext Alignment Notes
- Applying margin on pre-tax item rate is aligned with ERPNext's standard model where item rates are base rates and tax is added by tax templates.
- Creating Sales Invoice items with base sell rate and then attaching item tax templates is the correct accounting direction and does not inherently double tax.
- The updated print format now separates before-tax and with-tax values more clearly than before.

### Findings
1. No verified double-tax bug in the sell-side pricing path.
   - `OrderPricingService.applyMrpCap` calculates sell rate from `vendorRate * (1 + margin%)` with no GST in the formula.
   - `OrderBillingService.buildSellItems` uses vendor/base rate as the input to margin, then stores the resulting sell rate as the Sales Invoice item `rate`.
   - `InvoiceService.applyItemTaxTemplates` applies GST afterward through ERPNext item tax templates, which means tax is added once at invoice time.

2. Vendor bill validation depends on tax-inclusion inference, which is a residual workflow risk.
   - `OrderBillingService.sumOrderItemsTotal` adds GST on top of parsed line amounts only when `inferGstIncludedInLineAmounts(...)` returns false.
   - `order-page.component.ts` mirrors similar inference in `gstIncludedInLineAmounts`.
   - This prevents double tax for known invoice layouts, but it is heuristic and can misclassify unfamiliar vendor formats if labels or totals are ambiguous.

3. Transport handling is now presentation-safe in the PDF, but business semantics should stay explicit.
   - Transport is added as a separate invoice item in `OrderBillingService.createSellOrder` when `apply_transport_to_invoice` is true.
   - The PDF summary now breaks out goods subtotal, transport, taxable total, GST total, and invoice total, which reduces confusion.

### Verified Evidence
- Margin before tax:
  - `OrderPricingService.applyMrpCap`: sell rate derived directly from vendor rate.
  - `OrderBillingService.buildSellItems`: `vendorRate` is used as the source rate before GST.
- GST after margin:
  - `InvoiceService.applyItemTaxTemplates`: attaches item tax template from `aas_gst_percent`.
- No double tax in reviewed invoice `ACC-SINV-2026-00009`:
  - `Sales Invoice.net_total = 116072.27`
  - `total_taxes_and_charges = 3139.26`
  - `grand_total = 119211.53`
  - This confirms base amount plus tax once.

### Gaps To Close
- Replace GST-inclusion heuristics with explicit parser metadata whenever possible, so vendor bill validation knows whether parsed `rate`/`amount` is inclusive or exclusive without inference.
- Add a focused regression test covering both vendor-PDF modes:
  - line amounts exclusive of GST
  - line amounts inclusive of GST
- Add one workflow note in the UI explaining that sell margin is applied on pre-tax rate, while invoice totals shown to finance are tax-inclusive.

### Proposed Follow-Up Work
- Add backend tests around `inferGstIncludedInLineAmounts` and `sumOrderItemsTotal` using real sample invoices.
- Persist parser-level flags such as `rate_includes_tax` / `amount_includes_tax` from vendor template extraction to avoid ambiguous matching.
- Consider showing both pre-tax and with-tax preview values in the order review UI, not only in the final PDF.

## 2026-05-30 Draft-Only Vendor PDF Reupload Bill Update

### Scope Reviewed
- Vendor PDF re-upload behavior after vendor bill / branch invoice generation
- Draft-only invoice replacement policy and versioning

### Current Implementation Summary
- Vendor PDF upload (`/orders/{id}/vendor-pdf`) is allowed not only during `VENDOR_ASSIGNED` / `VENDOR_PDF_RECEIVED`, but also during `VENDOR_BILL_CAPTURED` and `SELL_ORDER_CREATED`.
- If re-upload happens while the linked invoices are still draft (`docstatus = 0`), the middleware regenerates:
  - Vendor Purchase Invoice (always for `VENDOR_BILL_CAPTURED` and `SELL_ORDER_CREATED`)
  - Customer Sales Invoice (only for `SELL_ORDER_CREATED`)
- Old draft invoices are retained for audit and marked as replaced:
  - `aas_invoice_version_status = OLD`
  - `aas_replaced_by = <new invoice id>`

### ERPNext Alignment Notes
- This flow is accounting-safe because it only operates on draft documents; draft edits/replacements do not create ledger impact in ERPNext until submit.
- Submitted document corrections are explicitly out-of-scope for this flow; ERPNext generally expects a cancel/amend path after submission, which must be handled as a separate revision process.

### Findings
1. Draft-only replacement is an appropriate safety boundary.
   - It prevents silent ledger changes and forces a deliberate accounting workflow once documents are submitted.
2. Auditability is preserved via explicit versioning fields on replaced invoices.
   - Older drafts remain queryable but can be filtered out of active lists using `aas_invoice_version_status`.
