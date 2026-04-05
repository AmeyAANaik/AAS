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

## Project Context to Provide (Fill In Before Use)
Add these details above the prompt when you paste it into Codex:

- Repo/modules to touch (e.g., `ui/`, `mw/`, `erpmodule/`)
- API base URLs and auth scheme used by the UI and middleware
- Current order-related endpoints (if any) and their versions
- ERPNext app name, app version, and whether custom doctypes/fixtures are used
- Existing workflow/state names (if any) to align with
- Roles used in your ERPNext instance (branch, vendor, finance, admin, etc.)
- Preferred testing tools and locations for UI/middleware tests

If any of the above are unknown, explicitly say “unknown” so Codex can surface questions.

---

## Definition of Done (Codex Must Satisfy)
- Architecture and data model map from image upload → order creation → vendor bill → sell order.
- Concrete file list with edits and brief diffs for each file.
- Explicit state machine with allowed transitions and enforcement points.
- Middleware endpoints and payloads with versioning notes.
- UI flow with screens/components, data fetching, and validation coverage.
- Tests covering critical state transitions and margin calculation.
- Migration/backfill notes and rollout plan.

---

## Optional Add‑Ons (Include If Relevant)
- Observability: audit log entries at each state transition.
- Permissions: role matrix per endpoint and per state transition.
- Notifications: email/Slack when vendor is assigned or invoice posted.
- Feature flagging: gate the flow for pilot branches/vendors.

---

## Project-Specific Prefill (AAS Repo)
Paste this block above the prompt when using it for this repo.

**Repo modules to touch**
- `ui/` (Angular)
- `mw/` (Spring Boot)
- `erpmodule/` (ERPNext/Frappe)

**API base + auth**
- Base path: `/api`
- Auth: `POST /api/auth/login` returns JWT (stored in UI `localStorage` as `aas_auth_token`)
- UI always calls middleware (UI never calls ERPNext directly)

**Current order/invoice endpoints**
- Orders:
  - `POST /api/orders`
  - `GET /api/orders`, `GET /api/orders/{id}`
  - `PUT /api/orders/{id}`
  - `POST /api/orders/{id}/status`
  - `POST /api/orders/{id}/assign-vendor`
  - `GET /api/orders/export`
- Invoices:
  - `POST /api/invoices`
  - `GET /api/invoices`
  - `GET /api/invoices/export`
  - `GET /api/invoices/{id}/pdf`
- Payments: `POST /api/payments`

**ERPNext mappings (current)**
- Orders: `Sales Order`
- Invoices: `Sales Invoice`
- Payments: `Payment Entry`
- Vendors: `Supplier`
- Shops/Branches: `Customer`
- Items: `Item`, Categories: `Item Group`

**Existing custom fields (via `POST /api/setup/ensure`)**
- `Sales Order`: `aas_vendor` (Supplier link), `aas_status` (Select)
- `Item`, `Sales Order Item`: `aas_margin_percent`, `aas_vendor_rate`

**Roles**
- App roles mapped from ERP roles: `ADMIN`, `VENDOR`, `SHOP`, `HELPER`
- Key access rules enforced in `mw/src/main/java/com/aas/mw/config/SecurityConfig.java`

**Testing locations/tools**
- UI: Angular unit tests in `ui/` (Karma/Jasmine)
- Middleware: JUnit tests in `mw/`
- ERPNext: fixtures/migrations in `erpmodule/` (if needed)

**Key files (starting points)**
- Middleware controllers:
  - `mw/src/main/java/com/aas/mw/controller/OrdersController.java`
  - `mw/src/main/java/com/aas/mw/controller/VendorAssignmentController.java`
  - `mw/src/main/java/com/aas/mw/controller/InvoiceController.java`
  - `mw/src/main/java/com/aas/mw/controller/PaymentsController.java`
  - `mw/src/main/java/com/aas/mw/controller/SetupController.java`
- Middleware services/clients:
  - `mw/src/main/java/com/aas/mw/service/OrderService.java`
  - `mw/src/main/java/com/aas/mw/service/InvoiceService.java`
  - `mw/src/main/java/com/aas/mw/service/PaymentService.java`
  - `mw/src/main/java/com/aas/mw/service/SetupService.java`
  - `mw/src/main/java/com/aas/mw/client/ErpNextClient.java`
- UI services:
  - `ui/src/app/orders/order.service.ts`
  - `ui/src/app/bills/bills.service.ts`
  - `ui/src/app/shared/auth-token.service.ts`

**Known gaps vs. target flow**
- Middleware:
  - No image upload endpoint or ERPNext `File` attachment handling wired to `Sales Order`.
  - No explicit state machine enforcement beyond `aas_status` writes in `POST /api/orders/{id}/status`.
  - No vendor bill capture mapped to purchase docs (current invoices are `Sales Invoice`, not `Purchase Invoice`).
  - No automated sell-order creation from vendor bill + margin.
  - No configurable margin setting at order/header level (only item-level fields exist).
  - No audit logging at state transitions.
- ERPNext:
  - No `Branch Order Request` doctype (if needed) or workflow state configuration for this flow.
  - No configured workflow states tied to roles for `Sales Order`/`Purchase Invoice`.
  - No defined mapping from vendor bill back to branch order (custom link fields likely needed).
  - No standard attachment rules for order images (needs `File` link and permissions).
- UI:
  - No image upload component or order creation from image flow.
  - No status timeline component or state transition UI.
  - No vendor assignment UI beyond existing order update paths.
  - No vendor bill entry UI tied to an order.
  - No sell order preview (margin calc) or create action from vendor bill.


---

## Short Version (If You Need a Compact Prompt)

Implement an ERPNext-driven branch order workflow: image upload -> create order with attachment -> assign vendor -> state transition -> capture vendor bill -> generate branch sell order with 10% margin. Update middleware APIs, UI screens, and ERPNext mappings/workflows. Add validation, permissions, and automated tests. Return architecture, state machine, API contracts, file-level change plan, and rollout notes.
