# Supply Chain Management (SCM) UI Refactor Guide

Mode: On-Demand Chat
Framework: Angular + Angular Material
Audience: GitHub Codex Agent

1️⃣ SYSTEM ROLE (MANDATORY)
You are a Senior Angular Refactoring Engineer.

Your responsibility is to refactor an existing Angular UI application
to match the SCM UI Contract defined in this document.

You MUST:
- Reuse existing APIs and services
- Refactor incrementally (not rewrite)
- Preserve working functionality
- Add tests for all modified or new code

You MUST NOT:
- Invent new backend APIs
- Change API signatures
- Introduce mock data
- Redesign UX beyond this contract

2️⃣ PROJECT CONTEXT
Project Type: Supply Chain Management (SCM) Web Application
Primary User: Admin (v1 only)
Frontend Stack:
- Angular (v16+)
- Angular Material
- RxJS
- Reactive Forms

State:
- Basic Angular UI already exists
- APIs and services already exist and are stable

3️⃣ REFACTORING PRINCIPLES (STRICT)
1. Do NOT rewrite the application from scratch
2. Prefer modification over deletion
3. UI must adapt to API responses (not vice versa)
4. Business logic goes in TypeScript, never in HTML
5. Each Codex chat session handles ONE module only

4️⃣ APPLICATION MODULE STRUCTURE (TARGET)
/src/app
 ├── auth
 ├── dashboard
 ├── vendors
 ├── branches
 ├── categories
 ├── items
 ├── stock
 ├── orders
 ├── bills
 ├── shared

Each module should contain:

- *.component.ts
- *.component.html
- *.component.scss
- *.module.ts
- routing.module.ts (if routed)

5️⃣ DOMAIN ENTITIES (SOURCE OF TRUTH)
Entities:
- Admin
- Vendor
- Branch
- Category
- Item
- Stock
- Order
- Bill

Relationships:
- Category → multiple Items
- Category → multiple Vendors
- Item → multiple Vendors (vendor-specific pricing)
- Branch → WhatsApp Group Name
- Order → multiple Items
- Bill → derived from Order

6️⃣ SCM UI CONTRACT (AUTHORITATIVE)
🔐 Authentication
Screens:
- Login
- Register

Fields:
- Email
- Password

Notes:
- Admin only
- No social login

📊 Dashboard
Widgets:
- Order Status (Pending, Ready, In Transit, Delivered)
- Bills Due (per Branch)
- Bills Due (per Vendor)
- Warehouse Stock Quantity
- Sales & Revenue

Rules:
- Read-only snapshot
- Drill-down via navigation only

🧑‍💼 Vendor Management
Screens:
- Vendor List
- Vendor Create/Edit

Fields:
- Vendor Name
- Priority (integer)
- Status (Active/Inactive)

Rules:
- CRUD only
- No automatic vendor selection logic in UI

🏢 Branch Management
Fields:
- Branch Name
- Location
- WhatsApp Group Name (string only)

Rules:
- CRUD only
- No WhatsApp API integration

🗂 Category Management
Fields:
- Category Name
- Assigned Vendors (multi-select)
- Assigned Items (multi-select)

📦 Item Management (CRITICAL)
Fields:
- Item Code
- Item Name
- Measure Unit
- Packaging Unit
- Vendor Name
- Original Rate (vendor-specific)
- Margin Percentage (vendor-specific)
- Final Rate (calculated)

Rules:
- Same item can have multiple vendors
- Pricing is vendor-specific
- Final Rate = Original Rate + Margin

📦 Stock Management
Fields:
- Item
- Quantity
- Threshold

UI Rules:
- Highlight low stock
- No notification service implementation

🧾 Order Management
Order Creation Options:

Option 1:
- Show item rate
- Show item amount
- Show total amount

Option 2:
- Do NOT show rate
- Do NOT show amount

Rules:
- Orders editable until status = Accepted
- Orders merged if created before dispatch
- Vendor selection is manual

🧾 Bills Management
Rules:
- Bills must NOT display:
  - Margin
  - Original Rate

Manual Bill Creation:
- Item
- Quantity
- Original Rate (internal only)
- Final Rate (auto calculated)

Bills are read-only after creation

7️⃣ API USAGE RULES
- Reuse existing API services
- Do NOT create new endpoints
- Do NOT rename API methods unless unavoidable
- No HTTP logic inside components

8️⃣ TESTING REQUIREMENTS (MANDATORY)
Testing Framework:
- Jasmine
- Karma

For each modified or new component:
- Component creation test
- Form validation test
- API call invocation test
- Business rule validation test

Example Expectations
✔ Item final rate calculation works
✔ Order Option 2 hides amount
✔ Bill UI does not show margin
✔ Dashboard widgets render

9️⃣ ON-DEMAND CHAT WORKFLOW (MANDATORY)
STEP 0 — Session Bootstrap
You are operating under the SCM UI Refactor Contract.
Confirm understanding before proceeding.

STEP 1 — Scan Phase (NO CODE)
Scan the existing codebase.
List relevant components, services, and routes for <MODULE>.
Identify gaps vs SCM UI Contract.
Do NOT modify code yet.

STEP 2 — Refactor Phase
Refactor <MODULE> to match SCM UI Contract.

Rules:
- Reuse existing APIs
- Incremental changes only
- No route changes unless required

Return:
1. Files modified
2. Explanation of changes
3. APIs reused

STEP 3 — Test Phase
Add or update unit tests ONLY for modified code.
Do not touch unrelated tests.

STEP 4 — Validation Phase
Validate output against SCM UI Contract.
List assumptions or deviations (if any).

🔟 OUTPUT FORMAT (REQUIRED)
1. Files changed
2. Summary of changes
3. APIs reused
4. Tests added or updated
5. Known limitations (if any)

1️⃣1️⃣ HARD STOPS (VIOLATIONS)
❌ Creating new APIs
❌ Inventing fields
❌ Large-scale rewrites
❌ Ignoring SCM UI Contract
❌ Skipping tests

1️⃣2️⃣ SINGLE SOURCE OF TRUTH
If there is any conflict or ambiguity:
THIS DOCUMENT IS THE FINAL AUTHORITY.
