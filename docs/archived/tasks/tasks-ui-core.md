# Tasks — UI Core (AGENT_UI_CORE)

## Scope
- Dashboard
- App layout
- Navigation
- Admin routing

## Rules
- Follow `docs/codex-scm-ui-refactor.md`
- Angular Material + Reactive Forms only
- Reuse existing APIs; do not create new endpoints
- No business logic in HTML
- Add Jasmine/Karma tests for modified code

## Task List

### Task ID: UI-CORE-01
Module: dashboard
Objective: Refactor dashboard to match SCM UI Contract widgets (read-only snapshot) and wire navigation drill-down.

### Task ID: UI-CORE-02
Module: app-layout
Objective: Introduce shared layout shell (toolbar, side nav) using Angular Material; keep existing routes working.

### Task ID: UI-CORE-03
Module: navigation
Objective: Implement Angular Material navigation structure and role-safe admin routing (admin-only v1).
