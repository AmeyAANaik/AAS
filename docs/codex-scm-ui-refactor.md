# SCM UI Refactor Contract (Codex)

## Purpose
This document defines the scope, rules, and workflow for refactoring an existing Angular SCM UI. It is the single source of truth for all refactor work.

## Non-Negotiable Rules
- Existing Angular application only; no rewrites.
- APIs already exist and MUST be reused.
- No new backend endpoints.
- Angular Material + Reactive Forms only.
- Business logic must be in TypeScript, not templates.
- Incremental refactor; preserve working functionality.
- Add tests for all modified or new code (Jasmine/Karma).
- No mock data.

## Target Module Structure
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

## Domain Entities (Source of Truth)
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

## SCM UI Contract (Summary)
### Authentication
Screens: Login, Register
Fields: Email, Password
Notes: Admin only; no social login

### Dashboard (Read-only)
Widgets:
- Order Status (Pending, Ready, In Transit, Delivered)
- Bills Due (per Branch)
- Bills Due (per Vendor)
- Warehouse Stock Quantity
- Sales & Revenue

### Vendor Management
Screens: Vendor List; Vendor Create/Edit
Fields: Vendor Name, Priority (integer), Status (Active/Inactive)
Rules: CRUD only; no auto-selection logic

### Branch Management
Fields: Branch Name, Location, WhatsApp Group Name (string only)
Rules: CRUD only; no WhatsApp API integration

### Category Management
Fields: Category Name, Assigned Vendors (multi-select), Assigned Items (multi-select)

### Item Management (Critical)
Fields: Item Code, Item Name, Measure Unit, Packaging Unit, Vendor Name,
Original Rate (vendor-specific), Margin Percentage (vendor-specific), Final Rate (calculated)
Rules: Same item can have multiple vendors; pricing is vendor-specific; Final Rate = Original Rate + Margin

### Stock Management
Fields: Item, Quantity, Threshold
Rules: Highlight low stock; no notification service implementation

### Order Management
Order Creation Options:
- Option 1: Show item rate, item amount, total amount
- Option 2: Do NOT show rate or amount
Rules: Orders editable until status = Accepted; orders merged if created before dispatch; vendor selection is manual

### Bills Management
Rules: Bills must NOT display margin or original rate
Manual Bill Creation: Item, Quantity, Original Rate (internal only), Final Rate (auto calculated)
Bills are read-only after creation

## API Usage Rules
- Reuse existing API services
- Do NOT rename API methods unless unavoidable
- No HTTP logic inside components

## Testing Requirements
Framework: Jasmine + Karma
For each modified or new component:
- Component creation test
- Form validation test
- API call invocation test
- Business rule validation test

## Workflow
1) Task Discovery (read task files; list Task IDs)
2) Wait for user approval of specific Task ID
3) Scan existing code for that module (no code changes)
4) Refactor incrementally to match contract
5) Add/Update tests for modified code
6) Validate against contract

## Output Format (per task)
- Files changed
- Summary of changes
- APIs reused
- Tests added/updated
- Assumptions or limitations
