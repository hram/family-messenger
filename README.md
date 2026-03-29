# Family Messenger

Family Messenger is a Kotlin monorepo for a self-hosted family messaging MVP. The backend is now implemented around `Ktor + Koin + Exposed DSL + PostgreSQL`, while the shared transport contract remains centralized in `shared-contract`.

## Repository Layout

- `backend/`: Ktor backend with auth, validation, persistence, sync, SQL artifacts, and docs
- `client/composeApp/`: Compose Multiplatform client for Android, iOS, Desktop, and Web JS
- `client/`: client entrypoint, wrapper notes, and client-specific documentation
- `shared-contract/`: shared Kotlin Multiplatform DTO and API contract module
- `infra/`: Docker Compose deployment layer for local runs and Ubuntu 24.04 VPS
- `docs/`: cross-cutting architecture documentation
- `promts/`: original implementation prompts, staged project steps, and the HTML prototype used as an early UI reference

## Module Wiring

`shared-contract` is the single transport-definition module. The backend imports it on the JVM side and uses its DTOs in route handlers. The client depends on the same module from `commonMain`, so the request and response payloads stay aligned across platforms.

## Development Notes

The backend now includes:

- route handlers for all MVP API endpoints
- PostgreSQL persistence via Exposed DSL
- Koin-based DI
- centralized JSON error handling
- schema bootstrap and demo seed support

The client is now implemented as a Kotlin Multiplatform Compose app with shared Ktor/Koin client logic, local persistence, auth/session handling, polling sync, and platform entrypoints for Android, iOS, Desktop, and Web JS.

## Current Focus

1. Tighten production risks around transport security and external deployment.
2. Keep deploy/docs flow consistent between `README.md`, `infra/README.md`, and `docs/DEPLOY_RUNBOOK.md`.
3. Continue polishing platform UX and operational safety for family use.

See [backend README](backend/README.md), [backend API notes](backend/docs/api.md), [client README](client/README.md), and [overall architecture](docs/ARCHITECTURE.md) for details.

The practical next steps after the current implementation pass are tracked in [TODO.md](TODO.md).
Manual product-level checks are collected in [TEST_SCENARIOS.md](TEST_SCENARIOS.md).
Операционный контекст по ручному SSH-деплою хранится в [docs/DEPLOY_RUNBOOK.md](docs/DEPLOY_RUNBOOK.md).
Инструкция по первому запуску через web wizard лежит в [docs/INSTALL_WIZARD.md](docs/INSTALL_WIZARD.md).

Для самой простой пользовательской инструкции смотри [DEPLOY_FOR_FAMILY.md](DEPLOY_FOR_FAMILY.md).
Для подробной пошаговой инструкции смотри [DEPLOY_STEP_BY_STEP_FOR_FAMILY.md](DEPLOY_STEP_BY_STEP_FOR_FAMILY.md).

Если интересен сам процесс вайбкодинга, смотри:

- [promts/prompt.md](promts/prompt.md)
- [promts/step_1.md](promts/step_1.md)
- [promts/step_2.md](promts/step_2.md)
- [promts/step_3.md](promts/step_3.md)
- [promts/step_4.md](promts/step_4.md)
- [promts/step_5.md](promts/step_5.md)
- [promts/family_messenger_prototype.html](promts/family_messenger_prototype.html)
- [docs/habr_article.md](docs/habr_article.md)

## Deployment Rule

Для VPS deploy зафиксировано жёсткое правило:

- backend нельзя собирать на сервере из исходников
- сервер не должен тянуть Gradle distribution и build-time зависимости во время deploy
- деплой должен использовать только готовый backend дистрибутив
- целевой артефакт для VPS: `family-messenger-backend-all.jar`, опубликованный в GitHub Releases

Это ограничение появилось из практического опыта: source-build на VPS делает deploy медленным и хрупким и завязывает запуск на внешние registry и их rate limits.

## One-Command VPS Install

Целевой путь для обычного пользователя теперь такой:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/install.sh | bash
```

Скрипт:

- ставит Docker, Java runtime и Caddy
- поднимает Postgres
- скачивает готовый `family-messenger-backend-all.jar` из GitHub Releases
- скачивает готовый production web bundle из GitHub Releases
- скачивает Android APK без `FCM` из GitHub Releases
- создаёт `systemd` service
- публикует сайт по адресу вида `http://<server-ip>:8080`
- печатает итоговый URL вида `http://<server-ip>:8080`

Обновление:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/update.sh | bash
```

Удаление:

```bash
curl -fsSL https://raw.githubusercontent.com/hram/family-messenger/main/infra/uninstall.sh | bash
```

Подробности и ручной fallback описаны в [infra/README.md](infra/README.md).
Первый запуск после установки описан отдельно в [docs/INSTALL_WIZARD.md](docs/INSTALL_WIZARD.md).

## Prod И Dev На Одном Сервере

Если production уже используется семьёй, dev-контур нужно держать рядом, но строго отдельно.

Базовые правила:

- prod URL: `http://<server-ip>:8080`
- dev URL: `http://<server-ip>:9080`
- prod и dev должны иметь разные:
  - порты
  - `INSTALL_ROOT`
  - `CONFIG_ROOT`
  - `SYSTEMD_UNIT_NAME`
  - `POSTGRES_CONTAINER_NAME`
  - `POSTGRES_VOLUME_NAME`
  - `POSTGRES_COMPOSE_PROJECT_NAME`

В этом репозитории для этого есть:

- `infra/install.sh`, `infra/update.sh`, `infra/uninstall.sh` для prod
- `infra/install-dev.sh`, `infra/update-dev.sh`, `infra/uninstall-dev.sh` для dev

Для текущего ручного процесса семейного сервера дополнительно действует правило:

- основная операционная схема деплоя не через `git pull` на сервере, а через локальную сборку и загрузку артефактов по `ssh`
- перед любым таким деплоем нужно читать [docs/DEPLOY_RUNBOOK.md](docs/DEPLOY_RUNBOOK.md)

Важно:

- `200 OK` на `/api/health` означает, что backend отвечает, но не доказывает, что production-данные целы
- после любых операций с dev-контуром, если есть сомнение, нужно дополнительно проверить счётчики prod-данных в БД
- без отдельного Android dev flavor dev APK нельзя считать безопасной заменой prod APK на тех же устройствах
