# TODO

## Шаг A. Локальный ручной запуск end-to-end

Поднять:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Проверить:

- backend health
- register-device
- send message
- sync
- receipts
- location flow
- добавить debug-e2e режим с видимым desktop-окном для наблюдаемого прогона сценариев вручную

## Шаг B. Первый VPS deploy

Без идеализма. Просто поднять и убедиться, что:

- домен смотрит куда надо
- backend доступен извне
- Postgres жив
- клиент может подключиться
- one-command installer реально работает на чистом Ubuntu VPS
- не собирать backend на VPS из исходников
- не заставлять сервер тянуть Gradle и base images во время deploy
- выкатывать только готовый `family-messenger-backend-all.jar` из GitHub Releases
- backend запускать через `systemd`

## Шаг C. Довести реальные платформенные фичи

- geolocation
- secure storage
- notifications хотя бы минимально
- iOS smoke run

## Шаг D. Только потом production hygiene

- token cleanup
- invite admin flow
- автоматизировать выпуск `fat jar` в GitHub Releases
- убрать source-build из server deploy path полностью
- CORS tightening
