Продолжай проект, сохраняя уже созданный shared-contract без ломки API.

Теперь реализуй backend полностью.

DI в backend должен быть реализован через Koin.

Нужно сделать:

1. Полноценный backend на Ktor:
- реальные route handlers
- request validation
- auth middleware / bearer token handling
- error handling
- JSON serialization
- health endpoint
- unified API response format

2. Реализовать persistence на PostgreSQL через Exposed.
   Выбери Exposed DSL или DAO и кратко объясни выбор.
   Реализуй:
- table definitions
- repositories
- database initialization
- schema or migration bootstrap
- seed support

3. Реализовать сущности и логику для:
- families
- users
- devices
- invites
- messages
- message_receipts
- location_events
- auth_tokens

4. Реализовать endpoint'ы:
- POST /api/auth/register-device
- POST /api/auth/login
- GET /api/profile/me
- GET /api/contacts
- POST /api/messages/send
- GET /api/messages/sync?since_id=...
- POST /api/messages/mark-delivered
- POST /api/messages/mark-read
- POST /api/location/share
- POST /api/presence/ping
- POST /api/device/update-push-token
- GET /api/health

5. Реализовать ключевую серверную логику:
- invite code registration
- token issuance
- token validation
- client_message_uuid deduplication
- incremental sync by since_id
- receipts update
- family isolation / whitelist rules
- location event creation
- device last_seen updates
- simple rate limiting if practical
- input validation

6. Создать:
- schema.sql
- seed.sql
- backend README additions
- API examples

Важно:
- backend должен использовать DTO из shared-contract модуля
- не создавать дубли DTO внутри backend
- не заменять бизнес-логику TODO-заглушками
- код должен быть реалистичным, а не демонстрационным

После завершения:
- перечисли реализованные endpoint'ы
- покажи, как backend использует shared-contract DTO
- перечисли упрощения MVP
