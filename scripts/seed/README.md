# Seed Scripts (Tab Wise)

Each script in this folder maps to a UI tab. Start with Items.

## Items

```bash
MW_USERNAME=Administrator MW_PASSWORD=admin npm run seed:items
```

## Backup (before ERP wipe)

Exports current ERPNext `Item` master data (including AAS margin fields) and the referenced `Item Group` tree to snapshots under `scripts/seed/`.

```bash
ERP_USERNAME=Administrator ERP_PASSWORD=admin node scripts/seed/backup-items-and-groups.mjs
```
