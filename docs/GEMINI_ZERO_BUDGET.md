# Gemini-ready preparation — permanent ₹0 budget

This version is approved for saving on **`feature/gemini-ready-zero-budget` only**, with no merge, deployment, or activation. Existing Render Free and Neon Free services are unchanged. The deployed `2f55262` application continues to run its existing code. No billing, card, trial credit, subscription, API key, model activation, or Google inference call is authorized by this preparation.

## What works in this branch

Local rules remain the default for everyone. `useAi:false` never calls the Gemini component. `useAi:true` additionally requires `aiConsent:true` for that exchange. Absent consent, disabled policy, unsupported model, unavailable transport, AI limits, provider failure, timeout, or rejected output all return HTTP 200 with `source:local-fallback` and the original local suggestions/scenarios. Envelope errors and existing security responses retain their HTTP status codes.

The API still has no analysis history, session cookies, analysis IDs, or result persistence. `GET /api/analyses` remains 410. The database schema is unchanged and no migration is added.

The UI includes an unchecked, **disabled** future-consent checkbox. The production entry point never passes `aiAvailable=true`. Tests exercise a future enabled UI using an explicit component prop and mocked fetch; this prop is not an authorization control. Consent is cleared on edits, example reload, and clear. No `VITE_` switch, API key, or Google request exists in the frontend.

## Live transport is deliberately absent

The previous live Java HTTP code has been removed. The only bundled `GeminiTransport` implementation is `DisabledGeminiTransport`: `available()` always returns false and `exchange()` always throws without doing I/O. `GEMINI_ENABLED=true`, a populated key, or a forged consent flag cannot enable it. There is no production `HttpClient`, network adapter, paid endpoint, SDK model discovery, automatic model switch, search tool, code executor, or URL fetcher.

Mocked tests substitute the transport with Mockito, then validate the prepared HTTP request, typed response parsing, failures, and fallbacks. A real adapter is a separate code change requiring explicit approval. This is a Gemini-ready contract and orchestration implementation, **not an activatable live integration**.

## Data boundary

`StructuralSummaryFactory.Summary` contains only:

- An allowlisted HTTP method enum and HTTP status number.
- Body kind enums (EMPTY, INVALID, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL).
- Bounded counts of fields, array elements, scalar types, and nesting depth, plus a truncation flag.

All user-defined field names and all values are discarded, including harmless-looking ones. URL/hostname/path/query/fragment, pasted credentials, headers, body excerpts, and exception messages never enter the provider payload. Invalid JSON becomes only an INVALID kind; parse-error text is not copied or logged. Traversal stops at 256 nodes and depth 8; summary counts are bounded to 256. Existing incoming JSON/body limits remain unchanged.

Identical structures with different names or secrets produce identical summaries. This stronger privacy boundary reduces how specific AI advice can be: the model cannot diagnose an actual quantity value, route, contract field, or business rule it never receives. Structure counts themselves still describe the exchange; future real transfer needs informed consent and eligibility review. The backend receives the original pasted data for local validation, so visitors must still use redacted inputs.

## Output and prompt-injection controls

The prepared request separates system instructions from the enum/count-only user summary. No input text can become an instruction. No tools are enabled. Prompt wording is defense in depth, not a claim that all prompt injection is solved.

The response must contain exactly one completed, non-blocked text candidate. Its JSON must contain exactly `suggestions` (1–5 strings, max 500 characters each) and `scenarios` (1–4 objects with exactly `name`, `steps`, and `expected`, max 100/500/500 characters). Duplicate keys, trailing content, missing/unknown fields, wrong types, truncation, safety blocks, oversized output, control characters, HTML, external links, markdown fences, recognizable credential patterns, and an echo of the provider key are rejected. React continues rendering text through normal escaping.

The fixed request uses only `gemini-3.5-flash-lite`, a 20-second total deadline, one candidate, a 1,024-output-token limit, and a JSON response schema. The client rejects provider replies larger than 64 KiB before parsing. The future transport contract additionally requires a five-second connection deadline, byte bounds while reading, no redirects, and no retries. These real-network behaviors remain unimplemented and unverified because all live I/O is disabled.

## Quotas and zero-cost enforcement

The prepared AI allowance is one concurrent request, two attempts/minute, and twenty attempts/day, shared by the process. Failed attempts count too. A 429 blocks additional AI attempts for 24 hours; 401/403 block for the process lifetime; other provider/output failures cool down for a minute. The original general request/body/concurrency filters remain in place. No automatic retries occur.

These in-memory counters contain no visitor identifiers or analysis data. They reset on restarts and do not coordinate replicas. They are abuse protection, **not a permanent financial cap**. A client flag or a model ID cannot enforce Google's billing state. The current hard absence of a transport prevents all inference usage even if application counters reset.

The permanent operating rule is: use only an independently verified, eligible unpaid project and free model; never link billing, add a card, buy credits, rely on temporary trial credits, or upgrade on quota exhaustion. If free access or terms change, stop Gemini and use local analysis. No paid fallback or model substitution is permitted.

Google's documentation checked during planning lists standard `gemini-3.5-flash-lite` input/output on the free tier. This does not prove a particular account's eligibility or guarantee future free availability. The older `gemini-2.5-flash` ID is not allowed: it is deprecated and provider-side automatic routing would violate the explicit-model policy. Any model change needs review and approval, not an automatic fallback.

Google's unpaid-service terms allow product improvement and human review; confidential/personal data must not be sent. Its current terms require paid services for API clients offered to users in the EEA, Switzerland, or UK, and impose age/region restrictions. Under ₹0, those restrictions must result in local-only behavior, never paid activation. This branch intentionally implements **no unreliable client-declared region bypass**. A compliant audience/access design is still required before any real provider adapter can be approved for a public site.

Official references (recheck before any activation):

- [Model free-tier pricing](https://ai.google.dev/gemini-api/docs/pricing#gemini-3.5-flash-lite)
- [Billing/free-tier eligibility](https://ai.google.dev/gemini-api/docs/billing)
- [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Data use and audience terms](https://ai.google.dev/gemini-api/terms)
- [Deprecations](https://ai.google.dev/gemini-api/docs/deprecations)

## Disable procedure and approval boundaries

Leave `GEMINI_ENABLED=false`, `GEMINI_API_KEY` unset, and the disabled transport installed. Nothing in this preparation requires a real key. Do not put one in tests, source, chat, frontend variables, Docker build arguments, logs, or a committed env file.

After a separately approved future adapter exists, turning the flag off stops new AI calls only once the backend applies that configuration. Revoking a dedicated provider key is an independent emergency action, subject to provider propagation; suspending the backend stops new application requests. None can recall already transmitted data. No unauthenticated admin endpoint is provided.

The feature-branch push is approved. Merging into main and deployment still need separate approval. Real API testing or activation needs separate approval, confirmed unpaid-project/model eligibility, compliant audience controls, visitor disclosure/consent, and a provider policy review. **Paid services remain prohibited even if Gemini becomes unavailable.**

## Tests

With installed dependencies and cached Maven artifacts, these commands use no provider or dependency network access:

```sh
cd backend
./mvnw -o verify
cd ../frontend
npm test
VITE_API_BASE_URL=https://example-api.onrender.com npm run build
```

The example API origin is a build-time string, not contacted by the build. Maven's `-o` requires an existing dependency cache. Provider HTTP behavior is simulated with Mockito only. The full API mock-flow integration test uses H2 and verifies zero saved analyses after successful mocked AI and invalid-output fallback.

For a local browser run, start the `demo` profile against H2 on 8080 and run Playwright from the frontend. Do not point tests at the deployed Render or Neon services. The existing CI push trigger targets main only. This feature-branch push does not require a hosted CI run; no pull request or manual workflow run is authorized by this save operation.
