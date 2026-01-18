# AAS Platform - Startup Commands

#tree -L 2 -a


```
AAS/
├── erpmodule/      # ERPNext Docker stack (ERP Core)
├── middleware/     # AAS backend API (to be implemented)
├── ui/             # Custom frontend (to be implemented)
└── STARTUP_COMMANDS.md
```

## ERPNext Module (ERP Core)

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

- **URL**: http://localhost:8080
- **Default User**: `Administrator`
- **Default Password**: `admin`

### Check Status

```bash
cd erpmodule
docker compose -f pwd.yml ps
```

### View Logs

```bash
cd erpmodule
docker compose -f pwd.yml logs -f
```

## Multi-Company Setup

ERPNext is configured as a **single multi-company site**:

- **Site Name**: `aas.core.local`
- **Pattern**: One Company per hotel/restaurant
- **Benefits**: 
  - Consolidated reporting across all hotels
  - Shared item master, supplier master
  - Cross-company stock transfers
  - Single deployment to manage

### Adding a New Hotel/Restaurant Company

1. Login to ERPNext (http://localhost:8080)
2. Navigate to: **Setup > Company**
3. Click **New** and fill in:
   - Company Name (e.g., "Hotel Taj Pvt Ltd")
   - Abbr (e.g., "HTAJ")
   - Default Currency: INR
4. Setup Warehouses and Cost Centers for the company
5. Create users with company-specific permissions

## Middleware (To Be Implemented)

Placeholder for AAS backend service that will:

- Store `hotel_id` → `company_name` mapping
- Wrap ERPNext REST APIs with simplified endpoints
- Handle authentication and multi-tenant routing

```bash
cd middleware
# Commands TBD
```

## UI (To Be Implemented)

Placeholder for custom frontend:

```bash
cd ui
# Commands TBD
```

## API Documentation (Swagger UI)

- **Swagger UI**: https://animated-fiesta-r497vqg5q9qhv7q-8083.app.github.dev/swagger-ui.html
- **Bearer token (JWT)**:
  - `POST /api/auth/login` with ERPNext credentials (e.g., `Administrator` / `admin`)
  - Use `accessToken` from the response in Swagger Authorize (sends `Authorization: Bearer <token>`)

## Development Workflow

1. **Start ERPNext** (always first)
2. Configure companies and master data in ERPNext UI
3. **Start Middleware** (when implemented) to expose APIs
4. **Start UI** (when implemented) for custom UX

## Notes

- ERPNext runs on port **8080**
- Reserve port 8081+ for middleware/ui services
- All data persists in Docker volumes (survives container restart)
- For fresh install, run `docker compose -f pwd.yml down -v` to wipe volumes
