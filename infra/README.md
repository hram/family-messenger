# Infra

Этот слой предназначен для простого и воспроизводимого запуска `backend + postgres` на локальной машине и на VPS с Ubuntu 24.04 через `Docker Compose`.

## Структура

- [docker-compose.yml](/home/hram/projects/family-messenger/infra/docker-compose.yml)
- [\.env.example](/home/hram/projects/family-messenger/infra/.env.example)
- [backend/Dockerfile](/home/hram/projects/family-messenger/backend/Dockerfile)

## Что делает compose

- поднимает `postgres:16-alpine`
- инициализирует БД через [schema.sql](/home/hram/projects/family-messenger/backend/schema.sql) и [seed.sql](/home/hram/projects/family-messenger/backend/seed.sql)
- собирает backend из исходников через multi-stage Docker build
- запускает fat jar Ktor backend

## Локальный запуск

1. Перейти в `infra`:

```bash
cd infra
```

2. Подготовить `.env`:

```bash
cp .env.example .env
```

3. Для запуска с SQL init от Postgres на чистом volume оставить:

```env
DB_BOOTSTRAP_SCHEMA=false
DB_SEED_ON_START=false
```

Это важно: в compose схема и seed уже накатываются через `docker-entrypoint-initdb.d`, поэтому runtime-bootstrap в backend лучше отключить, чтобы не дублировать инициализацию.

4. Запустить:

```bash
docker compose up -d --build
```

5. Проверить backend:

```bash
curl http://localhost:8080/api/health
```

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

Если нужно сбросить БД и заново прогнать `schema.sql` и `seed.sql`:

```bash
docker compose down -v
docker compose up -d --build
```

## Деплой на Ubuntu 24.04

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

3. Разрешить запуск без `sudo`:

```bash
sudo usermod -aG docker $USER
newgrp docker
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

4. Запустить:

```bash
docker compose up -d --build
```

5. Проверить статус:

```bash
docker compose ps
docker compose logs -f backend
```

6. Обновление после изменений:

```bash
git pull
docker compose up -d --build
```

## Что нужно настроить руками на сервере

- открыть firewall для нужного внешнего порта, обычно `80/443` или временно `8080`
- сменить `DB_PASSWORD`
- настроить домен и reverse proxy позже, если backend будет публиковаться наружу
- настроить backup для docker volume `postgres_data`
- при публичном доступе включить HTTPS на уровне reverse proxy

## Почему без Caddy на этом шаге

Для первого локального запуска и базового VPS deploy достаточно `backend + postgres`. Reverse proxy и HTTPS лучше добавлять отдельным слоем, когда локальный и базовый серверный запуск уже подтверждены.
