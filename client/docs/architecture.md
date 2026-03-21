# Client Architecture

## Module Shape

The client is centered on `client/composeApp`, a Compose Multiplatform module with targets for Android, iOS, Desktop, and Web WASM.

## Shared Code in `commonMain`

- `FamilyMessengerApiClient`: Ktor client wrapper using DTOs from `shared-contract`
- `SessionRepository` and `ContactsRepository`: initial repository layer
- `AppStateStore` and `AppViewModel`: shared screen state and navigation scaffold
- `FamilyMessengerApp`: shared Compose UI for onboarding, contacts, chat, and settings
- `Platform.kt`: `expect/actual` boundary for platform detection and HTTP client creation

## Platform Source Sets

- `androidMain`: Android activity and OkHttp engine
- `iosMain`: `MainViewController()` wrapper and Darwin engine
- `desktopMain`: Desktop entrypoint and Java engine
- `wasmJsMain`: browser entrypoint and JS engine

## Scope of Step 1

This stage wires the targets and shared code without implementing offline persistence, sync engine, secure storage, push handling, or platform-specific services. Those layers will be added in later steps on top of the shared contract already in use.
