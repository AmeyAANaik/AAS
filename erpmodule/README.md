# ERPNext Module - AAS Core

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
