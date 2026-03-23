#!/usr/bin/env bash
set -euo pipefail

export CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger-dev}"
export SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-dev-backend}"

REPO_OWNER="${REPO_OWNER:-hram}"
REPO_NAME="${REPO_NAME:-family-messenger}"

curl -fsSL "https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/main/infra/update.sh" | bash "$@"
