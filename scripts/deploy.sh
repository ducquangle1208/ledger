#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"
: "${BACKEND_IMAGE:?Set the backend release image (prefer registry digest)}"
: "${FRONTEND_IMAGE:?Set the frontend release image (prefer registry digest)}"
export BACKEND_IMAGE FRONTEND_IMAGE
lock_deployment

# Keep previous runtime image IDs even when registry tags are later moved.
if capture_release "$DEPLOY_STATE_DIR/previous.env.partial"; then
    mv "$DEPLOY_STATE_DIR/previous.env.partial" "$DEPLOY_STATE_DIR/previous.env"
fi
case "${PULL_IMAGES:-yes}" in
    yes) compose pull backend frontend-assets;;
    no) :;;
    *) echo 'PULL_IMAGES must be yes or no' >&2; exit 2;;
esac
compose up -d --wait postgres redis
sh "$ROOT/scripts/backup.sh"
apply_release
capture_release "$DEPLOY_STATE_DIR/current.env.partial"
mv "$DEPLOY_STATE_DIR/current.env.partial" "$DEPLOY_STATE_DIR/current.env"
compose ps
echo 'Deployment ready. Previous images and backup are retained; schema rollback is never automatic.'
