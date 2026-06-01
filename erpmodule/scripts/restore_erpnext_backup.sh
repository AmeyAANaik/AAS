#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Restore an ERPNext/Frappe backup into a local Docker Compose site.

Defaults assume:
  - compose file: ../pwd.yml (relative to this script)
  - service: backend
  - site: aas.core.local
  - container backup dir: /home/frappe/frappe-bench/sites/<site>/private/backups

Usage:
  ./restore_erpnext_backup.sh --host-backup-dir /path/to/backups

Options:
  --host-backup-dir PATH   Host directory containing backup files (required)
  --compose-file PATH      Docker Compose YAML (default: ../pwd.yml)
  --service NAME           Compose service running bench (default: backend)
  --site NAME              Frappe site name (default: aas.core.local)
  --container-dir PATH     Container directory to copy backups into
  --db FILE                Database backup filename (.sql.gz) within host-backup-dir (default: auto-pick latest *-database.sql.gz)
  --public FILE            Public files tar within host-backup-dir (default: auto-pick latest *-files.tar)
  --private FILE           Private files tar within host-backup-dir (default: auto-pick latest *-private-files.tar)
  --no-files               Restore DB only (skip public/private files)
  -h, --help               Show help

Examples:
  ./restore_erpnext_backup.sh --host-backup-dir /Users/roshninaik/Projects/AAS/backup/backups
  ./restore_erpnext_backup.sh --host-backup-dir ./backups --compose-file ../compose.yaml --site mysite.local

Note:
  You may be prompted for the MySQL root password during restore.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_COMPOSE_FILE="$SCRIPT_DIR/../pwd.yml"

HOST_BACKUP_DIR=""
COMPOSE_FILE="$DEFAULT_COMPOSE_FILE"
SERVICE="backend"
SITE="aas.core.local"
CONTAINER_DIR=""
DB_FILE=""
PUBLIC_FILE=""
PRIVATE_FILE=""
RESTORE_FILES=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host-backup-dir)
      HOST_BACKUP_DIR="${2:-}"; shift 2 ;;
    --compose-file)
      COMPOSE_FILE="${2:-}"; shift 2 ;;
    --service)
      SERVICE="${2:-}"; shift 2 ;;
    --site)
      SITE="${2:-}"; shift 2 ;;
    --container-dir)
      CONTAINER_DIR="${2:-}"; shift 2 ;;
    --db)
      DB_FILE="${2:-}"; shift 2 ;;
    --public)
      PUBLIC_FILE="${2:-}"; shift 2 ;;
    --private)
      PRIVATE_FILE="${2:-}"; shift 2 ;;
    --no-files)
      RESTORE_FILES=0; shift 1 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 2 ;;
  esac
done

if [[ -z "$HOST_BACKUP_DIR" ]]; then
  echo "Missing required: --host-backup-dir" >&2
  usage
  exit 2
fi

if [[ -z "$CONTAINER_DIR" ]]; then
  CONTAINER_DIR="/home/frappe/frappe-bench/sites/$SITE/private/backups"
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 2
fi

if [[ ! -d "$HOST_BACKUP_DIR" ]]; then
  echo "Host backup dir not found: $HOST_BACKUP_DIR" >&2
  exit 2
fi

pick_latest() {
  local pattern="$1"
  # shellcheck disable=SC2012
  ls -1t "$HOST_BACKUP_DIR"/$pattern 2>/dev/null | head -n 1 || true
}

if [[ -z "$DB_FILE" ]]; then
  DB_FILE="$(pick_latest "*-database.sql.gz")"
fi

if [[ -z "$DB_FILE" || ! -f "$DB_FILE" ]]; then
  echo "Database backup not found. Expected something like '*-database.sql.gz' in: $HOST_BACKUP_DIR" >&2
  exit 2
fi

if [[ "$RESTORE_FILES" -eq 1 ]]; then
  if [[ -z "$PUBLIC_FILE" ]]; then
    PUBLIC_FILE="$(pick_latest "*-files.tar")"
  fi
  if [[ -z "$PRIVATE_FILE" ]]; then
    PRIVATE_FILE="$(pick_latest "*-private-files.tar")"
  fi

  if [[ -z "$PUBLIC_FILE" || ! -f "$PUBLIC_FILE" ]]; then
    echo "Public files archive not found. Expected '*-files.tar' in: $HOST_BACKUP_DIR" >&2
    exit 2
  fi
  if [[ -z "$PRIVATE_FILE" || ! -f "$PRIVATE_FILE" ]]; then
    echo "Private files archive not found. Expected '*-private-files.tar' in: $HOST_BACKUP_DIR" >&2
    exit 2
  fi
fi

DB_BASENAME="$(basename "$DB_FILE")"
PUBLIC_BASENAME="$(basename "${PUBLIC_FILE:-}")"
PRIVATE_BASENAME="$(basename "${PRIVATE_FILE:-}")"

echo "Using compose file : $COMPOSE_FILE"
echo "Using service      : $SERVICE"
echo "Using site         : $SITE"
echo "Host backup dir    : $HOST_BACKUP_DIR"
echo "Container dir      : $CONTAINER_DIR"
echo "DB backup          : $DB_BASENAME"
if [[ "$RESTORE_FILES" -eq 1 ]]; then
  echo "Public files       : $PUBLIC_BASENAME"
  echo "Private files      : $PRIVATE_BASENAME"
else
  echo "Files restore       : disabled (--no-files)"
fi

set -x
docker compose -f "$COMPOSE_FILE" exec "$SERVICE" mkdir -p "$CONTAINER_DIR"
docker compose -f "$COMPOSE_FILE" cp "$HOST_BACKUP_DIR"/. "$SERVICE":"$CONTAINER_DIR"

RESTORE_CMD=(
  docker compose -f "$COMPOSE_FILE" exec "$SERVICE"
  bench --site "$SITE" --force restore "$CONTAINER_DIR/$DB_BASENAME"
)

if [[ "$RESTORE_FILES" -eq 1 ]]; then
  RESTORE_CMD+=(
    --with-public-files "$CONTAINER_DIR/$PUBLIC_BASENAME"
    --with-private-files "$CONTAINER_DIR/$PRIVATE_BASENAME"
  )
fi

"${RESTORE_CMD[@]}"
docker compose -f "$COMPOSE_FILE" exec "$SERVICE" bench --site "$SITE" migrate
set +x

echo "Restore complete."
