# Codex Prompt: Branch Image-to-Order Flow in ERPNext (UI + Middleware)

Use this prompt with Codex to implement and explain the full order lifecycle.

## Ready-to-use Prompt

You are a senior ERPNext + full-stack engineer. Implement an end-to-end **Branch Image-to-Order workflow** using the existing architecture (UI + middleware + ERPNext), and update both backend and frontend to support it.

### Business Flow
1. A **branch uploads an image** of an order.
2. After upload, the system must **create a purchase request/order record** and attach/store the image with that order.
3. The order is then **assigned to a vendor**.
4. The system must **change the order state** at each step in a controlled workflow.
5. When vendor bill/invoice data is received, users can **enter vendor bill details** and update state.
6. System then creates a **sell order back to branch** by applying margin on vendor purchase amount.
7. Assume margin = **10%** (configurable preferred).
8. Use ERPNext features as much as possible and integrate with current middleware and UI.

### Technical Requirements
- Reuse ERPNext standard doctypes/workflows where possible (Sales Order, Purchase flow, attachments, statuses, naming, permissions).
- If custom doctypes/fields are needed, define them clearly with migration/fixtures.
- Ensure middleware APIs are explicit and version-safe.
- UI should support:
  - image upload,
  - order creation from image,
  - vendor assignment,
  - status timeline,
  - vendor bill entry,
  - calculated sell order preview (with 10% margin),
  - final sell order creation.
- Add validations:
  - cannot assign vendor before order exists,
  - cannot create sell order before vendor bill is posted/validated,
  - margin must be non-negative,
  - state transitions must be role- and sequence-safe.

### ERPNext Mapping (Expected)
Propose and implement mapping like:
- Branch request: custom "Branch Order Request" or Sales Order draft + attachment.
- Vendor assignment: supplier link + assignment log.
- Vendor bill: Purchase Invoice (or mapped equivalent).
- Sell order to branch: Sales Order generated from vendor-billed amount + margin.

Formula:
- `sell_amount = vendor_bill_total * (1 + margin_percent / 100)`
- For 10% margin: `sell_amount = vendor_bill_total * 1.10`

### Deliverables
1. Data model updates (ERPNext/custom fields/doctypes/workflow states).
2. Middleware endpoints + service-layer changes.
3. UI screens/forms/components and state-management updates.
4. Role/permission and validation logic.
5. Migration/backfill notes (if required).
6. Automated tests (unit/integration) for key flow and margin calculation.
7. API examples and sample payloads.
8. Rollout notes + edge cases.

### Output Format
Respond with:
1. **Architecture & Design Summary**
2. **Step-by-step Implementation Plan**
3. **Code Changes by Layer** (ERPNext, middleware, UI)
4. **State Machine Definition** (states + allowed transitions)
5. **API Contract** (request/response samples)
6. **Test Plan**
7. **Open Questions/Assumptions**

### Constraints
- Keep changes backward compatible when possible.
- Follow existing project conventions.
- Do not skip validation/error handling.
- Include exact files to change and brief diffs per file.

---

## Short Version (If You Need a Compact Prompt)

Implement an ERPNext-driven branch order workflow: image upload -> create order with attachment -> assign vendor -> state transition -> capture vendor bill -> generate branch sell order with 10% margin. Update middleware APIs, UI screens, and ERPNext mappings/workflows. Add validation, permissions, and automated tests. Return architecture, state machine, API contracts, file-level change plan, and rollout notes.
