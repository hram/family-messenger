# Deploy Runbook

Этот файл нужен как оперативная память проекта. Перед любым деплоем на сервер сначала читать его, а потом уже выполнять команды.

## Базовые Правила

- На сервере живут два контура: `prod` и `dev`.
- Рабочий процесс деплоя для семейного сервера ручной: локально собрать артефакты, залить их по `ssh`, перезапустить только нужный контур.
- Не предполагать `git pull` на сервере.
- Не предполагать, что сервер сам собирает проект через Gradle.
- Не путать `prod` и `dev`: у них разные порты, каталоги, `systemd` unit и postgres-инстансы.

## Что Собирать Локально

Backend fat jar:

```bash
./gradlew :backend:buildFatJar
```

Результат:

```text
backend/build/libs/family-messenger-backend-all.jar
```

Production web bundle:

```bash
./gradlew :client:composeApp:jsBrowserProductionWebpack
./gradlew :client:composeApp:jsBrowserDistribution
```

Результат:

```text
client/composeApp/build/dist/js/productionExecutable/
```

Важно:

- для ручного деплоя брать web bundle только из `client/composeApp/build/dist/js/productionExecutable/`
- одного `jsBrowserProductionWebpack` недостаточно как сигнала готовности deploy-артефакта: после него нужно добить `jsBrowserDistribution`
- не выкатывать web из `build/kotlin-webpack/...`: это промежуточный output, а не финальный deploy-каталог

Архив для заливки на сервер:

```bash
tar -C client/composeApp/build/dist/js/productionExecutable -czf /tmp/family-messenger-web.tar.gz .
```

Если нужен Android APK:

```bash
./gradlew :client:composeApp:assembleDebug
```

Результат:

```text
client/composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Известная Схема Окружений

Значения по умолчанию из `infra/install.sh` и `infra/install-dev.sh`:

| Контур | Public URL | Install root | Config root | Backend unit | Backend local |
| --- | --- | --- | --- | --- | --- |
| `prod` | `http://<server>:8080` | `/opt/family-messenger` | `/etc/family-messenger` | `family-messenger-backend` | `127.0.0.1:8081` |
| `dev` | `http://<server>:9080` | `/opt/family-messenger-dev` | `/etc/family-messenger-dev` | `family-messenger-dev-backend` | `127.0.0.1:9081` |

Связанные postgres defaults:

- `prod` container: `family-messenger-postgres`
- `dev` container: `family-messenger-dev-postgres`
- `prod` compose project: `family-messenger`
- `dev` compose project: `family-messenger-dev`

## Фактическое Состояние На Сервере

Проверено по `ssh` на `root@82.97.243.127:22`.

Production:

- backend jar: `/opt/family-messenger/family-messenger-backend-all.jar`
- web root: `/opt/family-messenger/web`
- web entrypoint: `/opt/family-messenger/web/index.html`
- Android APK: `/opt/family-messenger/web/downloads/family-messenger-android-debug.apk`
- public APK URL: `http://82.97.243.127:8080/downloads/family-messenger-android-debug.apk`

Development:

- backend jar: `/opt/family-messenger-dev/family-messenger-backend-all.jar`
- web root: `/opt/family-messenger-dev/web`
- web entrypoint: `/opt/family-messenger-dev/web/index.html`
- Android APK: `/opt/family-messenger-dev/web/downloads/family-messenger-android-debug.apk`
- public APK URL: `http://82.97.243.127:9080/downloads/family-messenger-android-debug.apk`

Caddy сейчас раздаёт:

- `:8080` -> `root * /opt/family-messenger/web`
- `:9080` -> `root * /opt/family-messenger-dev/web`
- `/api/*` на `prod` проксируется в `127.0.0.1:8081`
- `/api/*` на `dev` проксируется в `127.0.0.1:9081`

HTTP cache headers на момент проверки:

- `prod` `composeApp.js`: без `Cache-Control`
- `dev` `composeApp.js`: `Cache-Control: public, max-age=300, must-revalidate`

Это сделано специально только для `dev`, чтобы после частых web-деплоев браузер не держал старый JS слишком долго. `prod` этим hotfix не трогать без отдельного решения.

## Стандартный Ручной Деплой По SSH

Ниже шаблон. Перед запуском подставить реальные значения хоста и пользователя.

Подготовить переменные:

```bash
export DEPLOY_HOST="82.97.243.127"
export DEPLOY_USER="root"
export DEPLOY_PORT="22"
export DEPLOY_TARGET="${DEPLOY_USER}@${DEPLOY_HOST}"
```

Подключение:

```bash
ssh -p "${DEPLOY_PORT}" "${DEPLOY_TARGET}"
```

Для `prod`:

```bash
export INSTALL_ROOT="/opt/family-messenger"
export SYSTEMD_UNIT="family-messenger-backend"
export APP_USER="family"
export APP_GROUP="family"
export PUBLIC_HEALTH_URL="http://127.0.0.1:8080/api/health"
```

Для `dev`:

```bash
export INSTALL_ROOT="/opt/family-messenger-dev"
export SYSTEMD_UNIT="family-messenger-dev-backend"
export APP_USER="family-dev"
export APP_GROUP="family-dev"
export PUBLIC_HEALTH_URL="http://127.0.0.1:9080/api/health"
```

Собрать артефакты:

```bash
./gradlew :backend:buildFatJar
./gradlew :client:composeApp:jsBrowserProductionWebpack
./gradlew :client:composeApp:jsBrowserDistribution
tar -C client/composeApp/build/dist/js/productionExecutable -czf /tmp/family-messenger-web.tar.gz .
```

Проверка перед заливкой:

```bash
stat client/composeApp/build/dist/js/productionExecutable/index.html
stat client/composeApp/build/dist/js/productionExecutable/composeApp.js
```

Залить артефакты на сервер:

```bash
scp backend/build/libs/family-messenger-backend-all.jar "${DEPLOY_TARGET}:/tmp/family-messenger-backend-all.jar"
scp /tmp/family-messenger-web.tar.gz "${DEPLOY_TARGET}:/tmp/family-messenger-web.tar.gz"
```

Если нужен явный порт:

```bash
scp -P "${DEPLOY_PORT}" backend/build/libs/family-messenger-backend-all.jar "${DEPLOY_TARGET}:/tmp/family-messenger-backend-all.jar"
scp -P "${DEPLOY_PORT}" /tmp/family-messenger-web.tar.gz "${DEPLOY_TARGET}:/tmp/family-messenger-web.tar.gz"
```

Разложить файлы и перезапустить нужный контур:

```bash
ssh "${DEPLOY_TARGET}" "
  set -euo pipefail
  sudo install -o ${APP_USER} -g ${APP_GROUP} -m 0644 /tmp/family-messenger-backend-all.jar ${INSTALL_ROOT}/family-messenger-backend-all.jar
  sudo rm -rf ${INSTALL_ROOT}/web
  sudo mkdir -p ${INSTALL_ROOT}/web
  sudo tar -xzf /tmp/family-messenger-web.tar.gz -C ${INSTALL_ROOT}/web
  sudo chown -R ${APP_USER}:${APP_GROUP} ${INSTALL_ROOT}/web
  sudo systemctl restart ${SYSTEMD_UNIT}
  sudo systemctl restart caddy
  curl -fsS ${PUBLIC_HEALTH_URL}
"
```

Если нужен `rsync` вместо `scp`, использовать его допустимо, но смысл тот же: локальная сборка, затем загрузка артефактов в нужный контур.

## Проверка После Деплоя

Минимум:

```bash
ssh "${DEPLOY_TARGET}" "
  systemctl is-active ${SYSTEMD_UNIT}
  curl -fsS ${PUBLIC_HEALTH_URL}
"
```

Расширенная проверка:

```bash
ssh "${DEPLOY_TARGET}" "
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  sudo journalctl -u ${SYSTEMD_UNIT} -n 50 --no-pager
"
```

Если был риск задеть `prod` во время работ с `dev`, проверить production данные отдельно:

```bash
ssh "${DEPLOY_TARGET}" "
  docker exec family-messenger-postgres \
    psql -U family -d family_messenger -Atc \
    \"select count(*) from families; select count(*) from users; select count(*) from messages;\"
"
```

## Что Нужно Заполнить Один Раз И Держать Актуальным

Уже известно:

- `PROD SSH`: `root@82.97.243.127`
- `DEV SSH`: тот же сервер, отдельный контур на нём же
- `SSH port`: `22`
- способ авторизации: пароль
- источник секрета: `infra/.creds`

Не дублировать пароль в дополнительных markdown-файлах. Если пароль изменится, обновлять только `infra/.creds` или вынести секреты в приватное хранилище вне git.

Пока ещё не зафиксировано:

- есть ли нестандартные команды перезапуска кроме `systemctl restart ...`

## Урок Из Реального Деплоя

Зафиксированный опыт от деплоя `2026-03-23`:

- можно получить ложное ощущение успешной web-сборки, если смотреть только на `jsBrowserProductionWebpack`
- фактический deploy-каталог `build/dist/js/productionExecutable/` может остаться старым, даже если промежуточный webpack output уже новый
- правильный ручной путь для web deploy: `jsBrowserProductionWebpack` -> `jsBrowserDistribution` -> проверить `dist/` -> архивировать `dist/` -> заливать на сервер
- при обновлении `dev` web root сохранять каталог `downloads/`, иначе пропадёт опубликованный APK
- после деплоя проверять не только `/api/health`, но и `Last-Modified` или размер `composeApp.js`

## Источники Правды В Репозитории

- `infra/install.sh`
- `infra/update.sh`
- `infra/install-dev.sh`
- `infra/update-dev.sh`
- `infra/README.md`
