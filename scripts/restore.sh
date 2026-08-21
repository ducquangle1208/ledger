#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 backups/mini-ledger-<timestamp>.dump.gz" >&2
  exit 2
fi
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

backup=$1
[ -f "$backup" ] || { echo "Backup not found: $backup" >&2; exit 2; }

echo "Restoring replaces all data in $POSTGRES_DB. Set CONFIRM_RESTORE=yes to continue."
[ "${CONFIRM_RESTORE:-no}" = "yes" ] || exit 3

gzip -dc "$backup" | docker compose -f compose.prod.yaml exec -T postgres \
  pg_restore --clean --if-exists --no-owner --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
