# Tasks — Master Data (AGENT_MASTER_DATA)

## Scope
- Vendor Management
- Branch Management
- Category Management
- Item Management (vendor-specific pricing)

## Rules
- Follow `docs/codex-scm-ui-refactor.md`
- Angular Material + Reactive Forms only
- Reuse existing APIs; do not create new endpoints
- No business logic in HTML
- Add Jasmine/Karma tests for modified code

## Task List

### Task ID: MD-01
Module: vendors
Objective: Refactor vendor list + create/edit forms to match SCM contract fields and CRUD rules.

### Task ID: MD-02
Module: branches
Objective: Refactor branch CRUD to match SCM contract fields (including WhatsApp Group Name as string only).

### Task ID: MD-03
Module: categories
Objective: Refactor category CRUD with multi-select assigned vendors/items per SCM contract.

### Task ID: MD-04
Module: items
Objective: Refactor item CRUD with vendor-specific pricing and final rate calculation; enforce item-vendor mapping rules.
