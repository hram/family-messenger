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

- `POST /api/auth/register-device` -> `RegisterDeviceRequest` / `AuthPayload`
- `POST /api/auth/login` -> `LoginRequest` / `AuthPayload`
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

- auth-маршруты работают по invite code
- защищённые endpoint'ы используют bearer token
- в БД хранится только `token_hash`, а не сырой токен
- токен валидируется по `auth_tokens`, `expires_at` и `revoked_at`

## Валидация

- `deviceName`: `2..120`
- `pushToken`: до `512`
- `clientMessageUuid`: валидный UUID
- `messageIds`: `1..200`
- `latitude`: `-90..90`
- `longitude`: `-180..180`
- текстовые сообщения требуют `body`
- quick action сообщения требуют `quickActionCode`
- location сообщения требуют `location`

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
