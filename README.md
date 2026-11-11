# APIForge AI

A local REST API analysis workbench built with Java 21, Spring Boot, React/Vite, PostgreSQL, and optional Google Gemini. Paste request and response bodies, validate JSON, get troubleshooting suggestions, generate test scenario ideas, and revisit the latest 20 analyses.

## Requirements

Java 21 **JDK** (including `javac`), Node.js 22+ with npm, and Docker Compose for PostgreSQL. Maven Wrapper downloads Maven on first use. No cloud account is needed for local mode.

## Run with PostgreSQL

From the project root:

```sh
cp .env.example .env
docker compose up -d --wait
cd backend
./mvnw spring-boot:run
```

In a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Vite proxies `/api` to the backend on port 8080. The app and database bind to localhost. Stop app processes with Ctrl+C; stop PostgreSQL with `docker compose down` from the project root. Its named volume retains history.

For an ephemeral demo without Docker:

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The demo uses H2 in PostgreSQL compatibility mode and loses history on exit. PostgreSQL is the default database. Flyway manages the schema; Hibernate validates it.

## Configuration

Compose reads `.env` for `POSTGRES_PASSWORD`. Spring does **not** automatically read `.env`; export backend variables in the shell or configure your IDE:

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/apiforge` |
| `DATABASE_USER` | `apiforge` |
| `DATABASE_PASSWORD` | `local-development-only` |
| `SERVER_PORT` | `8080` |
| `SERVER_ADDRESS` | `127.0.0.1` |
| `GEMINI_ENABLED` | `false` |
| `GEMINI_API_KEY` | empty |
| `GEMINI_MODEL` | `gemini-2.5-flash` |

If you change the database password, set both Compose's `POSTGRES_PASSWORD` and the backend's `DATABASE_PASSWORD`. Changing the variable does not update an already initialized PostgreSQL volume.

## Optional Gemini integration

Gemini stays disabled unless the backend is explicitly configured **and** the user checks “Use Gemini” for that analysis. Before enabling it, approve external data transfer and any Google API billing in your own environment. No Google resources or API calls are needed for tests.

After approval, supply `GEMINI_ENABLED=true` and `GEMINI_API_KEY` securely in the backend environment, then restart it. Never put a key into frontend code or commit it. Enabling AI sends the pasted request body, response body, method, URL and status to Google's `generateContent` API. Remove credentials and private data first; automatic redaction is not provided. Provider failures fall back to explicitly labeled local suggestions. Gemini has connection and request timeouts; no automatic billable retries are made. Model availability depends on your Google account.

## Tests and build

```sh
cd backend
./mvnw verify
cd ../frontend
npm ci
npm test
npm run build
```

JUnit tests cover JSON parsing, invalid inputs, persisted history, and API validation. Mockito tests cover opt-in AI, provider failure fallback, and local troubleshooting. Spring integration tests use an isolated H2 database with Flyway migrations, not your PostgreSQL history. Frontend tests cover submission, errors, and JSON validation. No tests call Google or execute pasted URLs.

`frontend/dist` is the production frontend build; `backend/target/apiforge-ai-0.1.0.jar` is the backend artifact. This MVP includes no deployment configuration. The Vite development proxy is not part of the static production bundle; serving a production build requires a same-origin reverse proxy for `/api`.

## Browser workflow test

With the backend running against either PostgreSQL or the demo database:

```sh
cd frontend
npx playwright install chromium
npm run test:e2e
```

Vite starts automatically if needed. For an installed system Chromium, use `CHROMIUM_PATH=/usr/bin/chromium npm run test:e2e`. This test validates payloads, creates an analysis, reloads saved history, and checks mobile overflow. It creates a local history record.

## REST API

- `GET /api/health`: local service health.
- `POST /api/validate`: `{ "json": "{\"id\":1}" }`, returning `valid`, `message`, `formatted`.
- `POST /api/analyses`: analyze and persist an exchange.
- `GET /api/analyses`: latest 20 saved results.

Example analysis:

```sh
curl http://127.0.0.1:8080/api/analyses \
  -H 'Content-Type: application/json' \
  -d '{"method":"POST","url":"https://api.example.com/orders","requestBody":"{\"quantity\":0}","responseBody":"{\"error\":\"Invalid quantity\"}","status":422,"useAi":false}'
```

Allowed methods: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS. Bodies are strings limited to 50,000 characters each; URLs are HTTP(S) and limited to 2,048 characters; status is 100–599. Empty bodies are allowed. Invalid JSON is an analysis finding, while invalid envelope fields produce HTTP 400. Trailing content after a JSON value is rejected.

## Structure

```text
backend/
  pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
  src/main/java/ai/apiforge/    REST controller, service, JSON validator,
                              Gemini adapter, JPA entity/repository, error handling
  src/main/resources/         config profiles and Flyway SQL migration
  src/test/java/ai/apiforge/   JUnit, Mockito, Spring integration tests
frontend/
  src/                        React workbench, styling, Vitest tests
  package.json, package-lock.json, vite.config.js
compose.yml                   Local PostgreSQL only
.env.example                  Non-secret configuration examples
```

## MVP boundaries

This is a single-user localhost tool without login, access controls, automatic retention deletion, or rate limiting. Do not expose it to a public network. History stores generated results (including scenario URLs and possible Gemini excerpts), not the original body fields. Treat that history as potentially sensitive. Scenario cards describe tests for a human to review; they are not executable tests against the pasted API. AI guidance is untrusted advice, rendered as text. No supplied URL is fetched. There is no deployment or paid resource provisioning.
