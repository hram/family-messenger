#!/usr/bin/env bash
set -euo pipefail

CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger}"
SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-backend}"

log() {
  printf '[family-messenger] %s\n' "$*"
}

fail() {
  printf '[family-messenger] ERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  SUDO="sudo"
fi

[[ -f "${CONFIG_ROOT}/install.env" ]] || fail "Install metadata not found at ${CONFIG_ROOT}/install.env"
# shellcheck disable=SC1090
source "${CONFIG_ROOT}/install.env"

RELEASE_VERSION="${RELEASE_VERSION:-}"
if [[ -z "${RELEASE_VERSION}" ]]; then
  RELEASE_VERSION="$(curl -fsSL "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest" | sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
fi
[[ -n "${RELEASE_VERSION}" ]] || fail "Could not detect release version"

JAR_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${RELEASE_VERSION}/family-messenger-backend-all.jar"

log "Downloading ${RELEASE_VERSION}"
curl -fL "${JAR_URL}" -o /tmp/family-messenger-backend-all.jar
${SUDO} mv /tmp/family-messenger-backend-all.jar "${INSTALL_ROOT}/family-messenger-backend-all.jar"
${SUDO} chown "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/family-messenger-backend-all.jar"
${SUDO} chmod 0644 "${INSTALL_ROOT}/family-messenger-backend-all.jar"

${SUDO} sed -i "s/^RELEASE_VERSION=.*/RELEASE_VERSION=${RELEASE_VERSION}/" "${CONFIG_ROOT}/install.env"
${SUDO} systemctl restart "${SYSTEMD_UNIT_NAME}"

log "Updated backend to ${RELEASE_VERSION}"
