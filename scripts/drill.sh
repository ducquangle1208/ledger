#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"
cd "$ROOT"
umask 077
COMPOSE_PROJECT_NAME="ledger-drill-$(date -u +%Y%m%d%H%M%S)-$$"
drill="$ROOT/target/$COMPOSE_PROJECT_NAME"
mkdir -p "$drill"
export COMPOSE_PROJECT_NAME
export ENV_FILE="$drill/drill.env" DEPLOY_STATE_DIR="$drill/state" BACKUP_DIR="$drill/backups"
export COMPOSE_OVERRIDE_FILE="$ROOT/compose.drill.yaml" HEALTH_URL='http://127.0.0.1:18088/actuator/health/readiness'
RELEASE_FILE="$DEPLOY_STATE_DIR/current.env"
export PULL_IMAGES=no
node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))" > "$drill/password.txt"
chmod 0444 "$drill/password.txt"
secret_path="$drill/password.txt"
if command -v cygpath >/dev/null 2>&1; then secret_path=$(cygpath -m "$secret_path"); fi
printf 'APP_DOMAIN=localhost\nPOSTGRES_DB=mini_ledger\nPOSTGRES_USER=ledger\nPOSTGRES_PASSWORD_FILE=%s\n' "$secret_path" > "$ENV_FILE"
cleanup() {
    case "$COMPOSE_PROJECT_NAME" in ledger-drill-*) compose down --volumes --remove-orphans;; *) return 1;; esac
}
trap cleanup EXIT
trap 'exit 130' INT TERM

if [ "${DRILL_BUILD_IMAGES:-yes}" = yes ]; then
    docker build --label org.opencontainers.image.revision=drill-a -t mini-ledger-backend:ops-drill-a .
    docker build --label org.opencontainers.image.revision=drill-b -t mini-ledger-backend:ops-drill-b .
    docker build -t mini-ledger-frontend:ops-drill ./frontend
fi
export BACKEND_IMAGE=mini-ledger-backend:ops-drill-a FRONTEND_IMAGE=mini-ledger-frontend:ops-drill
sh scripts/deploy.sh
original_image=$(docker inspect --format '{{.Image}}' "$(compose ps -q backend)")
curl --fail --silent http://127.0.0.1:18088/app/transfer | grep -q 'id="root"'
BASE_URL=http://127.0.0.1:18088 node postman/verify.cjs "$drill/postman-before"

fingerprint() {
    compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$1" -At' sh "$1" < scripts/drill-fingerprint.sql
}
verify_audit() {
    MSYS2_ARG_CONV_EXCL='/app/app.jar' compose exec -T -e "DB_URL=jdbc:postgresql://postgres:5432/$1" backend \
        java -Xmx256m -jar /app/app.jar --server.port=0 --ledger.audit.verify=true
}
fingerprint mini_ledger > "$drill/before.json"
verify_audit mini_ledger > "$drill/audit-before.log"
sh scripts/backup.sh
backup=$(find "$BACKUP_DIR" -name '*.dump.gz' | sort | tail -n 1)
RESTORE_DB=mini_ledger_restore CONFIRM_RESTORE=yes sh scripts/restore.sh "$backup"
fingerprint mini_ledger_restore > "$drill/restored.json"
cmp "$drill/before.json" "$drill/restored.json"
verify_audit mini_ledger_restore > "$drill/audit-restored.log"

# Corrupt only a disposable copy; restore must fail before any database write.
cp "$backup" "$drill/corrupt.dump.gz"
cp "$backup.sha256" "$drill/corrupt.dump.gz.sha256"
printf 'corrupt' >> "$drill/corrupt.dump.gz"
if RESTORE_DB=mini_ledger_restore CONFIRM_RESTORE=yes sh scripts/restore.sh "$drill/corrupt.dump.gz" > "$drill/corrupt-restore.log" 2>&1; then
    echo 'Corrupt backup was incorrectly accepted' >&2; exit 1
fi
fingerprint mini_ledger_restore > "$drill/after-rejected-restore.json"
cmp "$drill/restored.json" "$drill/after-rejected-restore.json"

export BACKEND_IMAGE=mini-ledger-backend:ops-drill-b
sh scripts/deploy.sh
upgraded_image=$(docker inspect --format '{{.Image}}' "$(compose ps -q backend)")
[ "$original_image" != "$upgraded_image" ]
ROLLBACK_COMPATIBLE=yes sh scripts/rollback.sh
unset BACKEND_IMAGE FRONTEND_IMAGE
rolled_back_image=$(docker inspect --format '{{.Image}}' "$(compose ps -q backend)")
[ "$original_image" = "$rolled_back_image" ]
fingerprint mini_ledger > "$drill/after-rollback.json"
cmp "$drill/before.json" "$drill/after-rollback.json"
BASE_URL=http://127.0.0.1:18088 node postman/verify.cjs "$drill/postman-after"
verify_audit mini_ledger > "$drill/audit-after.log"
printf '{"passed":true,"project":"%s","originalImage":"%s","upgradeImage":"%s","rollbackImage":"%s","restoreFingerprintMatched":true,"corruptBackupRejected":true,"rollbackFingerprintMatched":true,"postmanBeforeAndAfter":true}\n' \
    "$COMPOSE_PROJECT_NAME" "$original_image" "$upgraded_image" "$rolled_back_image" > "$drill/result.json"
printf 'Drill passed; evidence directory: %s\n' "$drill"
