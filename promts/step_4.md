Продолжай проект, не ломая shared-contract DTO и backend API.

Теперь реализуй клиентскую часть полностью как Kotlin Multiplatform проект.

DI в клиенте должен быть реализован через Koin.

Нужно сделать:

1. Реализовать shared client слой:
- Ktor client
- repositories
- use cases
- sync engine
- polling logic
- auth/session handling
- retry logic
- error mapping
- deduplication logic
- message status state machine

2. Реализовать local persistence.
   Если есть риск нестабильности Room KMP для этого проекта, предпочесть SQLDelight.
   Нужно сделать:
- local entities
- queries / DAO equivalent
- storage abstractions
- pending messages storage
- sync cursor storage
- settings storage

3. Реализовать UI и платформенные entrypoints для:
- Android
- iOS
- Desktop
- Web JS

4. Реализовать экраны:
- onboarding
- contacts
- chat
- settings

5. Реализовать поведение:
- register-device flow
- login flow if it retained in API
- contacts loading
- chat history loading
- send text message
- quick actions
- location share abstraction
- mark delivered / mark read
- polling-based sync
- initial sync
- incremental sync
- offline-first minimal behavior

6. Реализовать платформенные адаптеры:
- secure storage
- geolocation abstraction
- notifications abstraction
- platform info abstraction

7. Push не делать обязательным.
   Если Android push будет добавляться, он должен быть строго optional и не ломать сборку.

Важно:
- shared код должен быть содержательным, а не декоративным
- sync engine должен жить преимущественно в shared
- web wasm target не должен быть пустой заглушкой
- desktop должен реально запускаться
- iOS должен быть реально заведён на shared code
- client должен использовать transport DTO из shared-contract, а не собственные дубликаты

После завершения:
- перечисли, что работает на каждой платформе
- перечисли ограничения по платформам
- покажи, какие части логики находятся в commonMain
