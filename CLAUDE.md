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
- Time: inject the `java.time.Clock` bean (`com.warbox.common.TimeConfiguration`, zone America/Sao_Paulo) instead of calling `Instant.now()` / `LocalDate.now()` directly.
- `backend/mvnw` must keep the executable bit (`git update-index --chmod=+x backend/mvnw`) and LF line endings (enforced by `.gitattributes`).

## Auth and security

- Server-side session in the servlet container (no Spring Session, no JWT in the browser). Cookie `JSESSIONID`: HttpOnly, SameSite=Lax, Path=/, `Secure` only when `SESSION_COOKIE_SECURE=true`, lifetime `SESSION_TIMEOUT`.
- CSRF follows the Spring Security SPA pattern (`com.warbox.security.SecurityConfig` + `SpaCsrfTokenRequestHandler`): cookie `XSRF-TOKEN` (readable by JS) and header `X-XSRF-TOKEN` with the raw cookie value, required on every POST/PUT/PATCH/DELETE (signup, login and logout included). `apiFetch` injects the header, calls `GET /api/auth/csrf` when the cookie is missing and retries once on 403 `CSRF_INVALID`. Login/signup rotate the token; logout (Spring's LogoutFilter, `POST /api/auth/logout`) expires both cookies.
- Every route requires a session except the ones listed with `permitAll()` in `SecurityConfig.securityFilterChain` (`/api/health`, `/error`, `GET /api/auth/csrf`, public `POST /api/auth/**`). To expose a new public route, add a method-specific matcher there. Anonymous access to anything else returns 401 JSON (`JsonAuthenticationEntryPoint`), never a redirect.
- Controllers get the user via `@AuthenticationPrincipal AccountUserDetails` (only `id()` is reliable; re-read profile data from the database).
- Error format for every 4xx: `{code, message, fields}` (`com.warbox.common.ApiErrorBody`). Throw `ApiException(HttpStatus, CODE, "mensagem pt-BR")`; `GlobalExceptionHandler` maps it, plus Bean Validation (`VALIDATION_ERROR` with `fields` keyed by request field) and unreadable JSON (`VALIDATION_ERROR`, `fields: {}`). Security errors use the same body (`UNAUTHENTICATED`, `CSRF_INVALID`, `FORBIDDEN`).
- E-mail goes through the `com.warbox.mail.EmailSender` interface (`SmtpEmailSender` -> Mailpit in dev; failures are logged, not thrown). Account e-mails are built by `AccountMailer` with links to `APP_BASE_URL`.
- Account tokens (e-mail verification, password reset) are random, single-use, expiring, and stored only as SHA-256 hex (`AccountTokenService`).
- Creator features: activating a creator profile (or any creator-only action) MUST call `AccountService.requireVerifiedEmail(userId)`, which throws 403 `EMAIL_NOT_VERIFIED` for unverified accounts.
- Backend tests: `@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration.class, TestMailConfiguration.class})` so the cached context is shared. `TestMailConfiguration` swaps SMTP for `RecordingEmailSender` (read `sent()`, `extractToken(...)`); no test uses real SMTP. Mutating MockMvc requests need `.with(csrf())` (spring-security-test); reuse the session with `result.getRequest().getSession(false)`. Reset state in `@BeforeEach` with `TRUNCATE TABLE account_tokens, users` and `RecordingEmailSender.clear()`. Expire tokens / skip cooldowns by moving `expires_at` / `created_at` with `JdbcTemplate`. Real cookie attributes (HttpOnly, SameSite) are only visible with `TestRestTemplate` on `RANDOM_PORT` (see `CsrfAndSessionCookieTest`).
- Frontend: `useMe()` (query key `ME_QUERY_KEY`, from `src/features/auth/hooks.ts`) is the single source of the auth state; invalidate/set it after login, signup, logout and e-mail verification instead of keeping auth state elsewhere.
- Environment variables: `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `APP_BASE_URL`, `SESSION_COOKIE_SECURE`, `SESSION_TIMEOUT` (see `.env.example`). Token lifetimes and the 60 s e-mail cooldown live in `warbox.auth.*` in `application.yml`.
