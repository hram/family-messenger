#!/usr/bin/env bash
set -euo pipefail

CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger}"
SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-backend}"
WEB_ASSET_NAME="${WEB_ASSET_NAME:-family-messenger-web.tar.gz}"
REQUESTED_RELEASE_VERSION="${RELEASE_VERSION:-}"

log() {
  printf '[family-messenger] %s\n' "$*"
}

fail() {
  printf '[family-messenger] ERROR: %s\n' "$*" >&2
  exit 1
}

wait_for_public_health() {
  local health_url="http://127.0.0.1:${PUBLIC_PORT}/api/health"
  log "Waiting for public health endpoint"
  local i
  for i in $(seq 1 60); do
    if curl -fsS "${health_url}" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  fail "Public health endpoint did not become ready in time: ${health_url}"
}

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  SUDO="sudo"
fi

[[ -f "${CONFIG_ROOT}/install.env" ]] || fail "Install metadata not found at ${CONFIG_ROOT}/install.env"
# shellcheck disable=SC1090
source "${CONFIG_ROOT}/install.env"

POSTGRES_COMPOSE_PROJECT_NAME="${POSTGRES_COMPOSE_PROJECT_NAME:-family-messenger}"

RELEASE_VERSION="${REQUESTED_RELEASE_VERSION}"
if [[ -z "${RELEASE_VERSION}" ]]; then
  RELEASE_VERSION="$(curl -fsSL "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest" | sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
fi
[[ -n "${RELEASE_VERSION}" ]] || fail "Could not detect release version"

JAR_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${RELEASE_VERSION}/family-messenger-backend-all.jar"
WEB_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${RELEASE_VERSION}/${WEB_ASSET_NAME}"

log "Downloading ${RELEASE_VERSION}"
curl -fL "${JAR_URL}" -o /tmp/family-messenger-backend-all.jar
${SUDO} mv /tmp/family-messenger-backend-all.jar "${INSTALL_ROOT}/family-messenger-backend-all.jar"
${SUDO} chown "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/family-messenger-backend-all.jar"
${SUDO} chmod 0644 "${INSTALL_ROOT}/family-messenger-backend-all.jar"

curl -fL "${WEB_URL}" -o /tmp/family-messenger-web.tar.gz
${SUDO} rm -rf "${INSTALL_ROOT}/web"
${SUDO} mkdir -p "${INSTALL_ROOT}/web"
${SUDO} tar -xzf /tmp/family-messenger-web.tar.gz -C "${INSTALL_ROOT}/web"
${SUDO} find "${INSTALL_ROOT}/web" -type d -exec chmod 0755 {} \;
${SUDO} find "${INSTALL_ROOT}/web" -type f -exec chmod 0644 {} \;
if command -v gzip >/dev/null 2>&1; then
  ${SUDO} find "${INSTALL_ROOT}/web" -type f \( -name '*.js' -o -name '*.mjs' -o -name '*.css' -o -name '*.wasm' -o -name '*.map' \) \
    -exec gzip -kf -9 {} \;
  ${SUDO} find "${INSTALL_ROOT}/web" -type f \( -name '*.js.gz' -o -name '*.mjs.gz' -o -name '*.css.gz' -o -name '*.wasm.gz' -o -name '*.map.gz' \) \
    -exec chmod 0644 {} \;
fi
${SUDO} chown -R "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/web"
rm -f /tmp/family-messenger-web.tar.gz

${SUDO} sed -i "s/^RELEASE_VERSION=.*/RELEASE_VERSION=${RELEASE_VERSION}/" "${CONFIG_ROOT}/install.env"
if [[ -f "${INSTALL_ROOT}/postgres/docker-compose.yml" ]]; then
  ${SUDO} docker compose -p "${POSTGRES_COMPOSE_PROJECT_NAME}" -f "${INSTALL_ROOT}/postgres/docker-compose.yml" up -d
fi
${SUDO} systemctl restart "${SYSTEMD_UNIT_NAME}"
${SUDO} systemctl restart caddy
wait_for_public_health

log "Updated backend to ${RELEASE_VERSION}"
