# Warbox

Warbox é uma plataforma de vídeo por assinatura.

## Stack

- Backend: Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React, Vite, TypeScript, React Router, TanStack Query, Tailwind CSS, shadcn/ui
- Infra local: Docker Compose (Postgres 16 e Mailpit)

## Pré-requisitos

Java 21, Node 22 e Docker.

## Como rodar

1. Suba os serviços: `docker compose up -d` (opcional: `cp .env.example .env`)
2. Backend: `cd backend && ./mvnw spring-boot:run` (no Windows: `mvnw.cmd spring-boot:run`)
3. Frontend: `cd frontend && npm install && npm run dev`
4. Abra http://localhost:5173

## Testes

- Backend: `cd backend && ./mvnw test` (o Docker precisa estar rodando)
- Frontend: `cd frontend && npm test`

Mais detalhes e convenções em [CLAUDE.md](CLAUDE.md).
