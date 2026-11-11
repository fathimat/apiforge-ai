# Verification — 2026-11-11

- Java 21.0.13 JDK, Maven 3.9.9: `mvn verify` passed; 11 JUnit/Mockito/Spring tests, no failures. Executable Spring Boot JAR built.
- PostgreSQL 17.11: local Compose container became healthy; Flyway migration and Hibernate schema validation succeeded.
- The 3 Spring integration tests were rerun with the PostgreSQL datasource; all passed.
- React: 3 Vitest tests passed and Vite production build succeeded.
- Playwright with system Chromium: 1 end-to-end workflow passed against the running PostgreSQL-backed application. It validates JSON, analyzes an exchange, reloads history, and checks mobile overflow.
- Desktop (1440 px) and mobile (390 px) layouts inspected using Chromium.
- Gemini was disabled. Opt-in and failure fallback behavior were tested with Mockito; a real Google API round trip was not performed.

The environment initially had a Java runtime without a compiler and no Maven. A JDK and Maven were downloaded into `/workspace/work/tools`; dependency caches and environment-specific proxy/trust configuration remain under `/workspace/work`, outside the project. No system installation, deployment, Google resources, or changes to existing repositories were made.

Local frontend: http://127.0.0.1:5173
Local backend health: http://127.0.0.1:8080/api/health

The local services were left running for review. Startup and shutdown instructions are in README.md.
