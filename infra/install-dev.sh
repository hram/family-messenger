#!/usr/bin/env bash
set -euo pipefail

export INSTALL_ROOT="${INSTALL_ROOT:-/opt/family-messenger-dev}"
export CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger-dev}"
export SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-dev-backend}"
export APP_USER="${APP_USER:-family-dev}"
export APP_GROUP="${APP_GROUP:-family-dev}"
export PUBLIC_PORT="${PUBLIC_PORT:-9080}"
export BACKEND_PORT="${BACKEND_PORT:-9081}"
export DB_PORT="${DB_PORT:-55432}"
export DB_NAME="${DB_NAME:-family_messenger_dev}"
export DB_USER="${DB_USER:-family_dev}"
export POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-family-messenger-dev-postgres}"
export POSTGRES_VOLUME_NAME="${POSTGRES_VOLUME_NAME:-family_messenger_dev_postgres_data}"
export CADDY_SITE_NAME="${CADDY_SITE_NAME:-family-messenger-dev}"

REPO_OWNER="${REPO_OWNER:-hram}"
REPO_NAME="${REPO_NAME:-family-messenger}"

curl -fsSL "https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/main/infra/install.sh" | bash "$@"
