# Family Messenger

Family Messenger is a Kotlin monorepo for a self-hosted family messaging MVP. The backend is now implemented around `Ktor + Koin + Exposed DSL + PostgreSQL`, while the shared transport contract remains centralized in `shared-contract`.

## Repository Layout

- `backend/`: Ktor backend with auth, validation, persistence, sync, SQL artifacts, and docs
- `client/composeApp/`: Compose Multiplatform client for Android, iOS, Desktop, and Web WASM
- `client/docs/`: client architecture notes
- `shared-contract/`: shared Kotlin Multiplatform DTO and API contract module
- `infra/`: Docker Compose deployment layer for local runs and Ubuntu 24.04 VPS
- `docs/`: cross-cutting architecture documentation
- `promts/`: original implementation prompts

## Module Wiring

`shared-contract` is the single transport-definition module. The backend imports it on the JVM side and uses its DTOs in route handlers. The client depends on the same module from `commonMain`, so the request and response payloads stay aligned across platforms.

## Development Notes

The backend now includes:

- route handlers for all MVP API endpoints
- PostgreSQL persistence via Exposed DSL
- Koin-based DI
- centralized JSON error handling
- schema bootstrap and demo seed support

The client is now implemented as a Kotlin Multiplatform Compose app with shared Ktor/Koin client logic, local persistence, auth/session handling, polling sync, and platform entrypoints for Android, iOS, Desktop, and Web WASM.

## Next Planned Layers

1. Finish the multiplatform client implementation.
2. Run a strict project self-review and tighten remaining MVP risks.
3. Expand deployment with reverse proxy and HTTPS if needed.

See [backend README](backend/README.md), [backend API notes](backend/docs/api.md), [client architecture](client/docs/architecture.md), and [overall architecture](docs/ARCHITECTURE.md) for details.

The practical next steps after the current implementation pass are tracked in [TODO.md](TODO.md).
Manual product-level checks are collected in [TEST_SCENARIOS.md](TEST_SCENARIOS.md).

## Deployment Rule

Для VPS deploy зафиксировано жёсткое правило:

- backend нельзя собирать на сервере из исходников
- сервер не должен тянуть Gradle distribution и build-time зависимости во время deploy
- деплой должен использовать только готовый backend дистрибутив
- целевой артефакт для VPS: `family-messenger-backend-all.jar`, опубликованный в GitHub Releases

Это ограничение появилось из практического опыта: source-build на VPS делает deploy медленным и хрупким и завязывает запуск на внешние registry и их rate limits.
