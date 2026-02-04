# Tasks — Transactions (AGENT_TXN)

## Scope
- Stock Management
- Order Management
- Bills Management

## Rules
- Follow `docs/codex-scm-ui-refactor.md`
- Angular Material + Reactive Forms only
- Reuse existing APIs; do not create new endpoints
- No business logic in HTML
- Add Jasmine/Karma tests for modified code

## Task List

### Task ID: TXN-01
Module: stock
Objective: Refactor stock management UI with low-stock highlighting and threshold handling per contract.

### Task ID: TXN-02
Module: orders
Objective: Refactor order creation/editing to support Option 1/Option 2 views, status gating, and manual vendor selection.

### Task ID: TXN-03
Module: bills
Objective: Refactor bills UI to hide margin/original rate, support manual bill creation, and enforce read-only after creation.
