# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Family Messenger** is a self-hosted family messaging app built with Kotlin Multiplatform. It's a monorepo containing a Ktor backend, Compose Multiplatform client (Android, iOS, Desktop, Web/WASM), and a shared API contract module.

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
./gradlew :client:composeApp:compileKotlinWasmJs    # Compile for Web/WASM
./gradlew :client:composeApp:installDebug           # Install Android debug APK
./gradlew :client:composeApp:desktopRun             # Run desktop client
```

### Docker (from `infra/` directory)
```bash
docker compose up -d --build          # Build and start backend + postgres
```

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
- **`App.kt`** — Root Compose UI
- **`Platform.kt`** — `expect`/`actual` boundaries for HTTP engines (OkHttp/Darwin/JS), settings storage, secure storage, geolocation, notifications

### Key Conventions

- **Dependency injection** via Koin 4.0 (both backend and client)
- **Serialization** via `kotlinx.serialization` (JSON)
- **Database schema** defined in `backend/schema.sql`; demo data in `backend/seed.sql`
- **Backend config** in `backend/src/main/resources/application.yaml`; env vars override YAML (e.g., `DB_HOST`, `DB_PASSWORD`, `AUTH_TOKEN_TTL_HOURS`)
- **Version catalog** at `gradle/libs.versions.toml` — all dependency versions live here

### Default Backend Config
- Port: `8081`
- DB: `localhost:5432/family_messenger` (user: `family`, password: `family`)
- Token TTL: 720 hours
- Swagger UI available at runtime (via `ktor-openapi` plugin)
