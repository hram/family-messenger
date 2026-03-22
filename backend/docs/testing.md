# Backend Testing

## First-Run Setup Scenario

Интеграционный сценарий первого запуска покрыт тестами в [BackendIntegrationTest.kt](/home/hram/projects/family-messenger/backend/src/test/kotlin/app/BackendIntegrationTest.kt):

- `setupStatusIsFalseOnCleanDatabaseWithoutSeed`
- `bootstrapInitializesSystemAndReturnsInviteCodes`
- `bootstrapCannotRunTwice`

Они проверяют:

- чистая БД без `seedOnStart` возвращает `initialized = false`
- bootstrap создаёт семью и invite-коды
- bootstrap требует хотя бы одного parent-administrator
- по сгенерированному invite-коду можно сразу логиниться
- повторный bootstrap запрещён

## Administration Scenario

Интеграционный сценарий администрирования покрыт тестами в [BackendIntegrationTest.kt](/home/hram/projects/family-messenger/backend/src/test/kotlin/app/BackendIntegrationTest.kt):

- `administratorCanManageFamilyMembersAfterUnlockingWithMasterPassword`
- `childCannotAccessAdministrationRoutes`

Они проверяют:

- только parent с `isAdmin = true` получает доступ к admin routes
- раздел администрирования действительно требует master password поверх bearer token
- администратор может создать invite и для ребёнка, и для родителя
- новый ребёнок и новый родитель могут войти по выданным invite-кодам
- администратор может удалить участника, после чего invite и связанные сессии перестают работать

## Auth Switching Scenario

Основной интеграционный сценарий смены пользователей описан в тесте [BackendIntegrationTest.kt](/home/hram/projects/family-messenger/backend/src/test/kotlin/app/BackendIntegrationTest.kt#L200) `authSwitchingKeepsUsersChatsAndMessagesSeparated`.

Этот тест нужен для проверки того, что после логина под другим пользователем backend не "смешивает" сессии, контакты и сообщения.

### Что он делает

Тест стартует с чистой in-memory БД и проходит такой сценарий:

1. Логинится как `PARENT-DEMO` и получает `parentToken`.
2. Логинится как `CHILD-DEMO` и получает `childToken`.
3. Проверяет `GET /api/profile/me` для `parentToken`.
4. Проверяет `GET /api/contacts` для `parentToken`.
5. Отправляет сообщение от родителя ребёнку.
6. Проверяет `GET /api/profile/me` для `childToken`.
7. Проверяет `GET /api/contacts` для `childToken`.
8. Проверяет `GET /api/messages/sync` для `childToken` и убеждается, что ребёнок видит сообщение родителя.
9. Дополнительно убеждается, что у ребёнка не появляются "свои" исходящие сообщения, которых он не отправлял.
10. Отправляет ответ от ребёнка родителю.
11. Проверяет `GET /api/messages/sync` для `parentToken` и убеждается, что родитель видит оба сообщения.
12. Дополнительно убеждается, что у родителя нет ложного чата "с самим собой".

### Что именно он гарантирует

- `inviteCode` приводит к правильному пользователю.
- После смены пользователя backend возвращает другой профиль.
- После смены пользователя backend возвращает другой список контактов.
- Семейный групповой чат всегда присутствует в контактах.
- Сообщения между parent и child остаются разделёнными по правильным sender/recipient.
- Сессия одного пользователя не начинает читать данные другого пользователя.

### Чего он не проверяет

- локальный кэш desktop/mobile клиента
- UI-перерисовку списка чатов
- автоматический logout/login на клиенте
- сохранение сессии между рестартами приложения

Для этого нужны отдельные клиентские тесты уровня `AppViewModel` или UI/e2e.

### Почему это один тест

Проблема, которую ты описал, проявляется не в одном endpoint отдельно, а в последовательности действий:

- один пользователь заходит
- появляются его чаты
- потом происходит смена пользователя
- и нужно проверить, что следующий набор данных уже относится к другому пользователю

Поэтому этот сценарий полезно держать именно целиком в одном тесте, а не разносить по разным мелким happy-path тестам.

### Как запускать

Из корня репозитория:

```bash
./gradlew :backend:test --tests app.BackendIntegrationTest.authSwitchingKeepsUsersChatsAndMessagesSeparated
```

Все backend integration tests:

```bash
./gradlew :backend:test
```

## Family Group Chat Scenario

Отдельный интеграционный сценарий общего семейного чата описан в тесте [BackendIntegrationTest.kt](/home/hram/projects/family-messenger/backend/src/test/kotlin/app/BackendIntegrationTest.kt#L296) `familyGroupChatRemainsSharedWhenSwitchingBetweenThreeUsers`.

Этот тест нужен для проверки того, что семейный чат действительно общий для всех участников семьи, а не "случайно одинаковый" только для двух пользователей.

### Что он делает

Тест стартует с чистой in-memory БД и проходит такой сценарий:

1. Добавляет третий invite `SIBLING-DEMO` в ту же семью `Demo Family`.
2. Логинится как `PARENT-DEMO`.
3. Логинится как `CHILD-DEMO`.
4. Логинится как `SIBLING-DEMO`.
5. Проверяет `GET /api/contacts` у каждого пользователя.
6. Убеждается, что у каждого в контактах есть:
   - семейный чат `id = 0`
   - два остальных члена семьи
7. Родитель отправляет сообщение в семейный чат.
8. Ребёнок делает `GET /api/messages/sync` и видит это сообщение.
9. Ребёнок отправляет своё сообщение в семейный чат.
10. Третий участник делает `GET /api/messages/sync` и видит уже оба сообщения.
11. Третий участник отправляет своё сообщение в семейный чат.
12. Родитель делает `GET /api/messages/sync` и видит все три сообщения в общей истории.

### Что именно он гарантирует

- семейный чат работает для семьи из трёх человек, а не только для пары `parent/child`
- у каждого пользователя список чатов перестраивается корректно для его сессии
- семейный чат всегда один и тот же: `recipientUserId = 0`
- сообщения от всех трёх участников попадают в одну общую историю
- каждый message UUID в общей истории сохраняет правильный `senderUserId`, чтобы клиент мог показать имя отправителя
- после переключения между разными пользователями история семейного чата не распадается на отдельные копии

### Чего он не проверяет

- порядок отображения сообщений на клиенте
- кэширование чатов в desktop/mobile приложении
- UI-рендер списка участников внутри группового чата

### Как запускать

Только этот тест:

```bash
./gradlew :backend:test --tests app.BackendIntegrationTest.familyGroupChatRemainsSharedWhenSwitchingBetweenThreeUsers
```
