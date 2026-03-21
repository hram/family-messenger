Продолжай проект. Теперь выдели инфраструктуру и деплой как отдельный слой.

Нужно реализовать infra для запуска на VPS с Ubuntu 24.04 через Docker Compose.

Сделай следующее:

1. Создай /infra и всё необходимое для деплоя.

2. Реализуй Dockerfile для backend:
- multi-stage build if practical
- production-friendly image
- корректный запуск Ktor server
- environment-based configuration

3. Создай docker-compose.yml для локального и VPS запуска.
   Минимально нужны сервисы:
- backend
- postgres

Опционально:
- caddy reverse proxy, если это улучшает deployability
  Но не усложняй без необходимости.

4. Создай .env.example / конфигурацию окружения:
- DB host
- DB name
- DB user
- DB password
- auth/token settings
- server port
- app base URL where relevant
- caddy/domain settings if used

5. Подготовь deploy-документацию:
- как установить Docker Engine и Docker Compose plugin на Ubuntu 24.04
- как клонировать проект
- как подготовить .env
- как запустить:
  docker compose up -d --build
- как посмотреть логи
- как перезапустить backend
- как обновить проект через git pull + docker compose up -d --build

6. Если reverse proxy добавлен:
- опиши, как подключить домен
- как включить HTTPS
- как маршрутизировать API

Важно:
- инфраструктура должна быть практичной для небольшого VPS
- не использовать Kubernetes
- не использовать overly complex DevOps stack
- всё должно быть понятно человеку, который только начинает работать с VPS
- деплой должен быть воспроизводимым

После завершения:
- покажи структуру infra файлов
- кратко опиши деплой-флоу на Ubuntu 24.04
- перечисли, что обязательно нужно настроить руками на сервере