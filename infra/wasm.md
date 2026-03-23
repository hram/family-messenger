# 📌 Web Target Decision: WASM vs JS (Compose Multiplatform)

## Контекст

В проекте используется Compose Multiplatform с web-клиентом.

Изначально был выбран target:
- `wasmJs` (Kotlin/Wasm + ComposeViewport)

При деплое backend + web клиента на сервер (доступ по `http://IP:PORT`) приложение не запускалось.

---

## Симптомы проблемы

При открытии приложения по `http://IP:PORT`:

- `window.isSecureContext = false`
- `window.crossOriginIsolated = false`

Ошибки в браузере:

- `The Cross-Origin-Opener-Policy header has been ignored, because the URL's origin was untrustworthy`
- `unhandledrejection: WebAssembly.Exception`
- падение в bootstrap `composeApp.js` ДО вызова `main()`

При запуске:

- `http://localhost` → работает
- `https://...` → работает

---

## Причина

Проблема НЕ в:
- backend
- API
- docker/deploy
- nginx/headers

Проблема в runtime:

- Compose WASM + Skiko bootstrap требует trusted origin
- на `http://IP` браузер:
    - считает origin "untrustworthy"
    - игнорирует COOP/COEP
    - не включает cross-origin isolation
- из-за этого падает инициализация WASM runtime до старта приложения

Важно:
- в коде приложения НЕ используется явно:
    - `SharedArrayBuffer`
    - `Web Workers`
- но runtime (Compose/Skiko) всё равно чувствителен к origin

---

## Ключевой вывод

❗ `wasmJs` target НЕ работает по `http://IP:PORT`

Это не баг конфигурации — это ограничение текущего стека.

---

## Принятое решение (временное)

Выполнена миграция:
