# Infra

Этот слой покрывает два разных сценария:

- локальная разработка через `Docker Compose`
- VPS deploy на Ubuntu 24.04 без backend build на сервере

## Быстрый путь для обычного VPS

Если цель проста: купить Ubuntu VPS и получить свой семейный мессенджер одной командой, используй именно этот путь:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/install.sh | bash
```

Что делает скрипт:

- ставит Docker Engine и Compose plugin
- ставит `openjdk-17-jre-headless`
- ставит `nginx`
- скачивает `family-messenger-backend-all.jar` из GitHub Releases
- скачивает готовый web bundle из GitHub Releases
- поднимает Postgres в Docker
- создаёт backend service через `systemd`
- публикует web-клиент через `nginx` на `http://<server-ip>:8080`
- открывает итоговый URL вида `http://<server-ip>:8080`

После завершения:

- открой в браузере `http://<server-ip>:8080`
- пройди setup wizard
- задай master password
- добавь родителей и детей

Обновление:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/update.sh | bash
```

Удаление:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/uninstall.sh | bash
```

Если нужно поставить конкретную версию release:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/install.sh | RELEASE_VERSION=v0.1.0 bash
```

Всё остальное ниже нужно только если хочется понять, что именно делает установщик, или если нужен ручной fallback.

Критичное ограничение для VPS deploy:

- VPS не должен собирать backend из исходников
- VPS не должен тянуть Gradle distribution и build-time base images во время deploy
- на сервер должен приезжать готовый backend дистрибутив
- в этом проекте целевой дистрибутив для VPS: готовый `family-messenger-backend-all.jar` из GitHub Releases

Причина практическая:

- source-build на сервере делает deploy медленным и хрупким
- deploy начинает зависеть от внешних registry и rate limits
- сервер превращается в build-машину, хотя должен только запускать готовый артефакт

## Структура

- [docker-compose.yml](/home/hram/projects/family-messenger/infra/docker-compose.yml)
- [docker-compose.local.yml](/home/hram/projects/family-messenger/infra/docker-compose.local.yml)
- [\.env.example](/home/hram/projects/family-messenger/infra/.env.example)
- [backend/Dockerfile](/home/hram/projects/family-messenger/backend/Dockerfile)
- [family-messenger-backend.service](/home/hram/projects/family-messenger/infra/systemd/family-messenger-backend.service)

## Что делает compose

- поднимает `postgres:16-alpine`
- инициализирует БД через [schema.sql](/home/hram/projects/family-messenger/backend/schema.sql)
- в текущем состоянии репозитория умеет собирать backend container из исходников
- этот backend build допустим только для локальной разработки
- на VPS используется только `postgres`, а backend запускается отдельно из готового `fat jar`

## Локальный запуск

1. Перейти в `infra`:

```bash
cd infra
```

2. Подготовить `.env`:

```bash
cp .env.example .env
```

Это обязательный шаг. Без файла `.env` `docker compose` подставит пустые значения в `${DB_*}`, `${SERVER_PORT}` и другие переменные, из-за чего запуск сломается ошибками вида `no port specified: :<empty>`.

3. Для первого запуска системы через web wizard оставить:

```env
DB_BOOTSTRAP_SCHEMA=false
DB_SEED_ON_START=false
```

Это важно: схема будет создана Postgres init-скриптом, а сама семья и invite-коды будут созданы через `GET/POST /api/setup/*`, а не через `seed.sql`.

4. Запустить:

```bash
docker compose up -d --build
```

Важно:
- либо запускать команду из каталога `infra`
- либо, если запуск идёт из корня репозитория, указывать env-файл явно:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

5. Проверить backend:

```bash
curl http://localhost:${SERVER_PORT:-8080}/api/health
```

Если в `.env` изменён `SERVER_PORT`, используй этот порт снаружи. Внутри контейнера backend слушает `8081`, а `SERVER_PORT` управляет только внешним пробросом порта.

6. Посмотреть логи:

```bash
docker compose logs -f backend
docker compose logs -f postgres
```

7. Перезапустить backend:

```bash
docker compose restart backend
```

8. Остановить окружение:

```bash
docker compose down
```

Если нужно сбросить БД и заново пройти wizard первого запуска:

```bash
docker compose down -v
docker compose up -d --build
```

## Локальная отладка backend вне Docker

Если Ktor backend запускается из `./gradlew :backend:run` или из IDE, удобнее поднимать только Postgres:

1. Перейти в `infra`:

```bash
cd infra
```

2. Подготовить `.env`:

```bash
cp .env.example .env
```

3. Запустить только локальную БД:

```bash
docker compose -f docker-compose.local.yml up -d
```

Если команда выполняется из корня репозитория:

```bash
docker compose -f infra/docker-compose.local.yml --env-file infra/.env up -d
```

4. Запустить backend локально:

```bash
cd ..
./gradlew :backend:run
```

5. Остановить локальную БД:

```bash
docker compose -f docker-compose.local.yml down
```

Если нужно сбросить volume и заново пройти wizard первого запуска:

```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d
```

## Деплой на Ubuntu 24.04

Важно:

- backend на VPS не собирается
- `docker compose up -d --build` на VPS использовать нельзя
- целевой путь для этого проекта: Postgres через `docker compose`, backend через `systemd` и готовый `fat jar` из GitHub Releases

### Установка Docker Engine и Compose plugin

1. Обновить пакеты:

```bash
sudo apt update
sudo apt upgrade -y
```

2. Установить Docker:

```bash
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

### Установка Java runtime

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
```

### Развёртывание

1. Клонировать проект:

```bash
git clone <repo-url>
cd family-messenger/infra
```

2. Подготовить `.env`:

```bash
cp .env.example .env
```

3. Обязательно поменять:

- `DB_PASSWORD`
- при необходимости `SERVER_PORT`
- auth-related значения, если нужен другой TTL/rate limit

4. Запустить только Postgres:

```bash
docker compose up -d postgres
```

Если запуск идёт не из `infra`:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres
```

5. Создать системного пользователя и каталоги:

```bash
sudo useradd --system --home /opt/family-messenger --shell /usr/sbin/nologin family || true
sudo mkdir -p /opt/family-messenger
sudo mkdir -p /etc/family-messenger
sudo chown -R family:family /opt/family-messenger
```

6. Подготовить runtime env-файл backend:

```bash
sudo tee /etc/family-messenger/backend.env > /dev/null <<'EOF'
SERVER_PORT=8080
DB_HOST=127.0.0.1
DB_PORT=5432
DB_NAME=family_messenger
DB_USER=family
DB_PASSWORD=CHANGE_ME
DB_BOOTSTRAP_SCHEMA=false
DB_SEED_ON_START=false
AUTH_TOKEN_TTL_HOURS=720
AUTH_RATE_LIMIT_ENABLED=true
AUTH_RATE_LIMIT_WINDOW_SECONDS=60
AUTH_RATE_LIMIT_MAX_REQUESTS=10
EOF
```

7. Скачать готовый `fat jar` из GitHub Releases:

```bash
curl -fL \
  -o /tmp/family-messenger-backend-all.jar \
  https://github.com/<owner>/<repo>/releases/download/<tag>/family-messenger-backend-all.jar
sudo mv /tmp/family-messenger-backend-all.jar /opt/family-messenger/family-messenger-backend-all.jar
sudo chown family:family /opt/family-messenger/family-messenger-backend-all.jar
sudo chmod 0644 /opt/family-messenger/family-messenger-backend-all.jar
```

8. Установить `systemd` unit:

```bash
sudo cp systemd/family-messenger-backend.service /etc/systemd/system/family-messenger-backend.service
sudo systemctl daemon-reload
sudo systemctl enable family-messenger-backend
sudo systemctl start family-messenger-backend
```

9. Проверить статус:

```bash
docker compose ps postgres
sudo systemctl status family-messenger-backend --no-pager
curl http://127.0.0.1:8080/api/health
curl http://<server-ip>:8080/api/health
```

10. Смотреть логи:

```bash
docker compose logs -f postgres
sudo journalctl -u family-messenger-backend -f
```

11. Обновление backend:

```bash
curl -fL \
  -o /tmp/family-messenger-backend-all.jar \
  https://github.com/<owner>/<repo>/releases/download/<tag>/family-messenger-backend-all.jar
sudo mv /tmp/family-messenger-backend-all.jar /opt/family-messenger/family-messenger-backend-all.jar
sudo chown family:family /opt/family-messenger/family-messenger-backend-all.jar
sudo systemctl restart family-messenger-backend
```

12. Полный перезапуск:

```bash
docker compose restart postgres
sudo systemctl restart family-messenger-backend
```

13. Если нужно полностью сбросить wizard-state и БД:

```bash
docker compose down -v
docker compose up -d postgres
sudo systemctl restart family-messenger-backend
```

## GitHub Release артефакт

Текущий backend build уже умеет собирать нужный артефакт:

```bash
./gradlew :backend:buildFatJar
```

Файл результата:

```bash
backend/build/libs/family-messenger-backend-all.jar
```

Именно этот файл должен прикрепляться к GitHub Release. VPS скачивает только его и не запускает Gradle.

## Что нужно настроить руками на сервере

- открыть firewall для внешнего порта `${SERVER_PORT}`, сейчас это `8080`
- сменить `DB_PASSWORD`
- настроить backup для docker volume `postgres_data`
- позже добавить домен, reverse proxy и HTTPS
