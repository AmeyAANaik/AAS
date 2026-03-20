# ERPNext Review Checklist

Use this checklist to compare AAS behavior with ERPNext-oriented workflow standards.

## 1. Workflow Modeling

- Check whether standard ERPNext doctypes are reused before custom states or duplicate records are introduced.
- Check whether custom `aas_*` fields complement the standard model instead of replacing core ERPNext meaning.
- Check whether each business step has one clear system-of-record document.

## 2. State And Transition Control

- Verify transitions in middleware service logic, not only in UI controls.
- Verify transition preconditions such as vendor assignment, bill capture, and sell-order creation.
- Verify whether invalid transitions are blocked with useful errors.

## 3. Document Lineage

- Trace links from branch request/image to `Sales Order`.
- Trace links from `Sales Order` to vendor-side procurement and `Purchase Invoice`.
- Trace links from vendor bill to branch-side `Sales Invoice` and later payment.
- Verify attachments and source reference fields are persisted.

## 4. Accounting Alignment

- Check whether invoices and payments follow standard ERPNext submission flow.
- Check whether totals, taxes, transport, rounding, and margin logic are explicit and auditable.
- Check whether custom calculations create risk of divergence from ERPNext totals.

## 5. Roles And Permissions

- Verify permissions in backend security configuration.
- Verify role restrictions match real workflow ownership.
- Verify sensitive actions are not protected only by hidden UI buttons.

## 6. Testing And Documentation

- Check for tests around state transitions and financial calculations.
- Check whether docs describe the implemented flow rather than the intended flow.
- Check whether the review document records dated findings and next actions.

## Review Output Template

Record:
- Scope reviewed
- Evidence inspected
- Current workflow summary
- Aligned with ERPNext
- Divergences or risks
- Recommended changes
