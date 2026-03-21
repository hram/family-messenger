Возьми финальный мастер-промпт как основной контракт проекта.

Сначала создай правильный архитектурный каркас проекта и общий shared-contract между backend и client.

Нужно сделать только следующее:

1. Создать структуру монорепозитория:
- /backend
- /client
- /shared-contract
- /infra
- README.md

2. Создать shared-contract Kotlin module, который реально используется и backend, и client.
   В shared-contract модуле реализуй:
- request DTO
- response DTO
- enum'ы
- transport models
- auth/session payload models
- sync payload models
- quick action codes
- unified error payload models where appropriate
- kotlinx.serialization

3. Продумать и реализовать API contract:
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

4. Создать skeleton backend на Ktor:
- gradle setup
- зависимости
- базовая структура пакетов
- wiring Ktor application
- routing skeleton
- config skeleton
- Exposed database wiring skeleton
- integration of shared-contract

5. Создать skeleton client KMP:
- targets: Android, iOS, Desktop, Web WASM
- shared client modules
- базовая wiring-структура
- entrypoints для платформ
- integration of shared-contract

6. Создать базовую архитектурную документацию:
- README.md
- /client/docs/architecture.md
- /backend/docs/api.md
- /docs/ARCHITECTURE.md if useful

Важно:
- пока не реализуй полностью бизнес-логику
- сначала сделай правильный каркас и shared contract
- DTO должны быть едиными и реально подключёнными с обеих сторон
- не делай дублирование transport моделей между backend и client
- покажи в build files, как shared-contract подключён и туда, и туда

После завершения:
- покажи итоговую структуру файлов
- объясни, как shared-contract подключён к backend и client
- перечисли принятые архитектурные решения