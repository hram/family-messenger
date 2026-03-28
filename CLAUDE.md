# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Family Messenger** is a self-hosted family messaging app built with Kotlin Multiplatform. It's a monorepo containing a Ktor backend, Compose Multiplatform client (Android, iOS, Desktop, Web/JS), and a shared API contract module.

## Build & Run Commands

### Backend
```bash
./gradlew :backend:build              # Full build
./gradlew :backend:buildFatJar        # Build fat JAR (for Docker)
./gradlew :backend:run                # Run locally (requires Postgres)
./gradlew :backend:test               # Run all tests (uses H2 in-memory DB, no Docker needed)
./gradlew :backend:test --tests "app.BackendIntegrationTest.profileReturnsCurrentUserForValidToken"  # Single test
```

### Client
```bash
./gradlew :client:composeApp:compileKotlinAndroid   # Compile for Android
./gradlew :client:composeApp:compileKotlinDesktop   # Compile for Desktop
./gradlew :client:composeApp:compileKotlinJs        # Compile for Web/JS
./gradlew :client:composeApp:installDebug           # Install Android debug APK
./gradlew :client:composeApp:desktopRun             # Run desktop client
```

### Docker (from `infra/` directory)
```bash
docker compose up -d --build          # Build and start backend + postgres
```

## Deploy Memory

Read `docs/DEPLOY_RUNBOOK.md` before any server deploy.

Established operational rules:

- there are two server environments: `prod` and `dev`
- the active family-server workflow is manual deploy over `ssh`
- do not assume `git pull` on the server
- do not build the project on the server
- build locally, upload artifacts, restart only the target contour
- default contours are `/opt/family-messenger` + `family-messenger-backend` for prod and `/opt/family-messenger-dev` + `family-messenger-dev-backend` for dev

## Architecture

### Module Structure

- **`shared-contract/`** — Kotlin Multiplatform module that is the single source of truth for all API transport models. Contains `ApiModels.kt` (enums), `Requests.kt`, and `Responses.kt`. Backend and all clients share these DTOs — never duplicate transport models.
- **`backend/`** — Ktor server (JVM). Uses Exposed DSL (not ORM) for PostgreSQL. PostgreSQL in production, H2 in test mode.
- **`client/composeApp/`** — All shared UI and domain logic (Compose Multiplatform). Platform-specific entry points (`androidApp/`, `iosApp/`, `desktopApp/`, `webApp/`) are thin wrappers.

### Backend Internals (`backend/src/main/kotlin/app/`)

- **`Application.kt`** — Entry point; configures plugin pipeline
- **`plugins/`** — Ktor plugins: `Authentication.kt`, `Routing.kt`, `StatusPages.kt`, `Serialization.kt`, etc.
- **`routes/`** — HTTP route handlers (auth, messages, devices, presence, health)
- **`service/Services.kt`** — Business logic (Auth, Profile, Message, Presence, Device services)
- **`repository/`** — `Repositories.kt` defines interfaces; `ExposedRepositories.kt` implements them with Exposed DSL
- **`db/Tables.kt`** — Exposed table definitions
- **`config/AppConfig.kt`** — Configuration loaded from `application.yaml` / env vars

### Client Internals (`client/composeApp/src/commonMain/kotlin/app/`)

- **`ClientCore.kt`** — All domain logic in one file: `FamilyMessengerApiClient` (Ktor HTTP), repositories, use cases, `LocalDatabase` (snapshot persistence), `SyncEngine` (polling), `SessionStore`
- **`AppViewModel.kt`** — MVI pattern: receives UI events, updates `AppState`
- **`App.kt`** — Root Compose UI: `FamilyMessengerApp` (тема + навигация) и `WideLayout` (двухколоночный layout). Экраны вынесены в отдельные файлы (см. "UI File Structure" ниже).
- **`Platform.kt`** — `expect`/`actual` boundaries for HTTP engines (OkHttp/Darwin/JS), settings storage, secure storage, geolocation, notifications

### Key Conventions

- **Dependency injection** via Koin 4.0 (both backend and client)
- **Serialization** via `kotlinx.serialization` (JSON)
- **Database schema** defined in `backend/schema.sql`; demo data in `backend/seed.sql`
- **Backend config** in `backend/src/main/resources/application.yaml`; env vars override YAML (e.g., `DB_HOST`, `DB_PASSWORD`, `AUTH_TOKEN_TTL_HOURS`)
- **Version catalog** at `gradle/libs.versions.toml` — all dependency versions live here

### UI Color System

All colors live in `client/composeApp/src/commonMain/kotlin/app/ui/AppColors.kt`. The app supports light and dark themes.

**Rules — строго обязательны:**

1. **Никогда не использовать `Color(0xFF...)` хардкод** в UI-файлах. Любой новый цвет сначала добавляется в `AppColorScheme` (с обоими значениями — light и dark), затем используется через composable-аксессор.

2. **Никогда не объявлять локальные `private val XxxColor = Color(...)`** внутри UI-файлов. Это было историческим антипаттерном — он устранён.

3. **Исключения** — разрешены без добавления в палитру:
   - `Color.White` — только как **tint** на цветном фоне (иконки, текст на кнопках). Как фон карточки/поверхности → использовать `CardBg`.
   - `Color.Black`, `Color.Transparent` — стандартные Compose-константы.

4. **Как добавить новый цвет:**
   - Добавить поле в `data class AppColorScheme`
   - Задать значение в `LightColors` и `DarkColors`
   - Добавить composable-аксессор: `internal val MyColor: Color @Composable get() = LocalAppColors.current.myColor`
   - Использовать `MyColor` в composable-функции — без дополнительных изменений

5. **Тема** подключается в `FamilyMessengerApp` через `CompositionLocalProvider(LocalAppColors provides ...)` + `isSystemInDarkTheme()`. Все `@Composable`-функции автоматически получают правильную тему.

**Текущая палитра (`AppColorScheme`):**

| Имя | Light | Dark | Роль |
|-----|-------|------|------|
| `TgBlue` | `#2AABEE` | `#2AABEE` | Акцент, кнопки |
| `TgBlueDark` | `#1A8DD1` | `#1A8DD1` | Тёмный акцент |
| `TgBlueTint` | `#E8F4FD` | `#0D2D3F` | Фон выделения |
| `TgBlueBorder` | `#B5D4F4` | `#1E4A6A` | Синие границы |
| `AppBg` | `#E9EAEF` | `#1C1C1E` | Основной фон |
| `SetupBg` | `#F0F2F5` | `#161618` | Фон экрана настройки |
| `SplashBg` | `#E5EEF7` | `#18222C` | Фон сплэш-экрана |
| `SurfaceBg` | `#F5F5F5` | `#2C2C2E` | Вторичные поверхности |
| `SidebarBg` | `#FFFFFF` | `#1C1C1E` | Боковая панель/список |
| `CardBg` | `#FFFFFF` | `#2C2C2E` | Карточки, диалоги |
| `CardBorder` | `#E8E8E8` | `#3A3A3C` | Границы карточек, разделители |
| `InputBorder` | `#E0E0E0` | `#3A3A3C` | Границы полей ввода |
| `TextPrimary` | `#000000` | `#F2F2F7` | Основной текст |
| `TextSecondary` | `#8A8A8A` | `#8D8D93` | Вторичный текст |
| `OnlineGreen` | `#4DB269` | `#4DB269` | Онлайн-индикатор |
| `ErrorRed` | `#E24B4A` | `#FF6B6B` | Ошибки, валидация |
| `DestructiveRed` | `#FF3B30` | `#FF453A` | Кнопка удаления |
| `LinkBlue` | `#185FA5` | `#5AC8FA` | Текст-ссылка |
| `WarnBg` | `#FFF8E8` | `#2A2010` | Фон предупреждения |
| `WarnBorder` | `#F0C060` | `#7A5A1A` | Граница предупреждения |
| `WarnText` | `#633806` | `#F0C060` | Текст предупреждения |
| `BannerErrorBg` | `#F9D6D0` | `#3D1A18` | Фон баннера ошибки |
| `BannerSuccessBg` | `#D8F0D8` | `#0F2B1A` | Фон баннера успеха |
| `BubbleMe` | `#DCEFD2` | `#1E3A28` | Мои сообщения |
| `BubbleThem` | `#FFFFFF` | `#2C2C2E` | Чужие сообщения |
| `StrengthWeak` | `#E24B4A` | `#FF6B6B` | Слабый пароль |
| `StrengthMedium` | `#EF9F27` | `#EF9F27` | Средний пароль |
| `StrengthStrong` | `#639922` | `#639922` | Надёжный пароль |

### UI File Structure

UI files live in `client/composeApp/src/commonMain/kotlin/app/ui/`.

**Правила организации файлов:**

1. **Каждый навигационный экран — в отдельном файле** (`ContactsScreen.kt`, `ChatScreen.kt`, и т.д.). Критерий: экран, на который можно перейти через `Screen` enum (см. `AppState.kt`).

2. **Каждый файл экрана содержит:**
   - `internal fun XxxScreen(...)` — мобильная обёртка (TopBar + Panel)
   - `internal fun XxxPanel(...)` — переиспользуемое содержимое экрана (вызывается и из мобильной обёртки, и из `WideLayout`)
   - `private` вспомогательные composable-функции, используемые только внутри файла

3. **`Components.kt`** — общие UI-примитивы, используемые в нескольких файлах: `TgTopBar`, `AvatarCircle`, `TgChip`, `TgTextField`, extension-функции на моделях (`ContactSummary.isFamilyGroup()`, `subtitleText()`). Все `internal`.

4. **`App.kt`** — только `FamilyMessengerApp` (тема, навигация, busy-индикатор) и `WideLayout` (двухколоночный layout для широких экранов). Никакого UI экранов здесь нет.

5. **Видимость:**
   - `Screen` + `Panel` → `internal` (доступны из `App.kt` и других файлов пакета)
   - Вспомогательные composable внутри файла → `private`
   - `WideLayout` остаётся в `App.kt` как `private` — он не является самостоятельным экраном, а лишь способом компоновки существующих Panel

### Default Backend Config
- Port: `8081`
- DB: `localhost:5432/family_messenger` (user: `family`, password: `family`)
- Token TTL: 720 hours
- Swagger UI available at runtime (via `ktor-openapi` plugin)
