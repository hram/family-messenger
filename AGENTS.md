# Repository Guidelines

## Project Structure & Module Organization
This repository is no longer prompt-only. The live source tree is:

- `backend/`: Ktor backend, Exposed repositories, SQL schema, integration tests
- `client/composeApp/`: Compose Multiplatform client for Android, Desktop, iOS, and Web/JS
- `shared-contract/`: shared DTO/API contract module used by backend and client
- `infra/`: install/update/uninstall scripts, local Docker Compose, `systemd` unit template
- `docs/`: cross-cutting architecture and operational docs
- `promts/`: original product and staged implementation prompts kept as reference

Key deployment docs:

- `README.md`: top-level project and deployment overview
- `infra/README.md`: infra scripts and server layout
- `docs/DEPLOY_RUNBOOK.md`: source of truth for manual SSH deploy workflow

## Build, Test, and Development Commands
Run commands from the repository root unless noted otherwise:

- `./gradlew :backend:build`: backend build
- `./gradlew :backend:test`: backend tests
- `./gradlew :backend:buildFatJar`: build deployable backend jar
- `./gradlew :client:composeApp:compileKotlinJs`: compile web client
- `./gradlew :client:composeApp:jsBrowserProductionWebpack`: build production web bundle
- `./gradlew :client:composeApp:assembleNoFcmDebug`: build Android no-FCM debug APK
- `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build`: local infra run
- `rg --files`: fast file listing
- `rg -n "<pattern>" .`: fast code/document search

Important deploy artifacts:

- backend jar: `backend/build/libs/family-messenger-backend-all.jar`
- web bundle dir: `client/composeApp/build/dist/js/productionExecutable/`
- Android no-FCM debug APK: `client/composeApp/build/outputs/apk/noFcm/debug/composeApp-noFcm-debug.apk`

## Deployment Memory
Before any server deploy, read `docs/DEPLOY_RUNBOOK.md`.

Operational rules already established in this repo:

- there are two server environments: `prod` and `dev`
- deploys are performed manually over `ssh`
- no `git pull` and no GitHub-based server deploy flow should be assumed for the active family server workflow
- local machine builds artifacts first, then uploads them to the target server
- prod and dev must stay isolated by install roots, config roots, ports, systemd units, postgres containers, volumes, and compose project names

Default server layout from infra scripts:

- prod install root: `/opt/family-messenger`
- prod config root: `/etc/family-messenger`
- prod systemd unit: `family-messenger-backend`
- dev install root: `/opt/family-messenger-dev`
- dev config root: `/etc/family-messenger-dev`
- dev systemd unit: `family-messenger-dev-backend`
- prod public URL default: `http://<server>:8080`
- dev public URL default: `http://<server>:9080`

If a future session needs to deploy and the concrete SSH host/user/path are missing, do not invent them. Check `docs/DEPLOY_RUNBOOK.md` first, then ask the user only for the missing server-specific values.

## Coding Style & Naming Conventions
Use clear, task-focused Markdown with short sections and numbered execution steps where helpful. Keep filenames lowercase with underscores for staged prompts, matching the existing pattern such as `step_3.md`.

For source modules:

- keep directory names explicit and stable
- prefer language-standard formatters and linters
- avoid committing generated artifacts under `build/`
- do not commit IDE-specific `.idea/` changes unless intentionally shared
- for Compose UI logo rendering, do not use `ic_launcher.svg` directly on web
- keep `appLogoPainter()` bound to a stable common UI asset such as `logo_ui.png`
- if login/splash must match the app icon visually, update `logo_ui` to the same artwork instead of pointing UI code back to launcher SVG resources

## Testing Guidelines
The repository has automated tests now.

For backend changes:

- run `./gradlew :backend:test`

For client or cross-module changes:

- run the narrowest relevant Gradle compile/test task you can justify
- document any gaps if full verification is too expensive

For documentation/deploy changes:

- verify command paths, artifact names, systemd unit names, and endpoint URLs against the current repo
- keep `README.md`, `infra/README.md`, and `docs/DEPLOY_RUNBOOK.md` consistent

## Commit & Pull Request Guidelines
Use concise, imperative commit messages such as:

- `Document manual SSH deploy workflow`
- `Update dev environment runbook`
- `Fix backend auth route validation`

Pull requests should include:

- short summary
- affected files
- rationale for architecture or deploy changes
- verification performed
- screenshots only when UI changes materially

## Configuration & Repository Hygiene
Respect `.dockerignore` and do not commit generated `build/`, `.gradle/`, local artifacts, or editor clutter.

If deployment behavior changes, update the operational docs in the same change:

- `README.md`
- `infra/README.md`
- `docs/DEPLOY_RUNBOOK.md`
