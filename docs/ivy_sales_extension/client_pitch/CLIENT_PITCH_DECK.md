# Ivy Sales Extension — Client Pitch Deck (PPT-Ready)

Date: 2026-04-18  
Audience: Client leadership, sales ops, IT, finance

---

## Slide 1 — Title
**Ivy Sales Extension on ERPNext**  
Configurable field-sales execution platform for multi-vendor growth

Subtitle: Built on ERPNext + Spring Boot middleware + mobile-ready workflows

---

## Slide 2 — Problem Statement
- Field sales data is fragmented across visits, orders, collections, and reporting.
- Outlet-level visibility and recommendation support are limited.
- Incentive tracking is often manual and delayed.
- Vendor-specific implementations increase cost and reduce reusability.

---

## Slide 3 — Vision
**One configurable platform** for:
- Beat planning
- Visit execution
- Secondary order capture
- Collection orchestration
- Recommendation-led selling
- Incentive intelligence

---

## Slide 4 — Architecture
1. **ERPNext Core**: transactional backbone (Customer, Item, Sales Order, Invoice, Payment Entry)
2. **Spring Boot Middleware**: orchestration, APIs, validation, sync, rule engines
3. **Custom Frappe App (`ivy_sales_extension`)**: extension DocTypes and configurable rules
4. **Mobile/PWA Layer**: rep execution UI
5. **AI Extensions (future)**: route optimization, image recognition, advanced recommendations

---

## Slide 5 — Why this model works
- Upgrade-safe: ERPNext core untouched
- Config-first: vendor/brand/channel/geography driven
- Modular: recommendation and incentive engines pluggable
- Scalable: middleware supports async/event integration and offline sync

---

## Slide 6 — Core user journeys
### Manager Journey
1. Create beat plan
2. Assign rep
3. Sequence outlets
4. Publish

### Rep Journey
1. Fetch today’s beat
2. Check-in
3. Capture visit + audit
4. Book order
5. Capture collection
6. Check-out

---

## Slide 7 — UI Flow: Rep Daily Beat
Show mock sequence:
- Today’s Plan screen
- Outlet list with visit status
- Check-in geo stamp
- Quick action cards: Visit, Order, Collection

(Refer: `UI_MOCK_FLOWS.md` Section 1)

---

## Slide 8 — UI Flow: Visit + Audit
Show mock sequence:
- Visit details form
- Shelf/audit capture
- Competitor/stockout fields
- Photo evidence upload

(Refer: `UI_MOCK_FLOWS.md` Section 2)

---

## Slide 9 — UI Flow: Secondary Order + Recommendations
Show mock sequence:
- Order entry grid
- Suggested SKUs with reason codes
- Accepted vs rejected recommendations
- Live order value update

(Refer: `UI_MOCK_FLOWS.md` Section 3)

---

## Slide 10 — UI Flow: Collections
Show mock sequence:
- Outstanding invoice list
- Collection amount validation
- Proof upload
- Submission status

(Refer: `UI_MOCK_FLOWS.md` Section 4)

---

## Slide 11 — UI Flow: Manager Control Tower
Show mock sequence:
- Route coverage
- Rep productivity
- Collection efficiency
- Recommendation acceptance KPI
- Incentive summary

(Refer: `UI_MOCK_FLOWS.md` Section 5)

---

## Slide 12 — Data model highlights
Key extension DocTypes:
- Outlet, Beat Plan, Sales Visit, Outlet Audit
- Secondary Order, Collection Entry, Van Stock
- Recommendation Rule, Outlet Sales Profile
- Incentive Plan, Incentive Slab, Incentive Ledger

---

## Slide 13 — Recommendation engine roadmap
- **Phase 1**: rule-based
- **Phase 2**: heuristic scoring
- **Phase 3**: ML/LLM-assisted

Output includes:
- Recommended SKUs
- Reason code
- Confidence/priority
- Suggested quantity

---

## Slide 14 — Incentive engine roadmap
- Configurable by vendor/role/geography/channel
- Slab-based payout calculation
- Earned/pending/approved/paid lifecycle
- Dashboard-ready KPI and payout visibility

---

## Slide 15 — API-first integration model
Rep APIs, Manager APIs, Sync APIs exposed via middleware.
ERPNext used as internal transactional target.

Benefits:
- consistent API contract
- auditability
- controlled business validation
- mobile/PWA readiness

---

## Slide 16 — Implementation plan (MVP)
1. Config masters
2. Outlet + Beat Plan
3. Sales Visit + Secondary Order
4. Collection Entry
5. Recommendation (rule-based)
6. Incentive Plan + Ledger
7. Secondary Order → Sales Order conversion
8. Dashboards and reports

---

## Slide 17 — Delivery governance
- Sprint cadence with demo checkpoints
- Joint UAT with client sales operations
- Role-based rollout (pilot region first)
- Data migration and master cleanup plan

---

## Slide 18 — Expected outcomes
- Higher rep productivity
- Better route execution discipline
- Increased order value via recommendations
- Faster collection cycles
- Transparent incentive management

---

## Slide 19 — Open decisions to finalize
- Distributor mapping model
- Route model type
- Order conversion approvals
- Mobile UX stack decision
- Recommendation initial strategy
- Incentive payout ownership

---

## Slide 20 — Next steps
- Approve architecture and MVP scope
- Freeze API contract v1
- Start sprint 0 setup
- Begin pilot rollout planning
