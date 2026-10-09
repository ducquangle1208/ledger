#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"
lock_deployment
previous="$DEPLOY_STATE_DIR/previous.env"
[ -f "$previous" ] || { echo 'No previous release recorded' >&2; exit 2; }
[ "${ROLLBACK_COMPATIBLE:-no}" = yes ] || { echo 'Confirm the previous application supports the current schema with ROLLBACK_COMPATIBLE=yes' >&2; exit 3; }
previous_backend=$(sed -n 's/^BACKEND_IMAGE=//p' "$previous")
format=$(docker image inspect --format '{{index .Config.Labels "org.miniledger.audit-format"}}' "$previous_backend")
[ "$format" = v1 ] || { echo 'Refusing rollback to an image without the v1 audit writer' >&2; exit 3; }
capture_release "$DEPLOY_STATE_DIR/rollback-from.env"
sh "$ROOT/scripts/backup.sh"
unset BACKEND_IMAGE FRONTEND_IMAGE
RELEASE_FILE=$previous
apply_release
cp "$previous" "$DEPLOY_STATE_DIR/current.env.partial"
mv "$DEPLOY_STATE_DIR/current.env.partial" "$DEPLOY_STATE_DIR/current.env"
mv "$DEPLOY_STATE_DIR/rollback-from.env" "$previous"
echo 'Application images rolled back; database schema retained.'
