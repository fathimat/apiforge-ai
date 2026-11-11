# Public-demo verification — 2026-11-11

## Passed locally

- Java 21.0.13 / Maven 3.9.9: `mvn verify`, **24 tests**, zero failures/errors; executable Spring Boot 3.5.16 JAR built.
- PostgreSQL 17.11: **7 integration tests** rerun with the real local PostgreSQL datasource; all passed.
- Frontend: **9 Vitest tests** passed; Vite production build passed with an example HTTPS backend origin.
- Playwright + system Chromium: **1 end-to-end test** passed against the PostgreSQL-backed API, including two isolated browser visitors, no shared history, reload privacy, and mobile overflow.
- `npm audit`: **zero known vulnerabilities** across production and development packages after updating Vitest. This is not a full Java/container vulnerability audit.
- Render Blueprint: passed validation against the current official JSON schema.
- GitHub Actions workflow: actionlint v1.7.12 passed. Third-party Actions references use resolved commit SHAs; jobs have read-only repository permissions, timeouts, and no deployment steps.
- Bash Docker smoke-test script passed syntax validation.
- Production runtime smoke test passed using the Dockerfile's runtime stage and the locally verified JAR: real `prod` profile, assigned port 10000, UID 10001, read-only filesystem, 512 MiB memory and 0.5 CPU, local PostgreSQL with hostname/certificate-verified TLS, schema migration, zero saved analyses, HTTP 410 history, and HTTP 400 AI requests. Test containers, their data volumes, and test networks were removed by the script.
- The staged source is checked for credential patterns and known secret environment values before commit/push. Example environment files and test-only credentials are intentionally non-secret.

## Limits of verification

The complete multi-stage Docker build was attempted repeatedly but this executor's VFS Docker storage exhausted its roughly 8 GB filesystem while unpacking the Maven build image. Clearing task build cache allowed a separate runtime image build and full production runtime smoke test to pass. The complete Docker build remains unverified locally; CI includes the full image build and the same verified-TLS smoke test on a hosted runner with more disk. Check that job before approving deployment.

No Render service, Static Site, Neon project, cloud database connection, Google API call, paid resource, or deployment has been created by this preparation work. Actual Render headers, browser CORS from assigned service URLs, Neon trust/connectivity, and provider billing settings still need the runbook's post-approval verification. Gemini is blocked in the public analysis service regardless of environment flags.

Environment-specific caches, JDK/Maven tools, temporary TLS certificates, Docker build logs, and proxy trust settings remain outside the repository under `/workspace/work` or temporary directories. Production images and repository examples contain no such credentials or proxy configuration.
