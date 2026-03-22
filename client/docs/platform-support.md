# Platform Support

## Working Platform Entry Points

- Android: `AndroidMainActivity` и shared Compose UI
- iOS: `MainViewController()` и shared Compose UI
- Desktop: `desktopMain` и запускаемое JVM-приложение
- Web WASM: `ComposeViewport` и shared Compose UI в браузере

## What Works on Every Platform

- onboarding screen с invite-code login flow
- web first-run setup wizard для пустой системы
- contacts screen с загрузкой и локальным кэшем
- chat screen с text messages, quick actions и mark read
- settings screen с base URL и polling settings
- shared auth/session handling
- shared local persistence
- shared polling sync engine
- offline-first minimal queue для pending messages

## Verified Build Status

- Desktop: `:client:composeApp:compileKotlinDesktop` проходит
- Web WASM: `:client:composeApp:compileKotlinWasmJs` проходит
- Android: `:client:composeApp:compileDebugKotlinAndroid` проходит
- iOS: shared code и `iosMain` wired, но сборка не прогонялась в этом окружении, потому что iOS targets здесь отключены локальной машиной

## Current Limitations

- geolocation abstraction пока подключена, но возвращает `null` на всех платформах
- notifications abstraction пока no-op, кроме desktop `println`
- secure storage на iOS пока хранится через `NSUserDefaults`, а не Keychain
- Android push строго optional и реализован как placeholder path, без обязательной интеграции FCM
- web client ожидает доступный backend по `http://localhost:8081`, CORS/reverse proxy отдельно не настраивались
