#!/usr/bin/env sh
set -eu

compose='docker compose --env-file .env -f compose.prod.yaml'

./scripts/backup.sh
$compose pull backend frontend-assets caddy postgres redis
$compose up -d postgres redis
$compose up -d --wait backend
$compose up -d --force-recreate frontend-assets
$compose up -d --wait caddy
$compose ps
