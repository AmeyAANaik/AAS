# AAS UI

Angular 17 frontend for the AAS platform.

## Purpose

The UI is the role-based client for:

- orders and branch-image order flow
- bills and payments
- vendors, branches, categories, and items
- stock and dashboard views

The UI talks only to the middleware through `/api` and does not call ERPNext directly.

## Local Development

Recommended workflow:

- UI only: use `npm`
- Middleware: use Docker Compose from the repo root
- ERPNext: use Docker Compose from `erpmodule/`

From the repository root:

```bash
cd /Users/roshninaik/Projects/AAS/ui
npm install
npm start
```

Default local URL:

- `http://localhost:4200`

The dev server proxies `/api` requests to the middleware using `proxy.conf.json`.

## Useful Commands

```bash
npm start
npm run build
npm test
npm run test:e2e
```

## Key Files

- `src/main.ts`
- `src/app/app.config.ts`
- `src/app/app.routes.ts`
- `proxy.conf.json`

## Notes

- Auth tokens are currently stored in browser `localStorage`.
- Middleware is expected at `http://localhost:8083` during local development.
- For full platform startup, use the root `STARTUP_COMMANDS.md`.
- UI development is the only part documented here to run through `npm`; MW and ERPNext are expected to run in Docker.
