# Warbox 2.0

Subscription video platform. Monorepo with a Spring Boot backend and a React frontend.

## Structure

- `backend/` - Java 21, Spring Boot 3, Maven (wrapper), Spring Data JPA, Flyway, PostgreSQL
- `frontend/` - React + Vite + TypeScript, React Router 7, TanStack Query 5, Tailwind CSS 4, shadcn/ui, Vitest
- `docker-compose.yml` - local Postgres 16 and Mailpit
- `.env.example` - template for the root `.env` (shared by docker-compose, Spring and Vite)
- `.github/workflows/ci.yml` - CI for pull requests

## Prerequisites

Java 21, Node 22, Docker.

## Running locally

```
docker compose up -d                      # Postgres + Mailpit
cp .env.example .env                      # optional, all variables have defaults
cd backend && ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev
```

URLs: frontend http://localhost:5173, backend http://localhost:8080 (`/api/health`), Mailpit UI http://localhost:8025.

## Tests

- Backend: `cd backend && ./mvnw test` - Docker must be running (Testcontainers).
- Frontend: `cd frontend && npm test`

## Conventions

- Code, identifiers, commits and PRs in English; UI text in pt-BR; money in BRL.
- Backend: feature packages `com.warbox.<feature>`; DTOs are records; REST under `/api` with camelCase JSON.
- Flyway migrations: `V<n>__<description>.sql`; never edit an already applied migration. Hibernate runs with `ddl-auto=validate`.
- Frontend: features in `src/features/<feature>`, pages in `src/pages`, HTTP calls only through `apiFetch` in `src/lib/api.ts` (credentials include), shadcn components in `src/components/ui`.
- Secrets only in `.env` (the repo is public); keep `.env.example` up to date.
- Tests must never call real Stripe or Cloudflare; use stripe-mock / WireMock.
- Every feature ships with tests.
- Spring Security is not configured yet; it arrives with the auth feature, which must permit `/api/health`.
