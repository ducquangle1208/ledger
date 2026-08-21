#!/usr/bin/env sh
set -eu

: "${BACKUP_DIR:=./backups}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

mkdir -p "$BACKUP_DIR"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
file="$BACKUP_DIR/mini-ledger-$stamp.dump"

docker compose -f compose.prod.yaml exec -T postgres \
  pg_dump --format=custom --no-owner --username "$POSTGRES_USER" "$POSTGRES_DB" > "$file"

gzip "$file"
find "$BACKUP_DIR" -type f -name 'mini-ledger-*.dump.gz' -mtime +14 -delete
printf 'Backup written to %s.gz\n' "$file"
