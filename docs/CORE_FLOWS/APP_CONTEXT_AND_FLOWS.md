# AAS App Context + Core Flows (Text-Only)

Date: 2026-04-18

## App Context (as implemented)

- **Architecture:** Angular UI (`ui/`) calls Spring Boot middleware (`mw/`), and middleware is the only integration layer to ERPNext (`erpmodule/`).
- **System of record:** ERPNext doctypes are used directly (`Sales Order`, `Purchase Invoice`, `Sales Invoice`, `Payment Entry`) with AAS custom fields for workflow tracking.
- **Primary workflow state field:** `Sales Order.aas_status` driven by middleware state checks.

## Flow 1: Order Workflow

**What it does:** End-to-end order lifecycle from creation to invoicing.

```mermaid
flowchart LR
  A[Create Order] --> B[Assign Vendor]
  B --> C[Upload Vendor PDF]
  C --> D[Capture Vendor Bill]
  D --> E[Create Sell Order]
  E --> F[Create Sales Invoice]
```

## Flow 2: Vendor Operations

**What it does:** Vendor-centric monitoring across pending work, ledger, and exceptions.

```mermaid
flowchart LR
  A[Vendor Summary] --> B[Vendor Detail]
  B --> C[Vendor Orders]
  C --> D[Vendor Ledger]
  D --> E[Exceptions and Follow-up]
  E --> F[CSV Export]
```

## Flow 3: Branch Operations

**What it does:** Branch/customer-centric view for receivables and order pipeline health.

```mermaid
flowchart LR
  A[Branch Summary] --> B[Branch Detail]
  B --> C[Branch Orders]
  C --> D[Branch Invoices]
  D --> E[Branch Ledger]
  E --> F[Collection Action]
```

## Flow 4: Billing & Invoice

**What it does:** Creates invoices, applies tax templates, and records payment evidence-backed entries.

```mermaid
flowchart LR
  A[Create Invoice] --> B[Apply GST Templates]
  B --> C[Set Due Date]
  C --> D[Issue Invoice PDF]
  D --> E[Record Payment + Evidence]
  E --> F[Update Outstanding]
```

## Flow 5: Reporting

**What it does:** Middleware-generated aggregated analytics over ERPNext orders/invoices/payments.

```mermaid
flowchart LR
  A[Select Report Type] --> B[Apply Filters]
  B --> C[Middleware Aggregates ERP Data]
  C --> D[Return KPI Table]
  D --> E[Review Metrics]
  E --> F[Export CSV]
```

## Notes

- This file intentionally avoids binary assets to stay PR-friendly in environments that block binary diffs.
- For ERPNext-alignment findings and risks, see `docs/erpnext-workflow-review.md`.
- If image export is still needed later, generate images locally from text/mermaid and attach them outside the git diff.
