# Client

Клиентская часть проекта живёт в `client/composeApp` и реализована как
Compose Multiplatform приложение с таргетами:

- Android
- iOS
- Desktop JVM
- Web JS

`shared-contract` используется напрямую: transport DTO не дублируются
отдельно в клиенте.

## Структура

- `composeApp/`: основной KMP клиент с `commonMain`, платформенными source set'ами и shared UI
- `docs/`: клиентская документация по архитектуре и platform support
- `desktopApp/`: desktop wrapper notes
- `iosApp/`: iOS wrapper notes
- `webApp/`: Web wrapper notes

## Куда Смотреть

- [architecture.md](/home/hram/projects/family-messenger/client/docs/architecture.md)
- [platform-support.md](/home/hram/projects/family-messenger/client/docs/platform-support.md)
- [desktopApp/README.md](/home/hram/projects/family-messenger/client/desktopApp/README.md)
- [iosApp/README.md](/home/hram/projects/family-messenger/client/iosApp/README.md)
- [webApp/README.md](/home/hram/projects/family-messenger/client/webApp/README.md)

## Основные Команды

Из корня репозитория:

```bash
./gradlew :client:composeApp:compileKotlinJs
./gradlew :client:composeApp:jsBrowserProductionWebpack
./gradlew :client:composeApp:jsBrowserDistribution
./gradlew :client:composeApp:assembleDebug
./gradlew :client:composeApp:run
./gradlew :client:composeApp:desktopTest
```
