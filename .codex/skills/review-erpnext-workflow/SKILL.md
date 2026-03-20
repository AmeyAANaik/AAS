---
name: review-erpnext-workflow
description: Review AAS workflow, code paths, and documentation against ERPNext-aligned standards, then record findings in the repo. Use when asked to review application workflow, compare current behavior with ERPNext conventions, audit order-to-bill-to-payment flows, assess middleware/UI/ERP mappings, or produce/update workflow documentation for this project.
---

# Review ERPNext Workflow

Review the implemented AAS workflow as a real system, not just as an abstract design. Trace behavior across `ui/`, `mw/`, and ERPNext mappings, compare that behavior with standard ERPNext expectations, and leave behind durable documentation in the repo.

## Review Flow

1. Read the current review baseline in `docs/erpnext-workflow-review.md`.
2. Read repo context only as needed:
   - Start with `PROJECT_CONTEXT.md`.
   - Use `docs/system-architecture-analysis.md` for current architecture and endpoint coverage.
   - Use `references/source-map.md` in this skill to jump to likely implementation files.
3. Inspect the concrete code paths for the workflow in scope.
4. Compare the implementation with the checklist in `references/erpnext-review-checklist.md`.
5. Produce findings ordered by severity, with file evidence.
6. Update `docs/erpnext-workflow-review.md` so the repo keeps a dated record of:
   - scope reviewed
   - evidence checked
   - alignment with ERPNext
   - gaps and risks
   - recommended next actions

## Expected Review Standard

Favor standard ERPNext behavior unless there is a clear project-specific reason not to.

Check especially for:
- Correct use of standard doctypes before inventing custom workflow state or duplicate records.
- Clean document lineage between branch request, vendor procurement, billing, sales invoice, and payment.
- Role-safe transitions enforced in backend code, not only the UI.
- Accounting-safe sequencing: draft, submit, invoice, payment, reconciliation.
- Minimal duplication of business state across custom fields when standard ERPNext fields could serve.
- Sufficient auditability: attachments, linked documents, references, and changelog-worthy workflow notes.

Treat a mismatch as acceptable only when the repo shows a deliberate tradeoff and the consequences are documented.

## Writing Findings

Write findings like a code review:
- Lead with bugs, workflow risks, regressions, missing validations, or documentation gaps.
- Cite exact files and relevant behavior.
- Distinguish verified facts from inference.
- Keep summaries brief; prioritize actionable issues.

If no major issues are found, say so explicitly and still note residual risks or missing test coverage.

## Updating The Repo Document

Always update `docs/erpnext-workflow-review.md` when this skill is used for a substantial review.

Append or refresh a dated section that includes:
- review date
- workflow scope
- files/documents inspected
- current implementation summary
- ERPNext alignment notes
- gaps to close
- proposed follow-up work

Preserve earlier review history unless it is clearly stale and replaced by a better summary.

## AAS-Specific Notes

- AAS uses middleware as the only UI-to-ERP integration path.
- The current workflow is centered on `Sales Order`, with custom `aas_*` fields carrying state and references.
- Vendor bill capture currently creates `Purchase Invoice`.
- Branch billing currently creates `Sales Invoice` from middleware-driven calculations.
- The middleware state machine is important evidence, but do not assume it is the whole workflow; verify controller, service, and UI behavior around it.

## References

- Use `references/source-map.md` to find the most relevant files by workflow area.
- Use `references/erpnext-review-checklist.md` for the review lens and documentation format.
