Ты senior solution architect, senior Kotlin Multiplatform engineer, senior Kotlin backend engineer, senior Ktor engineer и tech lead.

Нужно с нуля создать MVP self-hosted семейного мессенджера для связи родителя с детьми.
Цель: надёжная переписка в условиях нестабильной доступности публичных мессенджеров.
Результат должен быть в виде реального проекта с каталогами, кодом, SQL-схемой, документацией, docker-конфигурацией и инструкцией запуска.

# Общая идея

Нужно создать 2 основных компонента:

1. Multiplatform клиент на Kotlin Multiplatform
2. Self-hosted backend на Kotlin/Ktor + PostgreSQL

Также нужно создать 3-й общий технический слой:

3. Shared Kotlin module с DTO, контрактами API, enum'ами и общими сериализуемыми моделями, которые используются и backend, и client

Архитектурные требования:

- backend размещается не на shared hosting, а на VPS / виртуальном сервере с Ubuntu 24.04
- backend разворачивается через Docker Compose
- backend должен работать через обычные HTTP(S) запросы
- WebSocket НЕ использовать
- основной механизм получения новых сообщений: polling
- FCM использовать только как optional push-канал для Android
- система должна оставаться полностью работоспособной даже без FCM
- приложение рассчитано на маленький закрытый круг пользователей: родитель и дети
- публичная регистрация не нужна
- модель безопасности: закрытая семейная группа по invite code / pre-shared secret
- backend должен быть реалистично пригоден для запуска на одном небольшом VPS
- решение должно быть простым в деплое и сопровождении
- не использовать избыточно сложную инфраструктуру вроде Kubernetes
- dependency injection и в backend, и в client должен быть реализован через Koin

# Ключевое требование по клиенту

Клиент должен быть реализован как Kotlin Multiplatform проект с общей бизнес-логикой и общими моделями данных.

Нужно поддержать 4 платформы:

1. Android
2. iOS
3. Web WASM
4. Desktop

Важно:
- это не 4 независимых приложения, а один KMP/Kotlin Multiplatform проект
- нужно максимально переиспользовать shared code
- общими должны быть:
    - API client abstraction
    - DTO
    - domain models
    - repository layer
    - sync logic
    - polling logic
    - message state handling
    - local business rules
    - quick actions
    - settings model
    - auth/session models
    - serialization contracts
- платформенно-специфичными могут быть:
    - UI-адаптации, если они действительно нужны
    - secure storage
    - notifications
    - geolocation integration
    - app lifecycle specifics
    - local database driver specifics if needed
    - optional push integration specifics

Предпочтительный подход:
- Compose Multiplatform для UI там, где это практично
- shared module для domain/data/sync
- platform modules/adapters для Android, iOS, desktop, wasmJs
- DI в клиенте должен быть реализован через Koin

Если в каком-то месте полное 100% выравнивание платформ сильно усложняет MVP, выбирай максимально практичное решение, но сохрани единый архитектурный KMP-подход.

# Ключевое требование по shared модулю между client и backend

Нужно создать отдельный shared Kotlin module, который используется одновременно:

- backend'ом
- multiplatform client'ом

В этом модуле должны находиться:
- request DTO
- response DTO
- enum'ы
- сериализуемые модели API
- transport-level контракты
- quick action codes
- sync payload models
- auth/session payload models
- error models where appropriate

Важно:
- не дублировать DTO между backend и client
- не создавать отдельно backend DTO и отдельно client DTO для одних и тех же API-контрактов
- backend и client должны реально использовать один и тот же shared Kotlin module
- использовать kotlinx.serialization
- shared модуль должен быть реально подключён в Gradle-зависимости и использоваться по-настоящему, а не декларативно

Если какие-то внутренние backend domain/entity модели должны отличаться от transport DTO — это допустимо, но transport contract должен быть общим.

# Что именно нужно реализовать

Нужно создать полноценный монорепозиторий со структурой каталогов, например:

/family-messenger
/backend
/client
/composeApp
/iosApp
/desktopApp
/webApp
/shared-contract
/infra
README.md

Можно предложить более удачную структуру, если она логична.

Желательно, чтобы итоговая структура была близка к следующей:

/family-messenger
/backend
/src
/main
/kotlin
/resources
/test
Dockerfile
build.gradle.kts
/client
/composeApp
/iosApp
/desktopApp
/webApp
/docs
build.gradle.kts
/shared-contract
/src
/commonMain
build.gradle.kts
/infra
docker-compose.yml
.env.example
/caddy
README.md
settings.gradle.kts
gradle.properties

Можно предложить более удачную структуру, если она действительно улучшает проект.

# Функциональность MVP

## Backend

Реализовать REST API на Ktor server.

Минимальные endpoint'ы:

1. POST /api/auth/register-device
    - регистрация устройства по invite code
    - принимает:
        - invite_code
        - device_name
        - platform
        - push_token (optional)
    - platform может быть: android, ios, web, desktop
    - создаёт или находит пользователя/устройство
    - возвращает auth token и профиль

2. POST /api/auth/login
    - если нужен отдельный вход после регистрации
    - можно объединить с register-device, если архитектурно так проще
    - если объединение логичнее, задокументировать это решение

3. GET /api/profile/me
    - вернуть текущего пользователя и семейную группу

4. GET /api/contacts
    - вернуть список разрешённых контактов внутри семьи

5. POST /api/messages/send
    - отправка сообщения
    - типы сообщений:
        - text
        - quick_action
        - location
    - для MVP вложения и медиа НЕ делать

6. GET /api/messages/sync?since_id=...
    - основной endpoint для polling
    - возвращает новые сообщения, receipts, системные события
    - должен поддерживать инкрементальную синхронизацию

7. POST /api/messages/mark-delivered
    - отметить доставку списка сообщений

8. POST /api/messages/mark-read
    - отметить прочтение списка сообщений

9. POST /api/presence/ping
    - heartbeat клиента
    - обновляет last_seen

10. POST /api/location/share
- отправка текущей геопозиции
- latitude
- longitude
- accuracy optional
- label optional

11. POST /api/device/update-push-token
- обновление push token
- optional endpoint

12. GET /api/health
- healthcheck

Backend должен возвращать JSON.
Нужен единый формат ответа:
- success
- data
- error

Пример:
{
"success": true,
"data": { ... },
"error": null
}

или

{
"success": false,
"data": null,
"error": {
"code": "UNAUTHORIZED",
"message": "Invalid token"
}
}

Если для некоторых endpoint'ов более уместен более типизированный error/payload contract, это допустимо, но итоговый формат должен оставаться единообразным и понятным.

## Multiplatform client

Нужно реализовать клиент как Kotlin Multiplatform проект.

Предпочтительный стек:
- Kotlin Multiplatform
- Compose Multiplatform
- Ktor client
- Koin
- kotlinx.serialization
- Coroutines
- StateFlow
- SQLDelight или другая реалистичная KMP-friendly локальная persistence technology; если есть риск нестабильности Room KMP для этого scope, предпочесть SQLDelight
- shared MVVM/MVI-like presentation logic where practical
- expect/actual или интерфейсные abstractions для platform APIs

## Платформы

### Android
- полноценное приложение
- polling как основной fallback
- optional FCM push support
- secure token storage
- геолокация
- системные уведомления

### iOS
- приложение как минимум MVP-уровня
- те же основные экраны
- polling
- secure token storage
- геолокация
- базовая интеграция уведомлений через абстракции, даже если APNS останется упрощённым

### Web WASM
- web client на Kotlin/WASM + Compose where practical
- если Compose WASM в конкретном месте нестабилен для нужной фичи, можно выбрать совместимый практичный вариант в рамках Kotlin multiplatform ecosystem, но приоритет — Compose Multiplatform/Web/WASM friendly approach
- должен уметь:
    - логин по invite code
    - список контактов
    - чат
    - quick actions
    - polling sync
    - settings
- геолокация через browser APIs abstraction
- secure storage адаптировать под браузерные возможности

### Desktop
- desktop client на Compose Desktop
- те же базовые экраны
- polling
- локальное хранение токена
- quick actions
- чат
- settings

# Обязательное архитектурное требование по KMP

Нужно явно разделить код на:

1. client shared/commonMain
    - domain models
    - shared-contract DTO usage
    - repositories interfaces/implementations
    - sync engine
    - polling scheduler abstraction
    - quick actions
    - use cases
    - state holders / view models if chosen shared
    - serialization
    - API client
    - error handling
    - settings abstraction
    - auth/session logic
    - deduplication logic
    - message status logic

2. platform-specific source sets / modules
    - secure storage
    - notifications
    - geolocation
    - platform info
    - background behavior specifics
    - push integration specifics
    - database driver wiring if required

3. UI layer
    - максимально переиспользуемая Compose UI
    - если для iOS или web нужны платформенные обходы, делать это точечно
    - не дублировать всю UI-логику без необходимости

Важно:
- не делать фейковый KMP, где shared только models
- shared code должен содержать существенную бизнес-логику
- вся синхронизация и messaging flow должны жить преимущественно в shared client коде
- shared-contract модуль с DTO не заменяет shared client business logic; это разные уровни

# Экранов достаточно следующих

1. Экран первичной привязки устройства
    - поле invite code
    - поле device/user display name
    - выбор роли или получение роли из invite
    - кнопка подключиться
    - сохранение auth token

2. Главный экран / список контактов
    - список членов семьи
    - last seen
    - переход в чат

3. Экран чата 1:1
    - история сообщений
    - отправка текста
    - отображение sent / delivered / read
    - кнопки быстрых сообщений:
        - "Я вышел"
        - "Я у школы"
        - "Забери меня"
        - "Все нормально"
    - кнопка отправить геолокацию

4. Экран настроек
    - server base URL
    - polling interval
    - имя устройства
    - состояние push
    - logout / reset

# Поведение клиента

- после авторизации приложение сохраняет токен и профиль
- при открытии экрана чата:
    - загружает локальную историю
    - запускает синхронизацию с сервером
- polling должен быть основным способом получения новых сообщений
- polling должен работать даже если push недоступен
- Android FCM нужен только для ускорения доставки:
    - если пришёл push, клиент инициирует sync
    - без push клиент всё равно регулярно синхронизируется
- для iOS/web/desktop polling должен оставаться рабочим основным механизмом
- интервал polling:
    - настраиваемый
    - по умолчанию, например, 5–10 секунд, но не делать агрессивным без необходимости
- нужна базовая защита от дублирования сообщений
- нужны message status:
    - local_pending
    - sent
    - delivered
    - read
- при отправке сообщения:
    - сначала сохранять локально
    - потом отправлять на сервер
    - корректно обрабатывать retry
- shared sync engine должен уметь:
    - initial sync
    - incremental sync by since_id
    - sending pending messages
    - updating receipts
    - reconciling server state with local state

# Безопасность

Для MVP нужны разумные, но реалистичные меры:

- авторизация по bearer token
- токены хранить безопасно
- пароли пользователей НЕ нужны
- invite code используется для закрытой регистрации
- новые пользователи могут регистрироваться только по заранее созданному invite code
- публичной регистрации нет
- backend должен проверять, что общение возможно только внутри одной family group
- rate limit хотя бы простейший
- валидация входных данных обязательна
- SQL injection как класс проблемы не должен быть возможен благодаря корректной работе через Ktor + Exposed + параметризованные запросы / DSL
- CORS сделать аккуратно
- все секреты и настройки вынести в конфиг/окружение
- не хардкодить реальные ключи
- .env.example обязателен
- Docker Compose configuration не должна содержать реальные секреты

Не надо делать "настоящий end-to-end encryption как Signal".
Нужен обычный безопасный MVP по модели:
- HTTPS
- закрытый контур
- auth token
- whitelist внутри семьи

# База данных

Нужно спроектировать PostgreSQL schema и создать SQL файл инициализации.

Минимальные таблицы:

- families
- users
- devices
- invites
- messages
- message_receipts
- location_events
- auth_tokens или sessions

Продумать связи, индексы, created_at / updated_at, soft delete где нужно.

Примерно нужны такие сущности:

families:
- id
- name
- created_at

users:
- id
- family_id
- display_name
- role (parent/child)
- is_active
- created_at
- updated_at

devices:
- id
- user_id
- device_name
- platform
- push_token nullable
- last_seen_at
- created_at
- updated_at

invites:
- id
- family_id
- code
- role_to_assign
- expires_at nullable
- used_by_user_id nullable
- is_active
- created_at

auth_tokens:
- id
- user_id
- device_id
- token_hash
- expires_at nullable
- created_at
- revoked_at nullable

messages:
- id
- family_id
- sender_user_id
- recipient_user_id
- type (text, quick_action, location)
- body nullable
- quick_action_code nullable
- related_location_event_id nullable
- client_message_uuid
- created_at

message_receipts:
- id
- message_id
- user_id
- delivered_at nullable
- read_at nullable

location_events:
- id
- user_id
- latitude
- longitude
- accuracy nullable
- label nullable
- created_at

Индексы должны быть продуманы для sync и chat history.

# Backend implementation details

Предпочтительно сделать backend на Ktor без ненужной тяжеловесности.
Главное:
- понятная структура
- минимум магии
- читаемость
- простота деплоя на VPS через Docker Compose
- production-friendly, но без перегруза инфраструктурой

Использовать:
- Ktor server
- kotlinx.serialization
- PostgreSQL
- Exposed

По Exposed:
- можно использовать DSL или DAO
- нужно выбрать то, что лучше подходит для этого проекта
- выбор нужно кратко объяснить в документации или комментарии к архитектурным решениям
- приоритет: простота, прозрачность SQL-логики, удобство сопровождения, а не академическая идеальность

Желаемая структура backend:

/backend
/src
/main
/kotlin
/app
/config
/plugins
/routes
/service
/repository
/db
/model
/auth
/support
/resources
/test
Dockerfile
build.gradle.kts
README.md

Реализовать:
- routing
- auth middleware / authentication plugin wiring
- JSON response helper or unified response shaping
- error handling / status pages
- config loader
- database connection
- Exposed table definitions
- repositories/services
- базовое логирование
- readiness/health endpoint
- environment-based configuration

# Client implementation details

Нужно создать полноценный Kotlin Multiplatform проект.

Предпочтительная структура:

/client
/composeApp
/src
/commonMain
/androidMain
/iosMain
/desktopMain
/wasmJsMain
/iosApp
/desktopApp
/docs

или более удачную KMP-структуру, если она логична и стандартна.

Нужно реализовать:

- shared API client
- использование DTO из общего shared-contract модуля
- shared repositories
- shared use cases
- shared sync engine
- local persistence abstraction
- SQLDelight schema and queries if SQLDelight chosen
- ViewModel / state holder layer
- Compose UI screens
- platform services adapters
- periodic sync strategy
- Android optional FCM integration behind abstraction

Важно:
- если FCM не настроен, проект должен компилироваться и работать в degraded mode
- можно сделать build config flag или отдельный provider abstraction
- не ломать сборку, если firebase credentials отсутствуют
- при невозможности полностью настроить FCM в шаблоне, сделать аккуратные заглушки и подробную инструкцию

Важно по iOS:
- сгенерируй рабочую KMP-структуру для интеграции в Xcode
- создай базовый iosApp wrapper
- обеспечь понятные инструкции сборки

Важно по Web WASM:
- создай реально осмысленный web target
- не ограничивайся пустой демонстрационной страницей
- должны быть как минимум onboarding, contacts, chat, settings
- polling и send message должны реально использовать shared logic

Важно по Desktop:
- полноценный Compose Desktop entrypoint
- сборка должна быть понятной

# Quick actions

В приложении должны быть предустановленные quick actions:
- IM_OUT = "Я вышел"
- AT_SCHOOL = "Я у школы"
- PICK_ME_UP = "Забери меня"
- ALL_OK = "Все нормально"

На backend хранить quick_action_code, а на клиенте отображать локализованный текст.

# Инфраструктура и деплой

Нужно создать инфраструктурный слой для запуска на VPS с Ubuntu 24.04.

Создать:

/infra
docker-compose.yml
.env.example
/caddy
Caddyfile

Минимально docker-compose должен поднимать:
- backend
- postgres

Опционально:
- reverse proxy (предпочтительно Caddy), если это улучшает deployability

Важно:
- проект должен запускаться на VPS с Ubuntu 24.04 через Docker Compose
- основной сценарий запуска:
  docker compose up -d --build
- инфраструктура должна быть понятной человеку, который только начинает работать с VPS
- не использовать Kubernetes
- не использовать overly complex DevOps stack
- не делать внешний managed database обязательной зависимостью

Создать deploy-документацию:
- как установить Docker Engine и Docker Compose plugin на Ubuntu 24.04
- как клонировать проект на сервер
- как подготовить .env
- как запустить docker compose
- как посмотреть логи
- как перезапустить backend
- как обновить проект через git pull + docker compose up -d --build
- как подключить домен, если используется reverse proxy
- как включить HTTPS, если используется Caddy

# Документация

Нужно создать документацию:

1. README.md в корне
    - описание проекта
    - структура каталогов
    - как запускать backend
    - как запускать docker compose
    - как подготовить VPS на Ubuntu 24.04
    - как подключить БД
    - как собирать и запускать Android
    - как собирать и запускать iOS
    - как запускать web wasm client
    - как запускать desktop client
    - как включить или отключить Android FCM
    - список ограничений MVP

2. /backend/docs/api.md
    - описание endpoint'ов
    - форматы запросов и ответов
    - коды ошибок

3. /backend/sql/schema.sql
    - полная schema PostgreSQL

4. /backend/sql/seed.sql
    - минимальные тестовые данные:
        - family
        - invite code
        - 1 parent
        - 1 child
    - без небезопасных секретов

5. /client/docs/architecture.md
    - описание KMP архитектуры
    - что лежит в commonMain
    - что лежит в platform-specific коде
    - как устроен sync engine
    - как реализован polling
    - как подключён optional Android FCM
    - какие упрощения приняты для MVP

6. /client/docs/platform-support.md
    - что работает на Android
    - что работает на iOS
    - что работает на Desktop
    - что работает на Web WASM
    - какие есть ограничения

7. /infra/DEPLOY.md
    - как развернуть проект на VPS Ubuntu 24.04
    - как подготовить Docker
    - как запустить compose
    - как обслуживать и обновлять сервис

8. Если уместно, /docs/ARCHITECTURE.md
    - общая схема проекта
    - как связаны backend, client и shared-contract
    - как shared DTO используются по обе стороны

# Качество реализации

Критично:

- не делать псевдокод
- не оставлять TODO вместо ключевой бизнес-логики
- не делать "примерный каркас" — нужен реально полезный MVP
- код должен быть собран логично и последовательно
- все файлы должны быть созданы в корректных каталогах
- если где-то нужна заглушка, явно обозначить это в README
- не создавать лишние технологии
- не использовать Node.js для backend
- не использовать WebSocket
- не использовать сторонние платные сервисы как обязательную зависимость
- не делать SMS login
- не делать email login
- не делать RPC как основной transport layer
- использовать обычный понятный REST API
- shared DTO не должны превращаться в чрезмерно сложную магию

# Практичность важнее академической идеальности

Нужно принимать инженерные решения, пригодные для реального MVP.
При конфликте между "идеально красиво" и "реально работает и поддерживается" выбирай второй вариант.
Нужен минимально сложный, но не игрушечный результат.

# Режим работы

Работай как инженер, который реально создаёт проект.

Нужно:

1. Сначала спроектировать структуру каталогов
2. Затем создать shared-contract module
3. Затем создать backend
4. Затем SQL schema / Postgres setup
5. Затем создать KMP client shared architecture
6. Затем реализовать платформенные entrypoints и UI
7. Затем infra / docker compose / deploy docs
8. Затем документацию
9. Затем проверить согласованность API <-> client <-> shared-contract
10. Затем кратко перечислить, что создано

При генерации:
- создавай реальные файлы
- пиши полный код
- следи, чтобы имена endpoint'ов совпадали между backend и client
- следи, чтобы поля JSON совпадали между backend и client
- следи, чтобы SQL-схема соответствовала коду backend
- следи, чтобы shared DTO и local persistence соответствовали backend API
- следи, чтобы платформенные адаптеры не ломали общую архитектуру
- следи, чтобы shared-contract реально использовался, а не просто существовал в репозитории
- следи, чтобы infra была воспроизводимой на Ubuntu 24.04 VPS

# Дополнительно

Сразу предусмотреть:
- server base URL настраивается в client
- API versioning через /api/...
- client_message_uuid для дедупликации
- since_id для sync
- обработку пустых ответов sync
- безопасную обработку ошибок сети
- retry logic на клиенте
- offline-first поведение в разумных пределах
- настройку окружения через .env
- корректную работу docker compose networking
- healthcheck-friendly backend startup

# Очень важно

- не заменяй реализацию фразами вроде "implement as needed"
- не оставляй критичные места пустыми
- не упрощай проект до демонстрационного hello world
- если делаешь допущение, документируй его в README
- все основные файлы должны быть рабочими и согласованными
- предпочтение простоте, надёжности и deployability на VPS с Ubuntu 24.04
- в спорных местах выбирай максимально практичное решение, а не "красивую" архитектуру ради архитектуры
- KMP shared layer должен быть действительно содержательным, а не декоративным
- shared-contract layer должен реально использоваться backend и client
- Web WASM target должен быть реально осмысленным
- iOS target должен быть реально заведён на shared code
- desktop target должен быть реально запускаемым
- Android push не должен быть обязательным для общей работоспособности системы
- Docker Compose должен быть реально пригодным для первого деплоя на VPS
- backend должен быть понятен человеку, который работает с Kotlin и Ktor в реальной команде

# Итоговый результат

В результате должен появиться полный проект:
- backend на Kotlin/Ktor
- PostgreSQL schema
- shared-contract Kotlin module
- Kotlin Multiplatform client
- Android target
- iOS target
- Web WASM target
- Desktop target
- docker-compose инфраструктура
- документация

После завершения:
- покажи итоговую структуру файлов
- кратко перечисли ключевые технические решения
- перечисли, что осталось упрощённым в рамках MVP
- отдельно перечисли риски и места, которые потребуют доработки перед production

Начинай сразу с создания файлов проекта.
