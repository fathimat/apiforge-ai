# APIForge AI

A REST API analysis portfolio demo built with Java 21, Spring Boot, React/Vite, PostgreSQL, and a reserved Google Gemini adapter. Paste a redacted exchange, validate JSON, get local troubleshooting suggestions, and generate test scenario ideas. No pasted endpoint is executed.

**Public demo safety:** analyses are stateless. There is no history access, body persistence, login, cookie, or browser storage. Reloading clears results. Gemini is locked off server-side, including requests that explicitly ask for AI. The local prepared path returns labeled local fallbacks rather than enabling a provider. The database schema remains for future authenticated functionality, but the public analysis flow never writes to it.

Deployment preparation is included for Render Static Site + Docker Web Service + Neon PostgreSQL. These files do not create services. See [deployment instructions](docs/DEPLOYMENT.md), [security review](docs/SECURITY.md), and [verification](VERIFICATION.md).

## Gemini-ready feature branch — not deployed

This branch prepares a strict ₹0, mocked-only AI path. `GEMINI_ENABLED=false` remains the default and the only installed transport cannot perform network I/O. No environment variable can activate live Gemini. The consent UI is disabled; local rules work for all visitors. See [the zero-budget design, safeguards, tests, and approval gates](docs/GEMINI_ZERO_BUDGET.md).

## Run locally

Requirements: Java 21 **JDK**, Node.js 22+, npm. Maven Wrapper downloads Maven on first use.

Without PostgreSQL (ephemeral H2, ideal for reviewing the stateless demo):

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

In another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173 on the **same machine** running these processes. This address is not a public cloud preview URL. Vite proxies `/api` to localhost:8080. No frontend environment variable is needed for local development.

To use local PostgreSQL instead, from the project root:

```sh
cp .env.example .env
# Set a unique local DATABASE_PASSWORD in .env before use.
set -a
. ./.env
set +a
docker compose up -d --wait
cd backend
./mvnw spring-boot:run
```

The shell example is for bash/zsh; in PowerShell, set `$env:DATABASE_USER` and `$env:DATABASE_PASSWORD` instead. Spring does not load `.env` automatically. The database binds to localhost and uses an isolated `apiforge-ai` Compose project. Stop with `docker compose down`; it retains the named volume. Changing a password variable does not update an already initialized database. The public-demo behavior (no history or persistence) also applies locally.

## Configuration

| Setting | Local behavior | Production (`prod` profile) |
| --- | --- | --- |
| `DATABASE_URL` | Defaults to local PostgreSQL JDBC URL; `demo` profile uses H2 | Required PostgreSQL JDBC URL with verified TLS |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Required for PostgreSQL; `demo` uses isolated H2 credentials | Required, separate from URL, no production credential defaults |
| `PORT` | Overrides `SERVER_PORT` / 8080 | Render-assigned port, fallback 10000 |
| Bind address | 127.0.0.1 | 0.0.0.0 |
| `CORS_ALLOWED_ORIGINS` | localhost and 127.0.0.1 origins on port 5173 | Required exact HTTPS frontend origin, no trailing slash or wildcard |
| `VITE_API_BASE_URL` | Empty for Vite development proxy | Required HTTPS backend origin at frontend build time |
| `GEMINI_ENABLED` | false | false; bundled transport also blocks all live calls independently |
| `GEMINI_API_KEY` | Leave unset | Leave unset until separate approval and an authenticated, budget-controlled AI design |

Only the backend may ever receive database credentials or a Gemini key. Every `VITE_` variable becomes public browser code. Example environment files contain placeholders, not usable production credentials.

## Tests and builds

```sh
cd backend
./mvnw verify
cd ../frontend
npm ci
npm test
VITE_API_BASE_URL=https://example-api.onrender.com npm run build
npm audit --audit-level=high
```

The example HTTPS hostname is for testing a build only; replace it with the actual backend origin when deployment is approved. Production builds reject absent, HTTP, credential-bearing, or path-suffixed API origins. Run the build command with PowerShell environment syntax on Windows.

With the backend running on 8080:

```sh
cd frontend
npx playwright install chromium
npm run test:e2e
```

Vite starts automatically. To use an installed Chromium: `CHROMIUM_PATH=/usr/bin/chromium npm run test:e2e`. The browser test covers the workbench, separate visitors, disabled history, reload privacy, and mobile overflow. Automated tests never call Google.

JUnit/Mockito tests cover byte and JSON limits, forged proxy headers, rate and concurrency caps, CORS, safe errors, stateless analysis, production origin/TLS validation, and blocked AI. Spring integration tests use H2; CI additionally reruns them against an isolated PostgreSQL service. GitHub Actions runs tests/builds only, with read-only repository permissions and no deploy job.

## REST API

| Endpoint | Behavior |
| --- | --- |
| `GET /api/health` | Minimal process liveness, no credentials or database data |
| `POST /api/validate` | `{ "json": "{\"id\":1}" }` → validity and bounded formatted preview |
| `POST /api/analyses` | Stateless result and four scenario ideas, `id:null` |
| `GET /api/analyses` | HTTP 410: shared history disabled |

```sh
curl http://127.0.0.1:8080/api/analyses \
  -H 'Content-Type: application/json' \
  -d '{"method":"POST","url":"https://api.example.com/orders","requestBody":"{\"quantity\":0}","responseBody":"{\"error\":\"Invalid quantity\"}","status":422,"useAi":false}'
```

Envelopes require an HTTP(S) URL (max 2,048 characters), supported method, HTTP status 100–599, and body strings of at most 50,000 characters each. Empty bodies are allowed. The entire HTTP body must also fit in **128 KiB**, so escaping or multi-byte text can reach the byte cap sooner. Malformed inner JSON is an analysis finding; an invalid envelope produces 400. Requests asking for AI require `aiConsent:true`; when disabled or unavailable, they return local results labeled `local-fallback` without a Google call. This branch has no live transport. The deployed `2f55262` version remains unchanged and still rejects AI requests with 400. The JSON nesting cap is 64 and oversized formatted previews are omitted.

## Project layout

```text
backend/                 Spring Boot API, security filters, JPA schema, tests, Dockerfile
frontend/                React workbench, API client, Vitest and Playwright tests
.github/workflows/       CI tests and builds, no deployments
.github/dependabot.yml   Dependency update checks
render.yaml              Unapplied hosting template; auto-deploys disabled
compose.yml              Local PostgreSQL only
backend/.env.example     Production backend placeholders
frontend/.env.example    Public API origin example
.env.example             Local database placeholders
```

This is a limited portfolio demo, not an authenticated production API testing platform. Use only sample or redacted data. App memory and cloud infrastructure still process requests; see the security review for residual risks and deployment gates.
