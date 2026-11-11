# Render + Neon deployment runbook

**Runbook for the existing hosting setup. The owner has deployed the application; this local Gemini-ready work does not modify those deployments. Do not reapply this template or create/redeploy services without approval.** Keep the source repository private; Render can deploy a private repository through its GitHub integration. Public visitors receive the built application, not access to the GitHub repository.

Architecture: browser → Render Static Site (React assets) → HTTPS Render Web Service (Spring Boot Docker) → TLS Neon PostgreSQL. Gemini is disabled. PostgreSQL currently holds only the empty analysis schema/Flyway metadata; no visitor exchanges or results are saved.

## 1. Authorization and permanent ₹0 gate

After deployment is explicitly approved:

1. Connect Render to GitHub and grant access to **only** `fathimat/apiforge-ai`.
2. Use a free Render workspace/Static Site and a **Free** Web Service. Do not select a paid instance or add disks, paid databases, custom domains with charges, or credit-funded services.
3. Select Neon's **Free** plan, keep autoscaling/compute within that plan, and review limits in its current dashboard.
4. Review both providers' billing and usage settings before clicking any Create/Deploy button. Do not add a payment method, activate billing, buy credits, or authorize supplementary spending. Limits and prices can change; do not accept an upgrade prompt automatically.
5. Keep Render auto-deploys **Off** for both services. The template sets `autoDeployTrigger: "off"`; manual initial creation still deploys. GitHub Actions contains no deploy workflow or provider credentials.

The account authorization steps require your browser; do not paste tokens or database passwords into chat or repository files.

## 2. Neon PostgreSQL

1. Create a **new** Free project named `apiforge-ai`, using PostgreSQL 17 in a region close to the Render service (the template uses Frankfurt). Do not reuse or import another project's database.
2. In Neon's connection dialog choose a direct connection (hostname without `-pooler`). This small app has its own pool capped at three connections; a direct endpoint also keeps Flyway startup migrations straightforward.
3. Use the dedicated role for this project's database/schema only. The role needs permission to create the Flyway table and run the included migration; it should not have access to other application databases.
4. Put these values only into the Render **backend** Environment fields:

```text
DATABASE_URL=jdbc:postgresql://<DIRECT_NEON_HOST>/<DATABASE>?sslmode=verify-full&sslrootcert=/etc/ssl/certs/ca-certificates.crt
DATABASE_USER=<NEON_ROLE>
DATABASE_PASSWORD=<NEON_PASSWORD>
```

Use the JDBC format, not a `postgresql://user:password@host/...` URI. Do not embed credentials in the URL. Keep the CA path and `sslmode=verify-full`; the runtime image has the system CA bundle. No migration shell command is required: Flyway initializes the schema on backend startup, and Hibernate validates it. The production guard rejects insecure TLS settings before a datasource can connect.

A fresh project keeps any previous private local history out of the hosted database. The history endpoint remains disabled even if a table already contains data.

## 3. Backend — Render Web Service

Prefer the manual flow for the first deployment because the assigned frontend/backend URLs must be exchanged once. The checked-in `render.yaml` is a reproducible alternative once those origins are known; applying it also creates/deploys resources. No Render database is defined because Neon supplies PostgreSQL.

In Render choose **New → Web Service**, connect the private repository, and set:

| Field | Value |
| --- | --- |
| Branch | `main` |
| Runtime | Docker |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` (relative to root directory) |
| Docker Build Context | `.` (relative to root directory) |
| Region | Frankfurt, or the closest available region to Neon |
| Instance | **Free** |
| Health Check Path | `/api/health` |
| Auto-Deploy | **Off** |

Set backend environment variables:

| Name | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Values from step 2, stored only in Render |
| `CORS_ALLOWED_ORIGINS` | Exact static-site HTTPS origin, e.g. `https://<STATIC_SERVICE>.onrender.com`; no trailing slash |
| `GEMINI_ENABLED` | `false` |

Leave `GEMINI_API_KEY` **unset**. Do not override `PORT` or the bind address. Spring uses Render's assigned port and binds to 0.0.0.0 in production. The JRE runs as a non-root user with memory bounds suitable for evaluating a small Free instance.

If the frontend URL is not known yet, temporarily set CORS to `https://example.invalid`, which permits no actual visitor frontend. Create the backend, copy its assigned HTTPS origin, then replace CORS with the actual frontend origin once step 4 completes. This two-step setup avoids wildcard CORS; the backend can be healthy while allowing no browser frontend yet.

After approval, create the service and wait for it to become healthy. Record the actual backend origin, such as `https://<API_SERVICE>.onrender.com`. Placeholder hostnames in this repository are not live URLs.

## 4. Frontend — Render Static Site

Choose **New → Static Site**, select the same private repository, and set:

| Field | Value |
| --- | --- |
| Branch | `main` |
| Root Directory | `frontend` |
| Build Command | `npm ci && npm run build` |
| Publish Directory | `dist` |
| Auto-Deploy | **Off** |
| `NODE_VERSION` | `22` |
| `SKIP_INSTALL_DEPS` | `true` |
| `VITE_API_BASE_URL` | The actual backend **HTTPS origin**, no `/api` suffix |

`VITE_API_BASE_URL` is public configuration baked into the JavaScript bundle; changing it requires rebuilding the Static Site. Put **no database credentials or Gemini key in any frontend variable**. Vite's localhost proxy is development-only and is not used in production. The frontend calls the separate backend over HTTPS and sends no cookies.

Create/deploy the static site only after approval. Copy its assigned HTTPS origin and update the backend's `CORS_ALLOWED_ORIGINS` to exactly that value. With auto-deploy off, manually redeploy the backend to apply the environment change. No wildcard or credentialed CORS is needed.

Apply the Static Site response headers from `render.yaml` in its Headers settings (using a Blueprint applies them automatically). For the CSP, replace `https://*.onrender.com` with the **exact backend origin** before launch. Preserve `connect-src 'self' <backend-origin>`, self-only scripts/styles, `object-src 'none'`, and anti-framing rules. A custom backend domain must be added to CSP explicitly. This app has no client-side routes, so a catch-all SPA rewrite is not required.

## 5. Launch verification

Use real assigned HTTPS origins, then check:

1. `/api/health` returns 200 with only application/status fields. This is liveness, not a recurring database probe.
2. The workbench can validate and analyze the example with CORS succeeding only for the configured frontend. A preflight from another origin must not receive `Access-Control-Allow-Origin`.
3. `GET /api/analyses` returns 410; guessed item paths such as `/api/analyses/1` return 404. A second browser sees no first visitor's data. Reloading clears results.
4. Analyses return `id:null`; the `analyses` table count stays zero after demo use. No request or response text should appear in service logs.
5. For deployed commit `2f55262`, `useAi:true` returns 400. In the local Gemini-ready branch (not deployed), it returns a labeled local fallback when consent or availability is missing; even with consent and flags, the bundled transport cannot call Google. Keep the provider key unset.
6. Bodies above 128 KiB return 413. Repeated processing requests eventually return 429 with `Retry-After`; wait for the next minute before further tests. Do not load-test a free service aggressively.
7. API responses are `no-store`; Static Site security headers and CSP are present, and browser DevTools shows only HTTPS backend requests. No credentials appear in the built bundle, Git history, or logs.
8. Recheck plan selections, budgets/usage, disabled auto-deploys, and GitHub repository privacy. Launch does not require making source public.

## 6. Local Docker verification

From the project root:

```sh
docker build -t apiforge-ai:portfolio backend
```

The multi-stage build runs backend tests before producing a minimal JRE image. To run the automated production runtime test against an isolated local PostgreSQL container with a generated, one-day TLS certificate:

```sh
./scripts/smoke-docker.sh apiforge-ai:portfolio
```

The script creates and cleans up only its own local test containers/network, checks the production profile and assigned port under a 512 MiB memory limit, verifies TLS and zero saved analyses, and checks that history and AI are blocked. It uses fake credentials and does not contact Neon or Google. Docker, OpenSSL, curl, and bash are required. GitHub Actions also builds the complete image and runs this test.

The Docker build copies only the JAR into the final image. For runtime verification, pass production values via a local ignored env file (not shell arguments or committed files):

```sh
docker run --rm --name apiforge-ai-review \
  --env-file /path/to/ignored-production.env \
  -e PORT=10000 -p 127.0.0.1:10000:10000 \
  --memory=512m --cpus=0.5 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m \
  --cap-drop=ALL --security-opt=no-new-privileges \
  apiforge-ai:portfolio
```

This connects to the database named in that file; use a test database only and approve any cloud connection first. No Neon connection has been tested by this preparation work. The Dockerfile optionally accepts BuildKit `maven_settings` and `java_truststore` secrets for trusted enterprise build proxies; standard Render builds need neither. Never disable TLS verification to make a build pass.

## 7. Free-tier limitations and costs

Checked against official documentation on 2026-11-11; recheck dashboards at deployment time:

- **Render Free Web Service:** idles after 15 minutes and can take about a minute to wake. Free instance hours are shared across the workspace (750/month); service availability can stop at limits. Filesystem writes are ephemeral. No free horizontal scaling or persistent disk. Bandwidth/build limits apply, and supplementary usage can be billable when a payment method exists. Excess service-initiated traffic, including external database access, can also cause suspension. See [Render Free](https://render.com/docs/free).
- **Render Static Site:** CDN hosting includes HTTPS and does not have the backend's idle wake-up behavior, but builds and bandwidth count toward workspace allowances. See [Static Sites](https://render.com/docs/static-sites).
- **Neon Free:** current documentation lists 100 CU-hours per project, 1 GB database storage per project (20 GB total account cap), and 5 GB public transfer per project, with compute scaling to zero after five idle minutes. Limits and region availability can change. Do not upgrade automatically when a cap is reached. See [Neon plans](https://neon.com/docs/introduction/plans).
- **Cold starts:** Render and Neon can both be asleep. The UI waits up to 90 seconds and gives a retry message for unavailable or HTML wake-up responses; it never retries submissions automatically. A brief outage is possible and unsuitable for uptime guarantees. Do not add artificial keep-alive jobs to bypass provider limits.
- **GitHub Actions:** private repositories use the account's included runner minutes and storage allowances. Check Actions billing and spending controls before enabling extra workflows; this project has timeouts/concurrency cancellation, no paid runner labels, no deployment job, and no artifacts uploaded.
- **Gemini:** live usage is prohibited in this preparation release: no live transport exists. Any future integration must stay on genuinely free, eligible service; billing, cards, subscriptions, credits, paid upgrades, and paid fallbacks are permanently prohibited. Quota exhaustion always means local analysis. See [the zero-budget plan](GEMINI_ZERO_BUDGET.md).

Provider docs for the configuration: [Web Services](https://render.com/docs/web-services), [Blueprint fields](https://render.com/docs/blueprint-spec), [monorepo root paths](https://render.com/docs/monorepo-support), [Neon JDBC](https://neon.com/docs/guides/java).

## Rollback and removal

Keep the repository private and secrets in provider settings. With auto-deploy off, pushing source does not redeploy an existing Render service. Use a known, security-reviewed commit for rollback; the original MVP has shared history and must **not** be exposed publicly. Suspend/delete only this app's services and Neon project when no longer needed. Database deletion is destructive and needs explicit approval; deleting Render services does not automatically delete a Neon project. No cleanup action has been performed by these instructions.
