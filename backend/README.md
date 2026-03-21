# Backend

Backend реализован на `Ktor + Koin + Exposed DSL + PostgreSQL`.

## Почему Exposed DSL

Для этого проекта DSL практичнее, чем DAO:

- запросы на sync, dedup по `client_message_uuid`, receipts и family-scoped выборки проще контролировать явно
- легче держать SQL-структуру прозрачной и предсказуемой
- меньше магии в местах, где важны индексы, `WHERE`-условия и инкрементальная синхронизация

## Что реализовано

- bearer auth с хранением `SHA-256` хеша токена в `auth_tokens`
- Koin DI для config, repository, service и database bootstrap
- централизованная обработка ошибок через `StatusPages`
- валидация входных DTO без дублирования transport-моделей
- persistence для `families`, `users`, `devices`, `invites`, `messages`, `message_receipts`, `location_events`, `auth_tokens`
- инкрементальный sync через таблицу `sync_events`
- deduplication по `(sender_user_id, client_message_uuid)`
- seed bootstrap c demo invite codes

## Конфигурация

Используются переменные из [application.yaml](/home/hram/projects/family-messenger/backend/src/main/resources/application.yaml):

- `DB_JDBC_URL`
- `DB_USER`
- `DB_PASSWORD`
- `DB_BOOTSTRAP_SCHEMA`
- `DB_SEED_ON_START`
- `AUTH_TOKEN_TTL_HOURS`
- `AUTH_RATE_LIMIT_ENABLED`
- `AUTH_RATE_LIMIT_WINDOW_SECONDS`
- `AUTH_RATE_LIMIT_MAX_REQUESTS`

## SQL

- [schema.sql](/home/hram/projects/family-messenger/backend/schema.sql)
- [seed.sql](/home/hram/projects/family-messenger/backend/seed.sql)

## API Examples

Регистрация устройства:

```bash
curl -X POST http://localhost:8080/api/auth/register-device \
  -H 'Content-Type: application/json' \
  -d '{
    "inviteCode":"PARENT-DEMO",
    "deviceName":"Pixel 8",
    "platform":"android"
  }'
```

Отправка сообщения:

```bash
curl -X POST http://localhost:8080/api/messages/send \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{
    "recipientUserId":2,
    "clientMessageUuid":"550e8400-e29b-41d4-a716-446655440000",
    "type":"text",
    "body":"Привет"
  }'
```

Sync:

```bash
curl "http://localhost:8080/api/messages/sync?since_id=0" \
  -H "Authorization: Bearer <token>"
```
