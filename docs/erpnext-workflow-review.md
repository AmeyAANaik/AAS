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

## 2026-04-18 App Context And Flow Mapping Review

### Scope Reviewed
- Application context baseline and currently implemented flow surface
- Core workflow endpoints for orders, billing, payments, vendor ops, branch ops, and reports
- Middleware state-machine and role enforcement that gate transitions
- Core flow documentation images generated for quick operational context

### Files Inspected
- `docs/archived/PROJECT_CONTEXT.md`
- `docs/archived/system-architecture-analysis.md`
- `mw/src/main/java/com/aas/mw/config/SecurityConfig.java`
- `mw/src/main/java/com/aas/mw/service/OrderFlowStateMachine.java`
- `mw/src/main/java/com/aas/mw/controller/OrdersController.java`
- `mw/src/main/java/com/aas/mw/controller/VendorAssignmentController.java`
- `mw/src/main/java/com/aas/mw/controller/InvoiceController.java`
- `mw/src/main/java/com/aas/mw/controller/PaymentsController.java`
- `mw/src/main/java/com/aas/mw/controller/VendorOpsController.java`
- `mw/src/main/java/com/aas/mw/controller/BranchOpsController.java`
- `mw/src/main/java/com/aas/mw/controller/ReportsController.java`
- `docs/CORE_FLOWS/APP_CONTEXT_AND_FLOWS.md`
- `docs/CORE_FLOWS/images/*.png`

### Current Implementation Summary
- AAS remains middleware-mediated (`UI -> MW -> ERPNext`) with ERPNext as system of record.
- Order lifecycle transitions are enforced in backend state-machine methods and reflect a staged order-to-bill-to-invoice pipeline.
- Vendor and branch operations modules expose summary/detail/order/ledger endpoints with CSV exports for finance operations.
- Billing supports invoice creation and GST item tax-template handling, while payments now emphasize attachment-backed payment submission.
- Reporting endpoints are middleware-computed aggregations rather than direct ERP report passthrough.

### ERPNext Alignment Notes
- **Aligned:** Core ERPNext doctypes are reused for accounting artifacts (`Sales Order`, `Purchase Invoice`, `Sales Invoice`, `Payment Entry`) rather than replacing them with custom storage.
- **Aligned:** Transition controls are enforced in backend services (`OrderFlowStateMachine`), not only in UI gating.
- **Aligned with caveat:** Workflow status tracking continues in `aas_status`, which is acceptable for orchestration but must stay synchronized with underlying ERP document state semantics.

### Findings
1. Backend transition guardrails are explicit and generally ERP-safe for the documented sequence.
   - `OrderFlowStateMachine` constrains assignment, PDF upload, bill capture, and sell-order creation by source state.

2. Security configuration is role-granular on most sensitive endpoints and includes helper/admin separation for vendor bill processing.
   - `SecurityConfig` constrains vendor assignment and invoicing to admin-level actions while allowing controlled operational roles for reads and payment evidence uploads.

3. Payment workflow semantics have hardened around evidence attachment but can diverge from older docs that mention plain `POST /api/payments` as the main path.
   - `PaymentsController` now returns an error on bare `POST /api/payments` and requires multipart evidence upload at `/api/payments/with-attachments`.

4. Core flows are documented with generated image artifacts for faster onboarding and review.
   - Added `docs/CORE_FLOWS/APP_CONTEXT_AND_FLOWS.md` with five generated flow images.

### Gaps To Close
- Update any remaining older flow docs that still present `POST /api/payments` as a normal successful path without mandatory attachments.
- Consider un-archiving or reintroducing canonical top-level context docs (`PROJECT_CONTEXT.md`, `docs/system-architecture-analysis.md`) at expected locations to reduce drift against skill references.
- Add a lightweight automated check ensuring flow docs and endpoint semantics stay consistent when controllers change.

### Proposed Follow-Up Work
- Add a docs consistency pass specifically for payment endpoints and invoice settlement behavior.
- Add regression tests for role-based access to order/invoice/payment state-changing routes.
- Extend generated flow images with version/date stamps embedded in each image footer to support documentation audits.
