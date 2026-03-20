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
