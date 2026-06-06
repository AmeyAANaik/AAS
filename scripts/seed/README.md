# Seed Scripts (Tab Wise)

Each script in this folder maps to a UI tab. Start with Items.

## Items

```bash
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:items
```

## Grocery reset + reseed

Hard-deletes current ERPNext `Grocery` items where possible, disables historically linked Grocery items that cannot be deleted, then reseeds the Grocery catalog from the normalized snapshots.

```bash
ERP_BASE_URL=http://localhost:8080 ERP_USERNAME=Administrator ERP_PASSWORD=admin node scripts/seed/reset-grocery-items.mjs
```

Dry-run:

```bash
DRY_RUN=1 ERP_BASE_URL=http://localhost:8080 ERP_USERNAME=Administrator ERP_PASSWORD=admin node scripts/seed/reset-grocery-items.mjs
```

## Backup (before ERP wipe)

Exports current ERPNext `Item` master data (including AAS margin fields) and the referenced `Item Group` tree to snapshots under `scripts/seed/`.

```bash
ERP_USERNAME=Administrator ERP_PASSWORD=admin node scripts/seed/backup-items-and-groups.mjs
```
