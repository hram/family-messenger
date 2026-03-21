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
- OpenAPI + Swagger UI через `io.github.smiley4`
- persistence для `families`, `users`, `devices`, `invites`, `messages`, `message_receipts`, `location_events`, `auth_tokens`
- инкрементальный sync через таблицу `sync_events`
- deduplication по `(sender_user_id, client_message_uuid)`
- seed bootstrap c demo invite codes

## Routing Style

- зависимости сервисов резолвятся в [Routing.kt](/home/hram/projects/family-messenger/backend/src/main/kotlin/app/plugins/Routing.kt) и передаются в route-модули явно
- protected route-модули не объявляют `authenticate { ... }` внутри себя, если они монтируются из общего auth-блока в [Routing.kt](/home/hram/projects/family-messenger/backend/src/main/kotlin/app/plugins/Routing.kt)
- при добавлении нового защищённого route-модуля его нужно подключать внутрь общего `authenticate("auth-bearer")` блока, а не дублировать auth-обёртку в каждом файле
- внутри route-файлов не использовать `Route.inject()` для бизнес-сервисов; зависимости должны быть видны в сигнатуре функции

## Tests

Backend покрыт минимальным набором integration/smoke тестов в [BackendIntegrationTest.kt](/home/hram/projects/family-messenger/backend/src/test/kotlin/app/BackendIntegrationTest.kt).

Что проверяется:

- `register-device` happy path
- повторное использование invite при `max_uses = 1`
- `login` для уже зарегистрированного устройства
- `401 Unauthorized` для protected route без bearer token
- `profile/me` с валидным token
- `messages/send` и `messages/sync`
- deduplication по `clientMessageUuid`
- rate limit для auth endpoint'ов
- доступность `openapi.json` и наличие `bearerAuth` в спецификации

Как это устроено:

- тесты запускают реальное Ktor-приложение через `testApplication {}`
- вместо PostgreSQL используется in-memory `H2` в PostgreSQL-совместимом режиме
- перед каждым тестом схема сбрасывается, поэтому тесты изолированы друг от друга

Запуск из корня репозитория:

```bash
./gradlew :backend:test
```

Запуск только backend-тестов без демона:

```bash
./gradlew --no-daemon :backend:test
```

Запуск одного тест-класса:

```bash
./gradlew :backend:test --tests app.BackendIntegrationTest
```

Запуск одного теста:

```bash
./gradlew :backend:test --tests app.BackendIntegrationTest.profileReturnsCurrentUserForValidToken
```

Важно:

- для этих тестов не нужен Docker и не нужен локальный PostgreSQL
- тесты не требуют `buildFatJar` и не зависят от `docker compose`
- если добавляешь новый protected endpoint, обычно стоит добавить хотя бы один smoke/integration тест на happy path и один тест на `401`

## Конфигурация

Используются переменные из [application.yaml](/home/hram/projects/family-messenger/backend/src/main/resources/application.yaml):

- `DB_JDBC_URL` или `DB_HOST` + `DB_PORT` + `DB_NAME`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
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

Swagger UI:

```bash
open http://localhost:8080/api-docs/swagger
```
