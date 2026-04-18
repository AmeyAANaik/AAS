# Ivy Sales Extension — UI Mock Flows (Client Demo)

Use this as a storyboard for Figma/PPT screens.

## 1) Rep Daily Beat Flow

```text
[Login]
   -> [Today’s Beat Plan]
      -> [Outlet List by Sequence]
         -> [Select Outlet]
            -> [Check-in]
               -> [Visit Action Hub]
                  -> (Visit Form | Book Order | Collection | Audit)
```

### Screen notes
- Top bar: rep name, date, sync status
- Cards: planned outlets, visited, pending, productive
- CTA: "Start Visit"

---

## 2) Visit + Audit Flow

```text
[Visit Action Hub]
   -> [Visit Details]
      -> [Outlet Audit]
         -> [Photo Upload]
            -> [Save Draft / Submit]
               -> [Visit Completed]
```

### Fields
- check-in/check-out timestamp, location
- stockout flag, shelf share, promo compliance
- competitor notes and remarks

---

## 3) Secondary Order + Recommendation Flow

```text
[Visit Action Hub]
   -> [Create Secondary Order]
      -> [Item Entry Grid]
      -> [Recommended SKUs Panel]
         -> [Accept / Reject Recommendation]
            -> [Order Summary]
               -> [Submit Secondary Order]
```

### UX blocks
- Left: order lines (item, qty, rate, amount)
- Right: recommendation cards with reason code
- Footer: total qty, total value, discount impact

---

## 4) Collection Flow

```text
[Visit Action Hub]
   -> [Outstanding Invoices]
      -> [Select Invoice]
         -> [Enter Collection + Mode]
            -> [Upload Proof]
               -> [Validate Outstanding]
                  -> [Submit Collection]
```

### Guardrails
- amount > 0
- amount <= outstanding
- proof attachment mandatory (configurable)

---

## 5) Manager Control Tower Flow

```text
[Manager Dashboard]
   -> [Route Coverage]
   -> [Rep Productivity]
   -> [Collections Efficiency]
   -> [Recommendation Acceptance]
   -> [Incentive Summary]
```

### KPI cards
- planned vs completed visits
- productive visits %
- secondary order value
- collection achieved %
- recommendation acceptance rate
- incentive earned vs approved

---

## 6) Sync & Offline State Flow

```text
[Offline Action Queue]
   -> [Retry Sync]
      -> [Conflict Resolver]
         -> [Resolved State]
            -> [Audit Log Updated]
```

### UX indicators
- pending sync count
- last successful sync timestamp
- per-record status chips (Pending/Failed/Resolved)


## 7) Monthly Reports Flow

```text
[Monthly Reports Dashboard]
   -> [Select Month / Region / Persona]
      -> [Sales + Collection KPIs]
         -> [MoM Trend]
            -> [Export PDF/CSV]
```

## 8) P&L Flow

```text
[P&L Analytics]
   -> [Revenue]
   -> [COGS]
   -> [Gross Profit]
   -> [Operating/Distribution Cost]
   -> [Net Profit]
      -> [Channel-wise Drilldown]
```

## 9) Recommendation Studio Flow

```text
[Suggestion Engine Panel]
   -> [Apply Segment/Type Filter]
      -> [Generate Suggestions]
         -> [Show Reason + Confidence + Expected Uplift]
            -> [Accept/Reject Tracking]
               -> [Manager Insight Dashboard]
```
