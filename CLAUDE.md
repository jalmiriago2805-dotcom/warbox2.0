# Warbox 2.0 - project guide

Subscription video platform. Monorepo: Spring Boot backend + React frontend.

## Structure

- `backend/` - Java 21, Spring Boot 3.5.x (stay on 3.x), Maven wrapper, JPA, Flyway, PostgreSQL
- `frontend/` - React + Vite + TypeScript, React Router, TanStack Query, Tailwind v4, shadcn/ui, Vitest
- `docker-compose.yml` - Postgres 17 + Mailpit (app and frontend are not in compose)
- `.env.example` - template for `.env`
- `.github/workflows/ci.yml` - CI on pull requests

## Prerequisites

JDK 21, Node 22+, Docker.

## Commands

- Infra: `docker compose up -d` (Postgres on 5432, Mailpit SMTP 1025 / UI http://localhost:8025)
- Backend: `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`), port 8080
- Frontend: `cd frontend && npm install && npm run dev`, port 5173, proxies `/api` to 8080
- Backend tests: `cd backend && ./mvnw test` - requires Docker (Testcontainers, `postgres:17-alpine`)
- Frontend tests: `cd frontend && npm test`
- If Testcontainers fails with a Docker API version error (Docker Engine 29+), override the `testcontainers.version` property in `backend/pom.xml` with a fixed release.

## Conventions

- Code, commits and PRs in English; UI in pt-BR with values in BRL.
- Backend packages by feature: `com.warbox.<feature>`. REST routes live under `/api`.
- Flyway migrations: `V<n>__<description>.sql`; never edit an applied migration. `ddl-auto=validate`.
- Test class names must end in `Test` (Surefire does not run `*IT`).
- Frontend: features in `src/features/<feature>`, pages in `src/pages`, shadcn in `src/components/ui` (add via `npx shadcn@latest add`), HTTP only through `apiFetch` + TanStack Query hooks.
- Tests never call real Stripe or Cloudflare; use stripe-mock / WireMock.
- Every feature ships with tests.
- Secrets only in `.env` (the repo is public); keep `.env.example` up to date.
- Future auth: server-side session + httpOnly cookie, no JWT in the browser. Spring Security is not configured yet.
- `backend/mvnw` must keep the executable bit (`git update-index --chmod=+x backend/mvnw`) and LF line endings (enforced by `.gitattributes`).
