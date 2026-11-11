# Public-demo security review

## Findings and changes

| Original risk | Change | Remaining constraint |
| --- | --- | --- |
| Global unauthenticated history leaks other visitors' results | All history reads return 410; analyses never call a repository; no persistence flag can turn this back on | Old databases may contain earlier local records. Use a fresh Neon project; do not import real local data. |
| Body size checked only after JSON deserialization | A pre-MVC filter rejects declared or actual body sizes above 131,072 bytes, including unknown/chunked length | Cloud edge traffic and TCP buffering occur before application checks |
| Anonymous CPU/concurrency abuse | 120 requests/minute process-wide, 30/minute per socket peer, four in-flight requests; 429/503 with Retry-After | Fixed-window boundary bursts, distributed denial of service, and deliberate shared-quota exhaustion remain possible |
| Spoofed client IPs could reset a naive limiter | Forwarded and X-Forwarded-For are never used; forwarding interpretation is disabled | Render proxies can share a socket-peer identity, so different visitors can share the 30/minute quota |
| Unbounded JSON nesting/pretty printing | Jackson depth 64, strings 50,000, numeric literals 1,000, document 128 KiB; formatted output capped at 100,000 characters | Validation suggestions do not replace an API contract or schema |
| Future public AI cost/data transfer | Public service rejects useAi:true before processing; UI has no AI opt-in; deployment config sets GEMINI_ENABLED=false and no API key | AI requires a separate code change, authorization, quotas, budget controls, privacy review, and user approval |
| Cross-origin use of backend | Exact configured frontend origins, JSON-only POST endpoints, limited methods/headers, no credentialed CORS | CORS is a browser control, not authentication. Scripts outside browsers can still call the API. |
| Detailed exceptions or caches can disclose data | Generic problem responses; no request/SQL logging, exception messages, or stack traces in application error responses/logs; no-store API responses | Hosting providers can retain access metadata. Do not add request-body logging/APM capture. |
| Unsafe rendering of pasted content | React renders text, no HTML injection; static CSP/anti-framing/referrer protections | No automatic secret/PII redaction; visitors must paste redacted sample data |
| Public outbound URL execution | Pasted endpoint is treated as text only | No request execution, SSRF, or executable API test runner is offered |
| Plaintext/misconfigured DB transport | prod refuses missing credentials or a JDBC URL without verify-full and a CA root; credentials separate from URL | Neon access/billing/role settings must be checked in its console |
| Container runs as root / excess heap | Minimal JRE runtime, UID 10001, bounded JVM memory, bounded Tomcat connections/threads | Free-tier memory must still be monitored with actual traffic |
| Old dependencies | Spring Boot patch upgraded; vulnerable Vitest tooling updated; npm audit and Dependabot configured | No comprehensive Java/container CVE scan or penetration test has been completed |

## Data handling

Only the current request sees its response. No analysis IDs, cookies, sessions, localStorage, IndexedDB, or cross-visitor result store are used. API results carry `Cache-Control: no-store`. The database is currently used for schema initialization/validation only; retaining PostgreSQL prepares for later authenticated features without collecting visitor analysis content. Health checks do not query the database on each request.

Textareas, browser memory, the host's request buffers, and the visitor's browser extensions can still observe pasted data. “Not saved” refers to application persistence, not a promise that infrastructure never processes data. Use the public example or redacted exchanges only. HTTPS protects transport, not malicious extensions or compromised devices.

## Limits and operations

- Limits are in-memory and per process. Restarts reset them; multiple replicas do not coordinate. Run the initial demo as one instance.
- Peer records are bounded to 2,048 per minute. Unknown peers are denied when full; an attacker cannot evict records to reset another peer's allowance.
- Health GETs and valid CORS preflights are exempt from API quotas. Render's edge protection must handle volumetric attacks; application limits are not a DDoS guarantee.
- The server accepts no compressed request bodies or multipart uploads. A 10-second connection timeout, small header limit, connection/accept limits, and thread limits reduce slow-client exposure; they do not eliminate it.
- Errors do not repeat request fields, secrets, provider text, SQL, or stack traces. Unexpected errors return a random reference; logs record only that reference and exception class.
- The backend has no redirect generation and ignores incoming proxy identity headers. TLS is terminated by Render; use its public HTTPS URL and automatic HTTP-to-HTTPS redirect.
- The static template permits connections to HTTPS onrender.com hosts because assigned service URLs are not known yet. Tighten `connect-src` to the exact backend origin before launch, especially if using a custom domain.
- HSTS is one year without includeSubDomains. Render must apply the configured static headers; verify them after deployment.

## Before approving launch

Use a fresh Neon database and dedicated role; provide secrets through Render's backend environment settings. Keep production profiles and Gemini-disabled settings intact. Verify exact CORS, CSP, HTTPS, 410 history responses, absent database writes, secret-free logs, quotas, and billing settings using the deployment checklist. Do not upload real credentials as test payloads.

This code is suitable for evaluating a constrained public portfolio demo after those checks. It does **not** provide login, user-owned history, contractual confidentiality, continuous availability, distributed abuse protection, or a guarantee of zero vulnerabilities. Do not enable storage or AI for anonymous visitors without another design and security review.
