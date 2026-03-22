Теперь проведи строгий self-review проекта как principal engineer.

Проверь и исправь проект по следующим направлениям:

1. Shared contract
- действительно ли backend и client используют одни и те же DTO
- нет ли скрытого дублирования transport моделей
- согласованы ли поля serialization
- нет ли расхождений между документацией и кодом

2. Backend API
- совпадают ли реальные endpoint'ы с документацией
- корректны ли request/response модели
- нет ли расхождений в именах полей
- корректна ли auth/token логика
- корректен ли Ktor wiring

3. Database / Exposed
- соответствуют ли таблицы логике API
- достаточно ли индексов для sync и chat history
- корректен ли deduplication через client_message_uuid
- нет ли опасных упрощений
- оправдан ли выбор DSL/DAO

4. Infra / deployability
- реально ли backend запускается через Docker Compose
- достаточно ли Dockerfile production-friendly
- понятна ли инструкция для Ubuntu 24.04 VPS
- нет ли лишней инфраструктурной сложности

5. KMP architecture
- commonMain не декоративный ли
- не вынесено ли слишком много критичной логики в платформенные слои
- реально ли переиспользование кода между Android/iOS/Desktop/Web JS

6. Sync consistency
- initial sync
- incremental sync by since_id
- pending messages sending
- receipts propagation
- local/server reconciliation
- retry behavior
- offline behavior

7. Platform realism
- Android launchability
- iOS integration realism
- Desktop launchability
- Web JS practicality

8. Security
- token handling
- invite flow
- family isolation
- input validation
- dangerous assumptions

Исправь всё, что можешь исправить автоматически, прямо в коде.

После этого:
- выведи список найденных проблем
- перечисли, что исправлено
- перечисли, что сознательно оставлено упрощённым
- отдельно перечисли production-risks
