#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"
umask 077
mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/mini-ledger-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump.gz"
temporary=$(mktemp "$BACKUP_DIR/.dump.XXXXXX")
trap 'rm -f "$temporary" "$file.partial"' EXIT
trap 'exit 130' INT TERM

# No pipeline: a failed pg_dump must not be hidden by a successful compressor.
compose exec -T postgres sh -c 'exec pg_dump --format=custom --no-owner --username "$POSTGRES_USER" "$POSTGRES_DB"' > "$temporary"
gzip -c "$temporary" > "$file.partial"
gzip -t "$file.partial"
mv "$file.partial" "$file"
(cd "$BACKUP_DIR" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
printf 'Backup written to %s\n' "$file"
