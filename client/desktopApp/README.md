# Desktop Wrapper

The desktop entrypoint lives in `client/composeApp/src/desktopMain/kotlin/app/DesktopMain.kt`.

## Desktop UI E2E

Happy-path desktop UI e2e lives in [DesktopAuthSwitchE2eTest.kt](/home/hram/projects/family-messenger/client/composeApp/src/desktopTest/kotlin/app/DesktopAuthSwitchE2eTest.kt).

Что проверяет тест:

- реальный Compose Desktop UI
- реальный backend по HTTP на случайном локальном порту
- регистрация `PARENT-DEMO`
- logout и регистрация `CHILD-DEMO`
- отправка сообщения в семейный чат и в direct chat
- login обратно под `PARENT-DEMO`
- проверка, что семейный и direct чаты не смешали сообщения после смены пользователя

Что важно:

- тест не использует системные `java.util.prefs`, а поднимает отдельные in-memory stores
- backend поднимается внутри теста на H2 и не требует Docker/PostgreSQL
- для устойчивости шаг открытия settings сейчас делается через `AppViewModel.openSettings()`, а остальные ключевые действия идут через UI

Запуск:

```bash
./gradlew :client:composeApp:desktopTest --tests app.DesktopAuthSwitchE2eTest
```
