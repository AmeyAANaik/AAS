# AAS ERPNext Workflow Review

This document is the persistent review log for how AAS workflow maps to ERPNext-style standards.

## Review Baseline

- System shape: Angular UI -> Spring Boot middleware -> ERPNext/Frappe
- System of record: ERPNext
- Current primary business flow: branch request/image -> sales order -> vendor assignment -> vendor bill capture -> branch sales invoice -> payment
- Main implementation references:
  - `PROJECT_CONTEXT.md`
  - `docs/system-architecture-analysis.md`
  - `mw/src/main/java/com/aas/mw/service/OrderFlowStateMachine.java`
  - `mw/src/main/java/com/aas/mw/service/OrderService.java`
  - `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
  - `mw/src/main/java/com/aas/mw/service/PaymentService.java`
  - `ui/src/app/orders/order.service.ts`

## 2026-03-20 Review

### Scope

Reviewed the order-to-billing workflow and the new Codex skill path for future reviews.

### Workflow Summary

- AAS creates and manages branch demand primarily on `Sales Order`.
- Branch-image intake can create a `Sales Order` directly and immediately mark it `VENDOR_ASSIGNED`.
- Middleware stores workflow progression in custom `aas_status` values on `Sales Order`.
- Vendor bill capture creates a `Purchase Invoice` and writes bill metadata back to the source `Sales Order`.
- Branch-side billing is currently implemented by creating a `Sales Invoice` from middleware-calculated sell items and margin.
- Customer payment is handled by creating a `Payment Entry` against the `Sales Invoice`.

### Aligned With ERPNext

- Reuses standard ERPNext doctypes instead of introducing a large custom transaction stack.
- Uses `Purchase Invoice`, `Sales Invoice`, and `Payment Entry` for accounting-facing actions.
- Enforces key workflow guards in middleware through `OrderFlowStateMachine`.
- Persists source references through custom fields such as `aas_source_sales_order` and other `aas_*` linkage fields.

### Gaps And Risks

- `Sales Order` is carrying both branch-order intent and orchestration state, which can blur standard ERPNext document meaning.
- Branch-image order creation sets `aas_status` to `VENDOR_ASSIGNED` immediately in `OrderService#createOrderWithImage`, even though vendor assignment is also modeled as a separate explicit step.
- The documented architecture notes mention sell order creation, but `OrderBillingService#createSellOrder` currently creates a `Sales Invoice` and clears `aas_so_branch`, which suggests the document lineage is thinner than the workflow wording implies.
- Custom `aas_status` progression lives outside native ERPNext Workflow configuration, so ERPNext UI behavior and middleware behavior may diverge if users act directly in ERPNext.
- Review and architecture docs are stronger than test evidence right now; the workflow has more business complexity than the visible automated coverage suggests.

### Recommended Next Actions

- Decide whether branch demand should remain a `Sales Order` from the first step or move to a clearer pre-sales/request document model.
- Clarify and standardize the vendor-assignment step so auto-assignment and manual assignment do not imply conflicting states.
- Document the exact branch-side output: `Sales Order`, `Sales Invoice`, or both.
- Add or expand tests around state transitions, invoice creation, and payment linkage.
- Keep this document updated whenever workflow behavior changes.

## 2026-03-22 Review

### Scope

Reviewed the vendor workflow behind the Vendor tab, order-side vendor processing, and vendor operations reporting.

### Evidence Inspected

- `ui/src/app/vendors/vendor-list/vendor-list.component.ts`
- `ui/src/app/vendors/vendor-form/vendor-form.component.ts`
- `ui/src/app/vendors/vendor.service.ts`
- `ui/src/app/orders/order-page/order-page.component.ts`
- `ui/src/app/orders/order.service.ts`
- `mw/src/main/java/com/aas/mw/service/MasterDataService.java`
- `mw/src/main/java/com/aas/mw/service/VendorAssignmentService.java`
- `mw/src/main/java/com/aas/mw/service/VendorPdfService.java`
- `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
- `mw/src/main/java/com/aas/mw/service/VendorOpsService.java`
- `mw/src/main/java/com/aas/mw/meta/VendorFieldRegistry.java`
- `mw/src/main/java/com/aas/mw/config/SecurityConfig.java`

### Current Workflow Summary

- Vendor master data is stored on ERPNext `Supplier`, with AAS-specific vendor fields mapped to `aas_*` custom fields.
- The Vendor tab creates and updates vendor records through `/api/vendors`, and activation is intentionally blocked unless invoice template JSON and a validated sample PDF exist.
- Standard order creation can auto-derive vendor and category defaults, while branch-image order creation immediately assigns the top vendor for the selected category and sets `aas_status` to `VENDOR_ASSIGNED`.
- Manual vendor assignment is available only while the order is still in `DRAFT`.
- Vendor PDF upload performs OCR, attempts vendor-template parsing first, falls back to heuristic parsing, creates a `Purchase Order`, and writes parsed item and bill metadata back onto the source `Sales Order`.
- Vendor bill capture creates a `Purchase Invoice`, validates bill totals against parsed item totals plus transport, and advances the order to `VENDOR_BILL_CAPTURED`.
- Branch-side billing creates a `Sales Invoice` and stores its link on the same source `Sales Order`.
- Vendor operations dashboards derive backlog, parsing health, and ledger activity from `Supplier`, `Sales Order`, `Purchase Invoice`, and `Payment Entry`.

### Aligned With ERPNext

- Uses standard ERPNext doctypes for vendor, purchase, sales, and payment records instead of a parallel transaction model.
- Keeps vendor master data on `Supplier` and stores AAS-specific configuration in custom fields.
- Enforces important workflow guards in middleware rather than relying only on UI state.
- Persists document links such as source order, purchase order, purchase invoice, vendor PDF, and branch invoice references.

### Gaps And Risks

- Branch-image order creation skips the `DRAFT` step and marks the order `VENDOR_ASSIGNED` at creation time, while manual assignment is still modeled as a separate `DRAFT`-only action. This leaves two competing meanings for “vendor assigned.”
- Vendor activation currently depends on invoice-template readiness, so a vendor without OCR template configuration cannot be active or assignable even if procurement should be allowed without OCR. This is a workflow policy choice with significant operational impact.
- The UI treats any existing sample PDF as sufficient for activation state, but the backend re-validates against required parsed columns when activating. The frontend can therefore imply readiness slightly earlier than the backend will actually accept it.
- Security rules allow vendor users to post arbitrary order status updates, but vendor-specific operational actions such as PDF upload and bill capture are reserved for admin/helper roles. If vendor users are expected to work inside the vendor flow directly, the permission model does not currently match that expectation.
- Branch-side “sell order” wording still maps to `Sales Invoice` creation rather than a persisted branch `Sales Order`, so the documented lineage remains thinner than the labels suggest.

### Recommended Next Actions

- Decide whether vendor assignment should always be explicit, or whether category-based auto-assignment should become the only supported path for branch-image orders.
- Decide whether invoice-template validation is required for vendor activation, or whether it should instead be required only for OCR-assisted PDF processing.
- Align frontend vendor activation messaging with backend validation rules so the Vendor tab does not overstate readiness.
- Revisit role ownership for vendor workflow actions and tighten or expand permissions to match the intended business process.
- Keep documenting whether the branch-side commercial document is meant to be a `Sales Order`, a `Sales Invoice`, or both.
