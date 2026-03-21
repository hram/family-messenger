# Architecture Overview

## Modules

- `shared-contract` contains API DTOs, enums, error payloads, auth/session payloads, quick action codes, and sync payloads using `kotlinx.serialization`.
- `backend` is a Ktor JVM service that depends on the JVM variant of `shared-contract`.
- `client/composeApp` is a Compose Multiplatform app that depends on `shared-contract` from `commonMain`.

## Step 1 Decisions

- One shared transport module is the source of truth for API payloads.
- Backend internal models are separated from transport models even though business logic is still a skeleton.
- Client shared code owns API access, repository boundaries, app state, and screen routing scaffolding.
- Platform-specific code is limited to entry points and future adapters such as secure storage, notifications, and geolocation.

## API Shape

The initial contract includes the required `/api/...` endpoints for auth, profile, contacts, messaging, location, presence, device token updates, and health checks. Every endpoint is modeled with a typed request/response payload and wrapped in a uniform `ApiResponse<T>`.

## Why This Structure

The monorepo keeps deployment, server, client, and transport definitions together. That reduces drift between server and client and makes Step 2 and later phases easier to implement without changing public contracts.
