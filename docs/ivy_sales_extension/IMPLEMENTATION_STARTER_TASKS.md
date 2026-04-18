# Ivy Sales Extension — Implementation Starter Tasks

Date: 2026-04-18
Target App: `ivy_sales_extension`

This folder is the starting point for implementation. Execute tasks in order unless dependencies require adjustment.

## Task 1 — Bootstrap app skeleton and module registration

:::task-stub{title="Bootstrap ivy_sales_extension app and register modules"}
Create a new Frappe app named `ivy_sales_extension` and add module registration for `ivy_sales`.

Add/update:
- `ivy_sales_extension/hooks.py`
- `ivy_sales_extension/modules.txt`
- `ivy_sales_extension/ivy_sales_extension/api/__init__.py`
- `ivy_sales_extension/ivy_sales_extension/ivy_sales/__init__.py`

Ensure `hooks.py` has app metadata, placeholder `doc_events` for `Sales Visit`, `Secondary Order`, `Collection Entry`, and `scheduler_events` for manager daily jobs.
:::

## Task 2 — Build configuration masters (config-first)

:::task-stub{title="Implement configuration DocTypes for vendor-driven behavior"}
Create DocTypes for reusable configuration:
- Vendor
- Brand
- Vendor Brand Mapping
- Outlet Segment
- Recommendation Rule
- Incentive Plan + Incentive Slab (child table)
- Sales KPI Definition
- Scheme / Promotion Rule
- Visit Policy

Add indexes/constraints for common lookups:
- vendor + brand
- vendor + channel + outlet_segment + active
- plan name + date range + active.
:::

## Task 3 — Build field-sales operational DocTypes

:::task-stub{title="Create operational DocTypes for beat, visit, order, collection, and stock"}
Implement:
- Outlet
- Beat Plan
- Beat Plan Outlet (child)
- Sales Visit
- Outlet Audit
- Secondary Order
- Secondary Order Item (child)
- Collection Entry
- Van Stock
- Van Stock Item (child)
- Outlet Sales Profile
- Incentive Ledger

Use links to ERPNext masters (`Customer`, `Item`, `Warehouse`, `Sales Invoice`) where appropriate.
:::

## Task 4 — Validation and lifecycle hooks

:::task-stub{title="Implement validate and submit hooks for visit, order, and collection"}
Create controller methods in:
- `.../doctype/sales_visit/sales_visit.py`
- `.../doctype/secondary_order/secondary_order.py`
- `.../doctype/collection_entry/collection_entry.py`

Business rules:
- one open visit per outlet + rep
- check-in required before checkout
- secondary order requires item rows and qty > 0
- collection amount > 0 and <= invoice outstanding

Wire methods in `hooks.py` `doc_events`.
:::

## Task 5 — Role and permission matrix

:::task-stub{title="Design and apply role permissions for rep, manager, admin, and finance"}
Define roles and permissions for all new DocTypes.

Document in:
- `docs/ivy_sales_extension/ROLE_PERMISSION_MATRIX.md`

Cover create/read/update/submit/cancel and approval responsibilities.
:::

## Task 6 — Spring Boot middleware module setup

:::task-stub{title="Set up middleware domain modules and ERPNext adapter"}
Create Spring Boot domain modules/services:
- outlet-service
- beat-plan-service
- visit-service
- order-service
- collection-service
- sync-service
- dashboard-service
- integration-service

Implement ERPNext adapter for auth/session, retries, error mapping, and DTO transformation.
:::

## Task 7 — Rep APIs

:::task-stub{title="Implement rep-facing APIs for beat, visit, order, collection, sync, and recommendations"}
Implement endpoints:
- `GET /api/rep/beat-plan/today`
- `POST /api/rep/visit/check-in`
- `POST /api/rep/visit/check-out`
- `POST /api/rep/orders`
- `POST /api/rep/collections`
- `POST /api/rep/sync`
- `GET /api/rep/outlet/{outletId}/recommendations`
- `GET /api/rep/outlet/{outletId}/sales-profile`
- `GET /api/rep/incentives/current`

Add idempotency keys for create/sync operations.
:::

## Task 8 — Manager APIs and control tower

:::task-stub{title="Implement manager APIs for dashboard, coverage, rules, and incentives"}
Implement:
- `GET /api/manager/dashboard`
- `GET /api/manager/route-coverage`
- `POST /api/manager/mark-missed-visits`
- `GET /api/manager/incentives/summary`
- `POST /api/manager/recommendation-rules`
- `POST /api/manager/incentive-plans`

Add filters by vendor, region, channel, date range, and rep.
:::

## Task 9 — Recommendation engine (phase 1)

:::task-stub{title="Build pluggable recommendation engine with rule-based strategy"}
Create strategy interface and initial implementation:
- `RuleBasedRecommendationStrategy`

Use inputs:
- outlet history
- last ordered SKU dates
- order frequency
- outlet segment/channel/vendor/brand
- active schemes
- stock availability

Return:
- recommended SKUs
- reason code
- confidence/priority
- suggested quantity.
:::

## Task 10 — Incentive engine and ledger

:::task-stub{title="Implement incentive calculation and incentive ledger lifecycle"}
Implement processor to evaluate `Incentive Plan` and `Incentive Slab`, compute payouts by period, and write `Incentive Ledger` with statuses (earned/pending/approved/paid).

Support periodic scheduler or queue-based computation.
:::

## Task 11 — Secondary Order -> Sales Order conversion

:::task-stub{title="Implement conversion pipeline from Secondary Order to ERPNext Sales Order"}
Build conversion service with:
- validation of item/pricing/scheme
- mapping to ERPNext fields
- creation of ERPNext `Sales Order`
- linkage back via `linked_sales_order`
- conversion logs for success/failure and reprocess support.
:::

## Task 12 — Offline sync and conflict resolution

:::task-stub{title="Implement sync contract with idempotency, retries, and conflict handling"}
Define sync envelope for visits/orders/collections with:
- client request IDs
- dedupe storage
- retry-safe behavior
- conflict detection strategy
- sync audit log entries.
:::

## Task 13 — Reporting bundle

:::task-stub{title="Deliver MVP reports for productivity, coverage, collections, recommendations, and incentives"}
Implement report outputs for:
- Rep Productivity
- Route Coverage
- Outlet Order Summary
- Collection Efficiency
- Outlet Sales Profile
- Recommended SKU Acceptance Rate
- Focus SKU Performance
- Incentive Achievement Dashboard
- Vendor-wise Sales Performance

Add vendor/date/region/rep filters.
:::

## Task 14 — Security and auth model

:::task-stub{title="Define and implement authn/authz model across mobile, middleware, and ERPNext"}
Define token model, role claims, endpoint authorization, refresh policy, and audit logging.

Cover secure file upload for collection proofs and audit photos.
:::

## Task 15 — API contracts for client teams

:::task-stub{title="Publish versioned API contracts for rep and manager clients"}
Create OpenAPI specifications and examples for all rep/manager endpoints.

Include:
- request/response schemas
- error code catalog
- pagination/filter standards
- sync conflict and retry semantics.
:::

## Task 16 — Architecture decisions (open items closure)

:::task-stub{title="Close open architecture decisions via ADRs before full-scale build"}
Create ADRs in `docs/adr/` for:
- distributor linkage model (Customer vs Supplier)
- route model (DocType vs field)
- approval flow for order conversion
- mobile UX stack choice
- recommendation engine initial approach
- incentive payout ownership model.

Each ADR should include decision, rationale, options, and migration impact.
:::
