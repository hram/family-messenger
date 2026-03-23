#!/usr/bin/env bash
set -euo pipefail

CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger}"
SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-backend}"

log() {
  printf '[family-messenger] %s\n' "$*"
}

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  SUDO="sudo"
fi

if [[ -f "${CONFIG_ROOT}/install.env" ]]; then
  # shellcheck disable=SC1090
  source "${CONFIG_ROOT}/install.env"
else
  INSTALL_ROOT="${INSTALL_ROOT:-/opt/family-messenger}"
  POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-family-messenger-postgres}"
  POSTGRES_COMPOSE_PROJECT_NAME="${POSTGRES_COMPOSE_PROJECT_NAME:-family-messenger}"
  CADDY_SITE_FILE="${CADDY_SITE_FILE:-/etc/caddy/sites-enabled/family-messenger.caddy}"
fi

POSTGRES_COMPOSE_PROJECT_NAME="${POSTGRES_COMPOSE_PROJECT_NAME:-family-messenger}"

log "Stopping backend service"
${SUDO} systemctl disable --now "${SYSTEMD_UNIT_NAME}" >/dev/null 2>&1 || true
${SUDO} rm -f "/etc/systemd/system/${SYSTEMD_UNIT_NAME}.service"
${SUDO} systemctl daemon-reload
${SUDO} rm -f "${CADDY_SITE_FILE}"
${SUDO} systemctl restart caddy >/dev/null 2>&1 || true

if [[ -f "${INSTALL_ROOT}/postgres/docker-compose.yml" ]]; then
  log "Stopping PostgreSQL container"
  ${SUDO} docker compose -p "${POSTGRES_COMPOSE_PROJECT_NAME}" -f "${INSTALL_ROOT}/postgres/docker-compose.yml" down -v || true
else
  ${SUDO} docker rm -f "${POSTGRES_CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

log "Removing application files"
${SUDO} rm -rf "${INSTALL_ROOT}"
${SUDO} rm -rf "${CONFIG_ROOT}"

log "Family Messenger removed"
