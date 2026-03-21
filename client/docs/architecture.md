# Client Architecture

## Module Shape

Клиент реализован в `client/composeApp` как Compose Multiplatform приложение с таргетами:

- Android
- iOS
- Desktop JVM
- Web WASM

Все transport DTO берутся из `shared-contract`. Собственных дубликатов моделей в клиенте нет.

## Shared Logic in `commonMain`

Основная логика живёт в [ClientCore.kt](/home/hram/projects/family-messenger/client/composeApp/src/commonMain/kotlin/app/ClientCore.kt) и [AppViewModel.kt](/home/hram/projects/family-messenger/client/composeApp/src/commonMain/kotlin/app/AppViewModel.kt).

В `commonMain` находятся:

- `FamilyMessengerApiClient` на Ktor client
- `ApiExecutor` с retry и error mapping
- `LocalDatabase` с локальным persisted snapshot
- `SessionStore` для bearer session handling
- repositories для auth, contacts, messages, presence и device
- use cases для register/login/load contacts/send text/send quick action/share location
- `SyncEngine` с polling, initial sync, incremental sync и flush pending queue
- message status state machine и deduplication по `clientMessageUuid` / `messageId`
- shared Compose UI: onboarding, contacts, chat, settings
- `AppViewModel`, который связывает shared domain и UI
- Koin DI bootstrap через `ClientApp`

## Local Persistence

Для этого шага локальное хранилище реализовано как сериализованный snapshot поверх платформенных key-value backends.

В snapshot входят:

- local contacts cache
- local messages cache
- pending messages queue
- sync cursor
- client settings

Отдельно в secure storage хранится активная auth session.

## Sync Model

`SyncEngine` работает в shared коде и делает:

- восстановление локальной сессии
- polling-based sync
- initial sync с `sinceId = 0`
- incremental sync по сохранённому cursor
- flush pending messages
- presence ping
- optional placeholder update для push token
- локальное обновление message statuses

## Platform Boundaries

В [Platform.kt](/home/hram/projects/family-messenger/client/composeApp/src/commonMain/kotlin/app/Platform.kt) описан `expect/actual` boundary для:

- HTTP engine
- settings storage
- secure storage
- geolocation abstraction
- notifications abstraction
- platform info
- UUID generation
