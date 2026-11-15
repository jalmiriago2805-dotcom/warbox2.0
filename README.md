# Warbox

Warbox 2.0 é uma plataforma de vídeos por assinatura: criadores publicam conteúdo exclusivo e assinantes pagam (em BRL) para assistir. Este repositório é um monorepo com o backend Spring Boot e o frontend React.

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL
- **Frontend:** React, Vite, TypeScript, React Router, TanStack Query, Tailwind CSS v4, shadcn/ui, Vitest
- **Infra local:** Docker Compose (PostgreSQL 16 e Mailpit)

## Pré-requisitos

- JDK 21
- Node 22 ou mais recente
- Docker (com Docker Compose)

## Como rodar localmente

1. (Opcional) Crie o `.env` a partir do modelo. Todas as variáveis têm valores padrão:

   ```sh
   cp .env.example .env
   ```

2. Suba o Postgres e o Mailpit:

   ```sh
   docker compose up -d
   ```

3. Rode o backend (porta 8080):

   ```sh
   cd backend
   ./mvnw spring-boot:run      # Windows: .\mvnw.cmd spring-boot:run
   ```

4. Em outro terminal, rode o frontend (porta 5173):

   ```sh
   cd frontend
   npm install
   npm run dev
   ```

5. Abra http://localhost:5173.

Outros endereços úteis:

- Health da API: http://localhost:8080/api/health
- Mailpit (e-mails de desenvolvimento): http://localhost:8025

## Testes

- **Backend:** `cd backend && ./mvnw test` (Windows: `.\mvnw.cmd test`). O Docker precisa estar rodando, porque o Testcontainers sobe um PostgreSQL temporário.
- **Frontend:** `cd frontend && npm test`

## Convenções

Consulte o [CLAUDE.md](CLAUDE.md) para a estrutura do projeto e as convenções de código.
