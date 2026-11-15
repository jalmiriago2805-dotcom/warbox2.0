# Warbox

*Vídeos exclusivos dos seus criadores favoritos, por assinatura.*

Warbox 2.0 is a subscription video platform: a Spring Boot backend and a React frontend in one monorepo.

## Stack

- Backend: Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React, Vite, TypeScript, React Router, TanStack Query, Tailwind CSS v4, shadcn/ui
- Local infra: Docker Compose (Postgres 17 and Mailpit)

## Prerequisites

JDK 21, Node 22+, Docker.

## Quickstart

```sh
cp .env.example .env
docker compose up -d
cd backend && ./mvnw spring-boot:run       # Windows: mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev  # in another terminal
```

- App: http://localhost:5173
- API health: http://localhost:8080/api/health
- Mailpit: http://localhost:8025

## Tests

- Backend: `cd backend && ./mvnw test` (Docker must be running; Testcontainers starts Postgres)
- Frontend: `cd frontend && npm test`

See [CLAUDE.md](CLAUDE.md) for conventions.
