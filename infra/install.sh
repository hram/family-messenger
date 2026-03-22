#!/usr/bin/env bash
set -euo pipefail

REPO_OWNER="${REPO_OWNER:-hram}"
REPO_NAME="${REPO_NAME:-family-messenger}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/family-messenger}"
CONFIG_ROOT="${CONFIG_ROOT:-/etc/family-messenger}"
SYSTEMD_UNIT_NAME="${SYSTEMD_UNIT_NAME:-family-messenger-backend}"
APP_USER="${APP_USER:-family}"
APP_GROUP="${APP_GROUP:-family}"
SERVER_PORT="${SERVER_PORT:-8080}"
BACKEND_PORT="${BACKEND_PORT:-8081}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-family_messenger}"
DB_USER="${DB_USER:-family}"
AUTH_TOKEN_TTL_HOURS="${AUTH_TOKEN_TTL_HOURS:-720}"
AUTH_RATE_LIMIT_ENABLED="${AUTH_RATE_LIMIT_ENABLED:-true}"
AUTH_RATE_LIMIT_WINDOW_SECONDS="${AUTH_RATE_LIMIT_WINDOW_SECONDS:-60}"
AUTH_RATE_LIMIT_MAX_REQUESTS="${AUTH_RATE_LIMIT_MAX_REQUESTS:-10}"
RELEASE_VERSION="${RELEASE_VERSION:-}"

POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-family-messenger-postgres}"
POSTGRES_VOLUME_NAME="${POSTGRES_VOLUME_NAME:-family_messenger_postgres_data}"
WEB_ASSET_NAME="${WEB_ASSET_NAME:-family-messenger-web.tar.gz}"

log() {
  printf '[family-messenger] %s\n' "$*"
}

fail() {
  printf '[family-messenger] ERROR: %s\n' "$*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  need_cmd sudo
  SUDO="sudo"
fi

need_cmd curl
need_cmd awk
need_cmd sed

detect_release_version() {
  if [[ -n "${RELEASE_VERSION}" ]]; then
    printf '%s\n' "${RELEASE_VERSION}"
    return
  fi

  local latest
  latest="$(curl -fsSL "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest" | sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
  [[ -n "${latest}" ]] || fail "Could not detect latest GitHub release tag"
  printf '%s\n' "${latest}"
}

detect_public_ip() {
  local ip
  ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  if [[ -z "${ip}" ]]; then
    ip="$(ip route get 1.1.1.1 2>/dev/null | awk '/src/ {for (i=1; i<=NF; i++) if ($i == "src") { print $(i+1); exit }}')"
  fi
  printf '%s\n' "${ip:-localhost}"
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker and Compose plugin already installed"
    return
  fi

  log "Installing Docker Engine and Compose plugin"
  ${SUDO} apt-get update
  ${SUDO} apt-get install -y ca-certificates curl gnupg
  ${SUDO} install -m 0755 -d /etc/apt/keyrings
  if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | ${SUDO} gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    ${SUDO} chmod a+r /etc/apt/keyrings/docker.gpg
  fi
  . /etc/os-release
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" | \
    ${SUDO} tee /etc/apt/sources.list.d/docker.list >/dev/null
  ${SUDO} apt-get update
  ${SUDO} apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  ${SUDO} systemctl enable --now docker
}

install_java() {
  if command -v java >/dev/null 2>&1; then
    log "Java runtime already installed"
    return
  fi

  log "Installing OpenJDK 17 runtime"
  ${SUDO} apt-get update
  ${SUDO} apt-get install -y openjdk-17-jre-headless
}

install_nginx() {
  if command -v nginx >/dev/null 2>&1; then
    log "nginx already installed"
    return
  fi

  log "Installing nginx"
  ${SUDO} apt-get update
  ${SUDO} apt-get install -y nginx
  ${SUDO} systemctl enable --now nginx
}

ensure_user_and_dirs() {
  if ! getent group "${APP_GROUP}" >/dev/null 2>&1; then
    ${SUDO} groupadd --system "${APP_GROUP}"
  fi
  if ! id -u "${APP_USER}" >/dev/null 2>&1; then
    ${SUDO} useradd --system --home "${INSTALL_ROOT}" --gid "${APP_GROUP}" --shell /usr/sbin/nologin "${APP_USER}"
  fi

  ${SUDO} mkdir -p "${INSTALL_ROOT}/runtime"
  ${SUDO} mkdir -p "${INSTALL_ROOT}/postgres"
  ${SUDO} mkdir -p "${INSTALL_ROOT}/web"
  ${SUDO} mkdir -p "${CONFIG_ROOT}"
  ${SUDO} chown -R "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}"
}

generate_password() {
  python3 - <<'PY'
import secrets
import string

alphabet = string.ascii_letters + string.digits
print(''.join(secrets.choice(alphabet) for _ in range(32)), end='')
PY
}

write_schema() {
  local version="$1"
  local schema_url="https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/${version}/backend/schema.sql"
  log "Downloading schema.sql for ${version}"
  curl -fsSL "${schema_url}" | ${SUDO} tee "${INSTALL_ROOT}/postgres/schema.sql" >/dev/null
  ${SUDO} chown "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/postgres/schema.sql"
}

write_postgres_compose() {
  local db_password="$1"
  cat <<EOF | ${SUDO} tee "${INSTALL_ROOT}/postgres/docker-compose.yml" >/dev/null
services:
  postgres:
    image: ${POSTGRES_IMAGE}
    container_name: ${POSTGRES_CONTAINER_NAME}
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${db_password}
    ports:
      - "127.0.0.1:${DB_PORT}:5432"
    volumes:
      - ${POSTGRES_VOLUME_NAME}:/var/lib/postgresql/data
      - ${INSTALL_ROOT}/postgres/schema.sql:/docker-entrypoint-initdb.d/01_schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 10

volumes:
  ${POSTGRES_VOLUME_NAME}:
EOF
}

wait_for_postgres() {
  log "Waiting for PostgreSQL to become healthy"
  local i
  for i in $(seq 1 60); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${POSTGRES_CONTAINER_NAME}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" ]]; then
      return
    fi
    sleep 2
  done
  fail "PostgreSQL container did not become healthy in time"
}

download_jar() {
  local version="$1"
  local jar_url="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${version}/family-messenger-backend-all.jar"
  log "Downloading backend fat jar ${version}"
  curl -fL "${jar_url}" -o /tmp/family-messenger-backend-all.jar
  ${SUDO} mv /tmp/family-messenger-backend-all.jar "${INSTALL_ROOT}/family-messenger-backend-all.jar"
  ${SUDO} chown "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/family-messenger-backend-all.jar"
  ${SUDO} chmod 0644 "${INSTALL_ROOT}/family-messenger-backend-all.jar"
}

download_web() {
  local version="$1"
  local web_url="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${version}/${WEB_ASSET_NAME}"
  log "Downloading web bundle ${version}"
  curl -fL "${web_url}" -o /tmp/family-messenger-web.tar.gz
  ${SUDO} rm -rf "${INSTALL_ROOT}/web"
  ${SUDO} mkdir -p "${INSTALL_ROOT}/web"
  ${SUDO} tar -xzf /tmp/family-messenger-web.tar.gz -C "${INSTALL_ROOT}/web"
  ${SUDO} chown -R "${APP_USER}:${APP_GROUP}" "${INSTALL_ROOT}/web"
  rm -f /tmp/family-messenger-web.tar.gz
}

write_backend_env() {
  local db_password="$1"
  cat <<EOF | ${SUDO} tee "${CONFIG_ROOT}/backend.env" >/dev/null
SERVER_PORT=${BACKEND_PORT}
DB_HOST=127.0.0.1
DB_PORT=${DB_PORT}
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${db_password}
DB_BOOTSTRAP_SCHEMA=false
DB_SEED_ON_START=false
AUTH_TOKEN_TTL_HOURS=${AUTH_TOKEN_TTL_HOURS}
AUTH_RATE_LIMIT_ENABLED=${AUTH_RATE_LIMIT_ENABLED}
AUTH_RATE_LIMIT_WINDOW_SECONDS=${AUTH_RATE_LIMIT_WINDOW_SECONDS}
AUTH_RATE_LIMIT_MAX_REQUESTS=${AUTH_RATE_LIMIT_MAX_REQUESTS}
EOF
  ${SUDO} chmod 0600 "${CONFIG_ROOT}/backend.env"
}

write_nginx_config() {
  cat <<EOF | ${SUDO} tee /etc/nginx/sites-available/family-messenger >/dev/null
server {
    listen ${SERVER_PORT};
    server_name _;

    root ${INSTALL_ROOT}/web;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location = /openapi.json {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_set_header Host \$host;
    }

    location /swagger-ui/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_set_header Host \$host;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
  ${SUDO} ln -sf /etc/nginx/sites-available/family-messenger /etc/nginx/sites-enabled/family-messenger
  ${SUDO} rm -f /etc/nginx/sites-enabled/default
}

write_install_state() {
  local version="$1"
  local public_ip="$2"
  cat <<EOF | ${SUDO} tee "${CONFIG_ROOT}/install.env" >/dev/null
RELEASE_VERSION=${version}
REPO_OWNER=${REPO_OWNER}
REPO_NAME=${REPO_NAME}
INSTALL_ROOT=${INSTALL_ROOT}
CONFIG_ROOT=${CONFIG_ROOT}
SYSTEMD_UNIT_NAME=${SYSTEMD_UNIT_NAME}
APP_USER=${APP_USER}
APP_GROUP=${APP_GROUP}
SERVER_PORT=${SERVER_PORT}
BACKEND_PORT=${BACKEND_PORT}
DB_PORT=${DB_PORT}
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
POSTGRES_CONTAINER_NAME=${POSTGRES_CONTAINER_NAME}
POSTGRES_VOLUME_NAME=${POSTGRES_VOLUME_NAME}
POSTGRES_IMAGE=${POSTGRES_IMAGE}
WEB_ASSET_NAME=${WEB_ASSET_NAME}
APP_BASE_URL=http://${public_ip}:${SERVER_PORT}
EOF
  ${SUDO} chmod 0600 "${CONFIG_ROOT}/install.env"
}

write_systemd_unit() {
  cat <<EOF | ${SUDO} tee "/etc/systemd/system/${SYSTEMD_UNIT_NAME}.service" >/dev/null
[Unit]
Description=Family Messenger Backend
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_GROUP}
WorkingDirectory=${INSTALL_ROOT}
EnvironmentFile=${CONFIG_ROOT}/backend.env
ExecStart=/usr/bin/java -Dktor.deployment.port=\${SERVER_PORT} -jar ${INSTALL_ROOT}/family-messenger-backend-all.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF
}

maybe_open_ufw() {
  if ! command -v ufw >/dev/null 2>&1; then
    return
  fi
  local status
  status="$(${SUDO} ufw status 2>/dev/null | head -n1 || true)"
  if [[ "${status}" == "Status: active" ]]; then
    log "Opening TCP port ${SERVER_PORT} in UFW"
    ${SUDO} ufw allow "${SERVER_PORT}/tcp" >/dev/null
  fi
}

main() {
  local version
  local public_ip
  local db_password

  version="$(detect_release_version)"
  public_ip="$(detect_public_ip)"
  db_password="${DB_PASSWORD:-$(generate_password)}"

  log "Installing Family Messenger ${version}"
  install_docker
  install_java
  install_nginx
  ensure_user_and_dirs
  write_schema "${version}"
  write_postgres_compose "${db_password}"
  write_backend_env "${db_password}"
  write_systemd_unit
  write_nginx_config
  download_jar "${version}"
  download_web "${version}"

  log "Starting PostgreSQL"
  ${SUDO} docker compose -f "${INSTALL_ROOT}/postgres/docker-compose.yml" up -d
  wait_for_postgres

  log "Starting backend service"
  ${SUDO} systemctl daemon-reload
  ${SUDO} systemctl enable "${SYSTEMD_UNIT_NAME}"
  ${SUDO} systemctl restart "${SYSTEMD_UNIT_NAME}"
  ${SUDO} nginx -t
  ${SUDO} systemctl restart nginx
  maybe_open_ufw
  write_install_state "${version}" "${public_ip}"

  log "Installation complete"
  printf '\n'
  printf 'Open in browser: http://%s:%s\n' "${public_ip}" "${SERVER_PORT}"
  printf 'Backend health:   http://%s:%s/api/health\n' "${public_ip}" "${SERVER_PORT}"
  printf '\n'
  printf 'Saved files:\n'
  printf '  - %s/backend.env\n' "${CONFIG_ROOT}"
  printf '  - %s/install.env\n' "${CONFIG_ROOT}"
  printf '  - %s/postgres/docker-compose.yml\n' "${INSTALL_ROOT}"
}

main "$@"
