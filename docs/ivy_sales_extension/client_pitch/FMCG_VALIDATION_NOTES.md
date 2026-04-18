# FMCG Validation Notes for Ivy Sales UX Mock

## Is the summary direction valid for FMCG?
Yes. The current direction is aligned with standard FMCG field-sales operations where workflows are segmented by role and territory:

- Sales person: beat execution, outlet visit, order capture, collection, and product push.
- Distributor ops: order conversion, dispatch planning, fill-rate management, and collection follow-up.
- Retailer-facing lens: reorder assistance, invoice visibility, and payment context.
- Management: coverage, productivity, suggestion acceptance, and profitability.

## Required role-visibility model
A production-grade FMCG app should enforce scope-based access:

1. Sales person -> own outlets/routes only
2. ASM -> own area + subordinate sales reps
3. RSM/Region head -> all areas and subordinates in region
4. NSM/National -> national rollup with drilldowns

## Advanced capabilities recommended
- Territory heat map with target vs achievement overlays
- Hierarchy tree with subordinate KPI rollups
- Route productivity and strike rate maps
- Suggestion quality feedback loop (accept/reject reason capture)
- Role-wise P&L (territory, channel, region)
- Alerting for low fill-rate, overdue collection, and poor conversion

## Current mock status
Implemented in mock UI:
- Territory map view with role scope selector
- Subordinate visibility panel
- Monthly report view
- Recommendation studio
- P&L analytics view
