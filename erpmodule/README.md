# ERPNext Module - AAS

## Architecture

This ERPNext module serves as the **ERP Core** for the AAS (Agri-Automation System) platform.

### Single Multi-Company Setup

- **One ERPNext Site**: `aas.core.local`
- **Multiple Companies**: Each hotel/restaurant gets its own Company within this single site
- **Consolidated Reporting**: Admin can view stock, financials, and reports across all companies
- **Strong Permissions**: Users are restricted to their company's data through role-based permissions

## Stack Components

- **Backend**: Frappe/ERPNext v15.94.3 (Python)
- **Frontend**: Nginx serving ERPNext Desk UI
- **Database**: MariaDB 10.6
- **Cache & Queue**: Redis
- **Workers**: Background job processors (short, long queues)
- **Scheduler**: Cron-like job scheduler
- **WebSocket**: Real-time updates

## Quick Start

### Start ERPNext

```bash
cd erpmodule
docker compose -f pwd.yml up -d
```

### Stop ERPNext

```bash
cd erpmodule
docker compose -f pwd.yml down
```

### Access ERPNext UI

- URL: http://localhost:8080
- Default User: `Administrator`
- Default Password: `admin`

### Invoice PDF Rendering Note

ERPNext PDF generation runs `wkhtmltopdf` inside the `backend` container. For Sales Invoice PDF downloads to work, the ERP site host must be reachable from that container.

This stack sets:

```text
host_name = http://frontend:8080
```

in `sites/common_site_config.json` via `pwd.yml`.

Why this matters:
- Browser users still open ERP at `http://localhost:8080`
- But `wkhtmltopdf` inside the backend container cannot reliably render PDFs against `http://aas.core.local`
- Using `http://frontend:8080` gives the renderer an internal Docker-network URL it can reach

If invoice PDF download starts failing with `wkhtmltopdf ... ConnectionRefusedError`, rerun the ERP stack so this config is applied:

```bash
cd erpmodule
docker compose -f pwd.yml up -d --build
```

## AAS Custom Fields (Branch Metadata)

The ERPNext site initializes AAS-specific Customer fields used by the Branch tab:

- `Customer.aas_branch_location` (Data)
- `Customer.aas_whatsapp_group_name` (Data)

These are applied automatically in `pwd.yml` during the `create-site` step. If you need to re-apply them manually, run:

```bash
cd erpmodule
docker compose -f pwd.yml run --rm create-site
```

## Multi-Tenant Usage

### Adding a New Hotel/Restaurant

1. Login to ERPNext as Administrator
2. Go to **Setup > Company**
3. Create new Company (e.g., "Hotel ABC Pvt Ltd")
4. Configure Warehouses, Cost Centers for that company
5. Create users and assign them roles with company restrictions

### User Permissions

- Users can be restricted to see only their Company's data
- Use **User Permissions** to link users to specific Companies
- Admin users can see consolidated data across all companies

## Integration with AAS

The AAS middleware will:

- Store mapping: `hotel_id` → `company_name` in ERPNext
- Call ERPNext REST API with company filters
- Provide simplified APIs to custom UIs while ERPNext handles core business logic

## File Structure

```
erpmodule/
├── pwd.yml          # Docker Compose file (official Frappe setup)
├── .env             # Environment variables
├── compose.yaml     # (old, not used - using pwd.yml)
└── README.md        # This file
```

## Next Steps

1. Configure first Company in ERPNext UI
2. Set up Items, Suppliers, Warehouses
3. Create API users for AAS middleware integration
4. Test cross-company reporting
