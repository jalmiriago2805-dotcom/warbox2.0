# Warbox 2.0 - project guide

Subscription video platform. Monorepo: Spring Boot backend + React frontend.

## Structure

- `backend/` - Java 21, Spring Boot 3.5.x (stay on 3.x), Maven wrapper, Spring Data JPA, Flyway, PostgreSQL
- `frontend/` - React + Vite + TypeScript, React Router 7, TanStack Query 5, Tailwind CSS v4, shadcn/ui, Vitest
- `docker-compose.yml` - local Postgres 16 + Mailpit (backend and frontend are not in compose)
- `.env.example` - template for the root `.env`, shared by docker-compose, Spring (`spring.config.import`) and Vite
- `.github/workflows/ci.yml` - CI on pull requests

## Prerequisites

JDK 21 (Temurin in CI), Node 22+, Docker.

## Running locally

```sh
cp .env.example .env                        # optional: every variable has a working default
docker compose up -d                        # Postgres :5432, Mailpit SMTP :1025 / UI :8025
cd backend && ./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev   # another terminal
```

Local URLs:

- Frontend: http://localhost:5173 (Vite proxies `/api` to `API_PROXY_TARGET`, default http://localhost:8080)
- Backend: http://localhost:8080 (`GET /api/health` -> `{"status":"ok"}`)
- Mailpit UI: http://localhost:8025

## Tests

- Backend: `cd backend && ./mvnw test` (Windows: `.\mvnw.cmd test`). Docker must be running: Testcontainers starts `postgres:16-alpine`. Reuse `com.warbox.TestcontainersConfiguration` in integration tests.
- Frontend: `cd frontend && npm test` (`vitest run`); `npm run build` type-checks.
- If Testcontainers fails with a Docker API version error (Docker Engine 29+), override the `testcontainers.version` property in `backend/pom.xml` with a fixed release.

## Conventions

- Code, identifiers, comments, commits and PRs in English; UI text in pt-BR; money in BRL.
- Backend packages by feature: `com.warbox.<feature>`. DTOs are Java records.
- REST routes live under `/api`, JSON with camelCase fields. No CORS: the frontend goes through the Vite proxy.
- Flyway migrations: `src/main/resources/db/migration/V<n>__<description>.sql`; never edit an applied migration. Hibernate runs with `ddl-auto=validate`, so every schema change needs a migration.
- Test class names must end in `Test` (Surefire does not run `*IT`).
- Frontend: features in `src/features/<feature>`, pages in `src/pages`, shadcn components in `src/components/ui` (add via `npx shadcn@latest add`). HTTP calls only through `apiFetch` in `src/lib/api.ts` (`credentials: 'include'`) wrapped in TanStack Query hooks.
- Tests never call real Stripe or Cloudflare; use stripe-mock / WireMock.
- Every feature ships with tests.
- Secrets only in `.env` (the repo is public); keep `.env.example` up to date with development values.
- Spring Security is not configured yet. It arrives with the auth feature (server-side session + httpOnly cookie, no JWT in the browser), which must keep `/api/health` public.
- `backend/mvnw` must keep the executable bit (`git update-index --chmod=+x backend/mvnw`) and LF line endings (enforced by `.gitattributes`).
