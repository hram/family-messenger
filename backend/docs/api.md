# Backend API

Все endpoint'ы backend используют DTO из `shared-contract` и возвращают единый envelope:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

При ошибке:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or missing bearer token",
    "details": {}
  }
}
```

## Реализованные endpoint'ы

- `GET /api/setup/status` -> `SetupStatusResponse`
- `POST /api/setup/bootstrap` -> `SetupBootstrapRequest` / `SetupBootstrapResponse`
- `POST /api/auth/login` -> `LoginRequest` / `AuthPayload`
- `POST /api/admin/verify` -> `VerifyAdminAccessRequest` / `AdminMembersResponse`
- `POST /api/admin/members/create` -> `AdminCreateMemberRequest` / `AdminCreateMemberResponse`
- `POST /api/admin/members/remove` -> `AdminRemoveMemberRequest` / `AdminMembersResponse`
- `GET /api/profile/me` -> `ProfileResponse`
- `GET /api/contacts` -> `ContactsResponse`
- `POST /api/messages/send` -> `SendMessageRequest` / `SendMessageResponse`
- `GET /api/messages/sync?since_id=...` -> `SyncPayload`
- `POST /api/messages/mark-delivered` -> `MarkDeliveredRequest` / `AckResponse`
- `POST /api/messages/mark-read` -> `MarkReadRequest` / `AckResponse`
- `POST /api/location/share` -> `ShareLocationRequest` / `AckResponse`
- `POST /api/presence/ping` -> `PresencePingRequest` / `AckResponse`
- `POST /api/device/update-push-token` -> `UpdatePushTokenRequest` / `AckResponse`
- `GET /api/health` -> `HealthResponse`

## Auth

- auth-маршрут работает по invite code
- защищённые endpoint'ы используют bearer token
- в БД хранится только `token_hash`, а не сырой токен
- токен валидируется по `auth_tokens`, `expires_at` и `revoked_at`
- `user.isAdmin` возвращается в auth/profile payload и определяет доступ к разделу администрирования

## First-Run Setup

- `GET /api/setup/status` показывает, инициализирована ли система
- `POST /api/setup/bootstrap` доступен только до первого запуска
- bootstrap создаёт семью, сохраняет BCrypt hash master password и выпускает invite-коды для членов семьи
- хотя бы один `parent` должен быть отмечен как `isAdmin = true`
- флаг `isAdmin` разрешён только у родителей

## Валидация

- `pushToken`: до `512`
- `clientMessageUuid`: валидный UUID
- `messageIds`: `1..200`
- `latitude`: `-90..90`
- `longitude`: `-180..180`
- текстовые сообщения требуют `body`
- quick action сообщения требуют `quickActionCode`
- location сообщения требуют `location`
- `masterPassword`: `8..256`
- `members`: `1..20`, уникальные `displayName`, минимум один `parent`, минимум один `parent`-administrator

## Administration

- `/api/admin/*` доступны только аутентифицированному пользователю с `user.isAdmin = true`
- каждый admin-запрос дополнительно требует `masterPassword` в теле запроса
- `POST /api/admin/verify` разблокирует раздел администрирования и возвращает текущий список членов семьи
- `POST /api/admin/members/create` создаёт новый invite для родителя или ребёнка
- `POST /api/admin/members/remove` деактивирует invite, связанного пользователя и его активные токены
- флаг `isAdmin` в admin create разрешён только для `parent`

## Sync semantics

Инкрементальный sync реализован через таблицу `sync_events`:

- `nextSinceId` это id последнего обработанного sync event
- новые сообщения, обновления receipts и location events попадают в sync как отдельные события
- `messages` и `receipts` возвращаются как transport DTO
- `location_events` отдаются в `events` как `SystemEventPayload`

## Примеры

Health:

```bash
curl http://localhost:8080/api/health
```

Mark read:

```bash
curl -X POST http://localhost:8080/api/messages/mark-read \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"messageIds":[10,11]}'
```

Share location:

```bash
curl -X POST http://localhost:8080/api/location/share \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{
    "latitude":55.751244,
    "longitude":37.618423,
    "accuracy":15.0,
    "label":"Home"
  }'
```

Bootstrap:

```bash
curl -X POST http://localhost:8080/api/setup/bootstrap \
  -H 'Content-Type: application/json' \
  -d '{
      "masterPassword":"super-secret-pass",
      "familyName":"My Family",
      "members":[
      {"displayName":"Mom","role":"parent","isAdmin":true},
      {"displayName":"Kid","role":"child"}
    ]
  }'
```

Admin unlock:

```bash
curl -X POST http://localhost:8080/api/admin/verify \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"masterPassword":"super-secret-pass"}'
```
