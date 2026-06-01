#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/restore_erpnext_backup.sh" \
  --host-backup-dir "/Users/roshninaik/Projects/AAS/backup/backups" \
  --compose-file "/Users/roshninaik/Projects/AAS/erpmodule/pwd.yml" \
  --service "backend" \
  --site "aas.core.local"
