# Ivy Sales Extension — Figma Handoff Spec

This file defines the exact frame plan for a Figma designer.

## File name suggestion
`Ivy Sales Extension - Client Demo v1`

## Page structure
1. `00_Cover_and_Story`
2. `01_Rep_App_Flows`
3. `02_Manager_Dashboard`
4. `03_Design_System`
5. `04_Prototype_Links`

## Frame list (desktop/mobile mixed)

### A. Cover and positioning
- `A1_Title`
- `A2_Problem`
- `A3_Solution`
- `A4_Architecture`

### B. Rep app (mobile)
- `B1_Login`
- `B2_Todays_Beat`
- `B3_Outlet_List`
- `B4_Checkin`
- `B5_Visit_Form`
- `B6_Audit_Form`
- `B7_Order_With_Recommendations`
- `B8_Collections`
- `B9_Sync_Status`

### C. Manager (web)
- `C1_Dashboard`
- `C2_Route_Coverage`
- `C3_Rep_Productivity`
- `C4_Collections`
- `C5_Recommendation_Acceptance`
- `C6_Incentive_Summary`

## Component library

### Core components
- App bar
- KPI card
- Outlet card
- Recommendation card
- Item line row
- Status badge (Pending, Completed, Overdue, Synced, Failed)
- Stepper/timeline
- Drawer filter panel

### Form components
- Text input
- Number input
- Dropdown/select
- Date/time picker
- Geo-capture row
- Attachment uploader
- Submit bar

## Color/tone guidance
- Primary: operational blue
- Success: green
- Warning: amber
- Error: red
- Neutral grays for table backgrounds

## Prototype click path
1. Today’s Beat -> Outlet -> Check-in -> Visit Form -> Order -> Submit
2. Today’s Beat -> Outlet -> Collection -> Submit
3. Manager dashboard -> drilldown to route coverage and incentive summary

## Copy hints for client demo
- “Recommended SKU because this outlet bought every 14 days and is now due.”
- “Collection blocked: entered value exceeds outstanding amount.”
- “Rep achieved 92% route coverage this week.”

## Export requirement
Export selected key frames as PNG (for PPT insertion) outside git if binary-restricted.
