#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"
[ "$#" -eq 1 ] || { echo 'Usage: RESTORE_DB=... CONFIRM_RESTORE=yes sh scripts/restore.sh <file.dump.gz>' >&2; exit 2; }
: "${RESTORE_DB:?Set the explicit target database name}"
case "$RESTORE_DB" in ''|[0-9]*|*[!a-zA-Z0-9_]*) echo 'Invalid target database name' >&2; exit 2;; esac
[ "${CONFIRM_RESTORE:-no}" = yes ] || { echo 'Set CONFIRM_RESTORE=yes to replace the target database contents' >&2; exit 3; }
backup=$1
[ -f "$backup" ] && [ -f "$backup.sha256" ] || { echo 'Backup or checksum missing' >&2; exit 2; }
expected=$(awk 'NR == 1 { print $1 }' "$backup.sha256")
actual=$(sha256sum "$backup" | awk '{ print $1 }')
[ "${#expected}" -eq 64 ] && [ "$actual" = "$expected" ] || { echo 'Backup checksum mismatch' >&2; exit 2; }
gzip -t "$backup"
umask 077
temporary=$(mktemp)
trap 'rm -f "$temporary"' EXIT
trap 'exit 130' INT TERM
gzip -dc "$backup" > "$temporary"
compose exec -T postgres pg_restore --list < "$temporary" > /dev/null
primary=$(compose exec -T postgres printenv POSTGRES_DB | tr -d '\r')
if [ "$RESTORE_DB" = "$primary" ]; then
    [ "${ALLOW_PRIMARY_RESTORE:-no}" = yes ] || { echo 'Primary database restore requires ALLOW_PRIMARY_RESTORE=yes' >&2; exit 3; }
    [ -z "$(compose ps --status running -q backend)" ] || { echo 'Stop the backend before restoring its primary database' >&2; exit 3; }
fi
exists=$(compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -Atc "$1"' sh "SELECT 1 FROM pg_database WHERE datname = '$RESTORE_DB'" | tr -d '\r')
if [ "$exists" != 1 ]; then
    compose exec -T postgres sh -c 'exec createdb -U "$POSTGRES_USER" "$1"' sh "$RESTORE_DB"
fi
# Any SQL failure rolls back the entire restore; schema is restored with the data.
compose exec -T postgres sh -c 'exec pg_restore --clean --if-exists --no-owner --single-transaction --exit-on-error --username "$POSTGRES_USER" --dbname "$1"' sh "$RESTORE_DB" < "$temporary"
printf 'Restore completed: %s\n' "$RESTORE_DB"
