#!/usr/bin/env sh
# Sourced by the operational scripts, which run with set -eu.
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
: "${DEPLOY_STATE_DIR:=$ROOT/.deploy}"
: "${BACKUP_DIR:=$ROOT/backups}"
: "${RELEASE_FILE:=$DEPLOY_STATE_DIR/current.env}"
export ENV_FILE DEPLOY_STATE_DIR BACKUP_DIR

compose() {
    if [ -n "${COMPOSE_OVERRIDE_FILE:-}" ]; then set -- -f "$COMPOSE_OVERRIDE_FILE" "$@"; fi
    set -- -f "$ROOT/compose.prod.yaml" "$@"
    # Environment variables still override values in these env files.
    if [ -f "$RELEASE_FILE" ]; then set -- --env-file "$RELEASE_FILE" "$@"; fi
    set -- --env-file "$ENV_FILE" "$@"
    docker compose "$@"
}

lock_deployment() {
    umask 077
    mkdir -p "$DEPLOY_STATE_DIR"
    mkdir "$DEPLOY_STATE_DIR/lock" 2>/dev/null || {
        echo 'Another deploy/rollback holds the deployment lock. Inspect before removing a stale lock.' >&2; exit 1;
    }
    trap 'rmdir "$DEPLOY_STATE_DIR/lock"' EXIT
    trap 'exit 130' INT TERM
}

capture_release() {
    backend_id=$(compose ps -aq backend)
    frontend_id=$(compose ps -aq frontend-assets)
    [ -n "$backend_id" ] && [ -n "$frontend_id" ] || return 1
    backend_ref=$(docker inspect --format '{{.Image}}' "$backend_id")
    frontend_ref=$(docker inspect --format '{{.Image}}' "$frontend_id")
    printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$backend_ref" "$frontend_ref" > "$1"
}

apply_release() {
    compose up -d --no-deps --wait backend
    # Wait for the asset exporter to finish and propagate its exit code.
    compose up --no-deps --force-recreate --exit-code-from frontend-assets frontend-assets
    compose up -d --no-deps --wait caddy
    if [ -z "${HEALTH_URL:-}" ]; then
        domain=$(compose exec -T caddy printenv APP_DOMAIN | tr -d '\r')
        case "$domain" in http://*|https://*) HEALTH_URL="$domain/actuator/health/readiness";;
            *) HEALTH_URL="https://$domain/actuator/health/readiness";; esac
    fi
    curl --fail --silent --show-error --retry 10 --retry-delay 2 --retry-connrefused --max-time 10 "$HEALTH_URL"
    printf '\n'
}
