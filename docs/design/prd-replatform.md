# PRD: Loan service replatform — phases 3–6

| | |
|---|---|
| **Status** | Draft, awaiting approval (all requirements `Proposed`) |
| **Owner** | jlebonitte (approver) |
| **Scope** | Phases 3–6 of the approved migration plan: runtime + structure, PostgreSQL data layer, API + operability + auth, events + resilience |
| **Standards** | `hocragekey/enterprise-engineering-standards@750ab9e` |
| **Code baseline** | `hocragekey/j2ee-loan-service@38dc10a` |
| **Companion docs** | [Technical design](technical-design-replatform.md) · [Behavior parity](behavior-parity.md) · [Open decisions](open-decisions.md) · [OpenAPI](openapi/loan-service-v1.yaml) |

## 1. Problem

The loan service decides eligibility, prices and underwrites loan applications, but runs as a Java 8 WAR
with an in-memory H2 database inside the JVM. It has no JSON API, no authentication, no health signals and
no timeouts, and writes unstructured logs that contain PII. It cannot run on the approved platform
(ECS/Fargate, multi-region active-active on Aurora PostgreSQL Global Database), and the standards
assessment found 57 control gaps. Phases 0–2 add guardrails, safety-net tests and urgent security fixes
on the legacy stack. Phases 3–6 rebuild the application tier so that phases 7–8 (container, CloudFormation)
have something compliant to deploy.

## 2. Goals and non-goals

**Goals**

- G1. Same business decisions as today for every documented input ([behavior parity](behavior-parity.md)).
- G2. Close the application-side `must` controls assigned to phases 3–6 in the plan (traceability in §8).
- G3. Stateless, idempotent, health-checked service that is ready for ECS/Fargate in ≥ 2 regions
  (`PLAT-01`, `PLAT-10`), runnable and testable locally with no AWS account (`DEL-06`, `TEST-06`).
- G4. Small PRs that can be built in parallel once phases 1–2 merge (technical design §13).

**Non-goals (phases 3–6)**

- Changing credit, eligibility, pricing or underwriting rules (D-8).
- Real credit bureau, KYC/sanctions or email vendors (D-7, D-16).
- Dockerfile/image pipeline (phase 7), CloudFormation (phase 8), any AWS deployment or cutover (phase 9).
- A new UI (D-3).
- Splitting into multiple services (D-2).

## 3. Users and consumers

| Consumer | Needs |
|----------|-------|
| Channel applications (internal web/mobile/branch backends) | Submit applications and quick eligibility checks over an authenticated JSON API; safe retries |
| Underwriters and ops staff | Read applications and decisions (including referrals) and customer context |
| Compliance | Durable audit trail and compliance decision events |
| Downstream services (notifications, analytics) | Domain events on EventBridge, without PII |
| SRE / platform | Health, graceful shutdown, structured logs, metrics, config via SSM/Secrets Manager |

## 4. Assumptions and dependencies

- A1. Phases 0–2 have merged: CI on PRs, characterization and golden tests (the phase‑1 PR, parallel),
  `/admin/reload` removed, JSP escaping and security headers (PR #2), PII redaction.
- A2. No durable production data exists (H2 is in-memory), so phase 4 ports the schema and migrates no data.
- A3. Requirements marked `Blocked: D-n` assume that decision's recommendation in
  [open-decisions.md](open-decisions.md) and are re-checked once the decision is made.
- A4. Docker is available locally and on CI runners for Testcontainers (GitHub-hosted Ubuntu runners have it).

## 5. Requirements

Columns: **ID** · **Requirement** · **Acceptance criteria** · **Controls** · **PR** (technical design §13) · **Status**.

### 5.1 Runtime and structure (phase 3)

| ID | Requirement | Acceptance criteria | Controls | PR | Status |
|----|-------------|---------------------|----------|----|--------|
| RT-01 | Run on Java 21 LTS and Spring Boot 3.x; remove Log4j 1.x and H2 1.4.x | `mvn -B verify` green on JDK 21; `mvn dependency:tree` shows no `log4j:log4j` or `com.h2database:h2:1.4.*`; all pre-existing tests pass unchanged except imports and wiring | SEC-04, DEL-06 | P3-1 | Blocked: D-1 |
| RT-02 | Replace the static `AppContext` service locator and `new`-ed collaborators with constructor-injected beans | `AppContext` deleted; no `static` mutable fields in production code (ArchUnit); every service bean uses constructor injection | ARCH-01, ARCH-06 | P3-3 | Proposed |
| RT-03 | Modular monolith with enforced module boundaries | ArchUnit suite in the default build: no cycles between modules; modules use each other only through their public API package; domain packages import no `org.springframework..`, `jakarta.servlet..`, `java.sql..`, `javax.sql..`; a deliberate violation in a test fixture fails the build | ARCH-01, ARCH-06 | P3-3 | Blocked: D-2, D-15 |
| RT-04 | Business rules are pure domain policies callable without Spring, HTTP or a DB | Eligibility, credit scoring, KYC, collateral, pricing, underwriting, documents and amortization are unit-tested with `new` and no DB; `EligibilityRulesTest` no longer calls `DbUtil.initSchema()`; policies return result records and mutate no inputs | ARCH-02, TEST-02 | P3-4 | Proposed |
| RT-05 | Typed configuration from a provider abstraction replaces `ConfigHolder` | `ConfigHolder` deleted; all keys in technical design §9 bound to validated `@ConfigurationProperties`; local defaults let the app start with no env; the `aws` profile imports SSM Parameter Store and Secrets Manager; invalid values fail startup; `.env.example` lists keys without secrets | ARCH-05, PLAT-06, SEC-01 | P3-2 | Proposed |
| RT-06 | Structured JSON logging | Every log line is JSON with `timestamp`, `level`, `logger`, `message`, plus `requestId`/`traceId` inside a request and `region`; ArchUnit forbids `System.out`/`System.err`/`printStackTrace`; a test captures one request's log line and asserts the fields | OBS-01, PLAT-07 | P3-5 | Proposed |
| RT-07 | No PII in logs or audit messages | A test runs the golden suite with log capture and asserts that no seed email, first/last name, SSN last‑4 or phone appears in logs or `audit_event.message`; the email body is never logged | SEC-03, OBS-04 | P3-5 | Proposed |
| RT-08 | Dependency vulnerability scan in CI | A CI job runs a dependency/CVE scan on every PR and fails on high/critical findings unless an exception is documented in the repo | SEC-04, DEL-09 | P3-5 | Proposed |
| RT-09 | Region identity comes only from config | `loan.region-id` (`LOAN_REGION_ID`) appears in logs, events and audit rows; no region or AZ literal in `src/main` (ArchUnit or grep test) | RES-08, PLAT-10 | P3-2 | Proposed |

### 5.2 Data layer (phase 4)

| ID | Requirement | Acceptance criteria | Controls | PR | Status |
|----|-------------|---------------------|----------|----|--------|
| DATA-01 | PostgreSQL is the only system of record; the service owns its schema via Flyway | App migrates an empty PostgreSQL to the current version on start (or via the Flyway CLI step); H2 removed from runtime and test classpaths; migrations are versioned and forward-only in schema `loan_svc` | PLAT-04, RES-06 | P4-1, P4-2 | Proposed |
| DATA-02 | Local run and tests are hermetic | README documents `docker compose up -d postgres` + run; `mvn -B verify` starts PostgreSQL via Testcontainers; no other network access | TEST-06, DEL-06 | P4-1 | Proposed |
| DATA-03 | Port the legacy schema and reference data | Every legacy table maps to a target table (technical design §6.1); H2-only SQL (`MERGE … KEY`, `AUTO_INCREMENT`, `DROP ALL OBJECTS`, `MODE=Oracle`) is gone; rate-card rows equal legacy `data.sql`; seed customers load only in local/test/integration profiles | PLAT-04 | P4-1 | Blocked: D-14 |
| DATA-04 | Connection pool and DB timeouts | Pool size, connection timeout, statement timeout, lock timeout and socket timeout come from config keys; a test shows `pg_sleep` beyond the statement timeout fails within the bound and maps to `DEPENDENCY_UNAVAILABLE` | RES-02 | P4-2 | Proposed |
| DATA-05 | Application-generated UUID ids | Applications and eligibility checks get UUIDs; 50 concurrent creates produce 50 distinct rows and none overwritten (TD-01) | ARCH-04 | P4-3 | Blocked: D-11 |
| DATA-06 | Atomic application create | Application, decision, document checklist, audit rows and outbox events commit in one transaction; no insert→update fallback; a fault-injection test (failing document insert) leaves no partial rows | RES-05 | P4-3 | Proposed |
| DATA-07 | Persist the full decision | Decision code (including `D` for watchlist), reason, approved amount/rate, stipulations flag, last error and DTI/LTV/FICO are stored; get-by-id returns them; the schedule is recomputed deterministically on read (TD-02, TD-03) | TEST-03 | P4-3 | Proposed |
| DATA-08 | Idempotent application create | Same `Idempotency-Key` + same request → same response and exactly one application, one set of events, one notification; same key + different request → `422 IDEMPOTENCY_KEY_REUSED`; concurrent same key → one `201`, the other `409 IDEMPOTENCY_IN_PROGRESS`; keys kept ≥ `loan.idempotency.ttl` (24h) | ARCH-04, RES-03 | P4-4 (service), P5-3 (HTTP) | Proposed |
| DATA-09 | No destructive reset path outside tests | No `DROP`/`TRUNCATE` or reset code in `src/main`; tests isolate via fresh containers or per-test transactions | SEC-06, PLAT-06 | P4-2 | Proposed |
| DATA-10 | Data access works with Aurora Global Database write forwarding | SQL uses only parameterized DML/queries; no cross-request session state; the outbox relay can be enabled per region (writer region only); technical design §6.5 lists the assumptions phase 8 must verify against write forwarding | PLAT-04, PLAT-10, SEC-07 | P4-2, P6-1 | Proposed |

### 5.3 API, operability and auth (phase 5)

| ID | Requirement | Acceptance criteria | Controls | PR | Status |
|----|-------------|---------------------|----------|----|--------|
| API-01 | Versioned JSON API | `POST /v1/applications`, `GET /v1/applications/{applicationId}`, `POST /v1/eligibility`, `GET /v1/customers/{customerId}` implemented per [OpenAPI](openapi/loan-service-v1.yaml); contract tests check responses against the spec; controllers only map DTOs and call application services | ARCH-01 | P5-2, P5-3 | Proposed |
| API-02 | Standard error envelope on every error | `{code, message, details[], requestId}` for 400/401/403/404/405/406/409/415/422/500/503, including framework errors; no stack traces or SQL in responses; at least one 4xx and one 5xx test per endpoint | ARCH-03, OBS-03, SEC-03 | P5-1 | Proposed |
| API-03 | Validate input before business logic | Required fields, types, enums (`productCode`), ranges (`amount` > 0 with ≤ 2 decimals, `termMonths` 1–600, `collateralValue` ≥ 0) and formats (`customerId` `^[A-Za-z0-9]{1,20}$`) checked; violations → `400 VALIDATION_FAILED` with per-field `details`; a test per rule | SEC-02, TEST-05 | P5-1–P5-3 | Blocked: D-9, D-10 |
| API-04 | Request id round-trip | Valid inbound `X-Request-Id` (`^[A-Za-z0-9._-]{1,64}$`) is kept, otherwise a UUID is generated; echoed on every response and in the error envelope; present in logs (MDC), audit rows and event metadata; round-trip test | OBS-02 | P5-1 | Proposed |
| API-05 | Liveness and readiness | `GET /health/live` = process up; `GET /health/ready` = DB reachable and migrations current; both unauthenticated and return status only; readiness DOWN during shutdown; test: DB stopped → ready `503`, live `200` | RES-01, OBS-05, RES-05 | P5-1 | Proposed |
| API-06 | Graceful shutdown | On SIGTERM: readiness DOWN, stop accepting new requests, finish in-flight work within `loan.shutdown.timeout` (20s), stop outbox relay and queue consumers, close the pool; covered by a test or documented check | RES-04 | P5-1 | Proposed |
| API-07 | OIDC/OAuth2 authentication | All `/v1/**` require a bearer JWT validated for signature (JWKS), `iss`, `aud`, `exp`/`nbf`; `/health/**` public; missing/invalid/expired/wrong-audience → `401 UNAUTHENTICATED` envelope (tests); local profile uses HMAC test tokens from an untracked env secret | SEC-06, PLAT-03 | P5-4 | Blocked: D-5 |
| API-08 | Scope-based authorization | Scope per endpoint as in technical design §7.4; wrong scope → `403 FORBIDDEN` (tests); deny by default for unmapped routes | SEC-06 | P5-4 | Blocked: D-5 |
| API-09 | Security headers, transport, CORS | Responses carry `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`; HSTS when the request arrived over TLS (ALB `X-Forwarded-Proto`); CORS off unless origins are configured; no HTTP session (stateless); header test | SEC-08, RES-06 | P5-4 | Proposed |
| API-10 | Retire JSP UI and legacy routes; package as a JAR | No JSP, servlet or `web.xml`; `/apply`, `/eligibility`, `/underwrite`, `/customer` return 404; executable JAR; README updated | SEC-07, DEL-06 | P5-5 | Blocked: D-3 |
| API-11 | Metrics and tracing hooks | Micrometer metrics: HTTP latency/status by route, decisions by eligibility/decision code, pool usage, outbox backlog/age, circuit-breaker state; OpenTelemetry trace context in logs; exporters are no-ops locally and CloudWatch/OTLP in the `aws` profile; a test asserts the decision counter increments | OBS-06, PLAT-07 | P5-6 | Proposed |
| API-12 | Documented SLIs | README "SLIs" section: availability (non-5xx ratio of `/v1/**`), p95 latency of `POST /v1/applications` and `POST /v1/eligibility`, readiness ratio per region; illustrative targets | OBS-07 | P5-6 | Blocked: D-4 |
| API-13 | Documented scaling and health | README "Scaling" and "Health" sections: stateless, health paths, metrics to scale on, independent scaling per region | RES-07, OBS-05 | P5-6 | Proposed |
| API-14 | Customer read exposes no more than today | `GET /v1/customers/{id}` returns the BR-25 fields only (no SSN last‑4, no phone); unknown id → `404 NOT_FOUND` | SEC-03 | P5-2 | Proposed |

### 5.4 Events and resilience (phase 6)

| ID | Requirement | Acceptance criteria | Controls | PR | Status |
|----|-------------|---------------------|----------|----|--------|
| EVT-01 | Outbound dependencies behind ports with local fakes | `CreditScoreProvider`, `WatchlistScreener`, `DomainEventPublisher`, `EmailSender` and the notification queue are interfaces; default and test profiles use in-process fakes; the default suite makes no AWS SDK calls | PLAT-05, ARCH-06, TEST-06 | P6-1 | Blocked: D-7 |
| EVT-02 | Transactional outbox, at-least-once publish | Events are written in the create transaction; the relay publishes pending rows with bounded retries and records attempts/errors; event ids are stable so consumers can dedupe; test: publisher down → API `201` and row pending; publisher back → published | PLAT-05, RES-05 | P6-1 | Proposed |
| EVT-03 | Versioned event contracts without PII | `LoanApplicationDecided` v1 and `ComplianceDecisionRecorded` v1 per technical design §8, with a JSON Schema each; payload tests; no names, email, phone or SSN in payloads | PLAT-05, SEC-03 | P6-1 | Proposed |
| EVT-04 | Asynchronous decision notification | The notification worker consumes decision messages from a queue (SQS + DLQ target, in-memory locally), renders the BR-19 template, resolves the recipient at send time and calls `EmailSender`; failures never affect the API; after `maxReceiveCount` (5) a message goes to the DLQ (tests) | PLAT-05, RES-05 | P6-2 | Proposed |
| EVT-05 | AWS adapters | EventBridge `PutEvents` publisher and SQS consumer enabled by the `aws` profile, with SDK timeouts; unit tests use stubbed SDK clients; failed entries in a `PutEvents` batch are retried, not dropped | PLAT-05, RES-02 | P6-3 | Proposed |
| EVT-06 | Multi-region event topology documented | Technical design §8.4 describes regional buses and queues, which region runs the relay, and how failover moves it; phase 8 templates follow it | PLAT-05, PLAT-10, RES-08 | P6-1 (doc) | Proposed |
| RSL-01 | Timeouts on every outbound call | Credit, KYC/watchlist, EventBridge, SQS and email calls have configured timeouts (technical design §10); tests show a slow fake is cut off at the bound | RES-02 | P6-4 | Proposed |
| RSL-02 | Bounded retries only where safe | Max 3 attempts with exponential backoff and jitter for reads, event publish (stable id) and email (dedupe key); never around the application-create transaction (clients retry with `Idempotency-Key`); tests for "succeeds after transient failure" and "gives up after N" | RES-03 | P6-4 | Proposed |
| RSL-03 | Circuit breakers and bulkheads | Per-dependency breaker (credit, KYC, events, email) and concurrency limit; unit test of closed → open → half-open → closed | RES-09 | P6-4 | Proposed |
| RSL-04 | Controlled failure behavior | Critical dependency (DB, credit, KYC) failure or open breaker → `503 DEPENDENCY_UNAVAILABLE` within the time budget, no hang, no process exit, and no `N`/`ERR` business decision recorded; non-critical (events, email) failure → request succeeds; readiness tracks the DB only | RES-05, ARCH-03 | P6-4 | Proposed |
| RSL-05 | Failure-path test suite | Tests: DB down, DB slow (statement timeout), credit timeout, credit breaker open, publisher down, poison notification message; each asserts the envelope/status and that no partial state is left | RES-10, TEST-05 | P6-5 | Proposed |

### 5.5 Parity and quality (all phases)

| ID | Requirement | Acceptance criteria | Controls | PR | Status |
|----|-------------|---------------------|----------|----|--------|
| PAR-01 | Business rules locked before code moves | Every `BR-*` in [behavior parity](behavior-parity.md) has a test (phase‑1 PR) before the PR that moves its code | TEST-01 | phase 1 | Proposed |
| PAR-02 | Golden suite passes at every layer | All 16 golden cases (§4 of behavior parity) pass against the pure domain (P3-4), PostgreSQL (P4-3) and the HTTP API (P5-3) | TEST-03 | P3-4, P4-3, P5-3 | Proposed |
| PAR-03 | Behavior changes only through the register | A PR that changes an expected value cites a `BC-*` row in §6 and updates this PRD in the same PR | TEST-01, DEL-02 | all | Proposed |
| PAR-04 | Coverage of new code | JaCoCo report in CI; ≥ 80% line coverage on domain and API packages (target, not a gate on legacy code) | TEST-04 | P3-1 | Proposed |
| NFR-01 | Container-friendly startup | Ready in < 30s with 1 vCPU / 2 GB (measured locally) so the ECS health-check grace period is predictable | RES-07 | P5-6 | Proposed |
| NFR-02 | Reproducible local workflow | README documents JDK 21, Docker, `mvn -B verify`, local run and token minting; no AWS credentials needed | DEL-06, TEST-06 | each PR | Proposed |
| NFR-03 | No secrets in source | Only placeholders in `.env.example` and `application-*.yml`; secret scanning stays green | SEC-01 | each PR | Proposed |
| NFR-04 | Parameterized SQL only | No string-concatenated SQL in `src/main` (review plus ArchUnit/grep check) | SEC-07 | P4-2 | Proposed |

## 6. Behavior-change register

Intentional, client-visible changes. Each needs approval before its PR merges. Everything else must stay
byte-for-byte equal in business outputs (behavior parity §4).

| ID | Change | Was | Will be | Why | Decision | PR |
|----|--------|-----|---------|-----|----------|----|
| BC-01 | Identifier format | `LA<epoch-ms>` application ids; `EL<epoch-ms>` quick-check ids | UUID `applicationId`; UUID `eligibilityCheckId` | Collisions overwrite data (TD-01); `ARCH-04` | D-11 | P4-3 |
| BC-02 | Transport | HTML forms and JSP under `/loan-service/*` | JSON under `/v1/*` (no context path); JSP retired | `ARCH-03`, `PLAT-03` | D-3 | P5-2…P5-5 |
| BC-03 | Invalid input | Silent defaults (term → 36, bad money → 0, unknown product → 12%), or an `ERR` page | `400 VALIDATION_FAILED` before business logic | `SEC-02` | D-9, D-10 | P5-1…P5-3 |
| BC-04 | Stored decision for watchlist declines | `DECISION_CD` NULL in the row | `D` | TD-02 | — | P4-3 |
| BC-05 | Get-application payload (additive) | Persisted columns only | Adds decision reason, approved amount/rate, stipulations, documents, schedule, last error | TD-03 | — | P4-3, P5-3 |
| BC-06 | Dependency failures | DB errors surface as "customer not found", `ERR` or a FICO‑0 decline | `503 DEPENDENCY_UNAVAILABLE`; no business decision recorded | TD-04, Q-14, `RES-05` | — | P4-2, P6-4 |
| BC-07 | Unknown customer / incomplete KYC data | `ERR` code on an HTML page | `422 CUSTOMER_NOT_FOUND` / `422 KYC_DATA_INCOMPLETE` envelopes; domain still yields `ERR` | `ARCH-03` | D-9 | P5-2, P5-3 |
| BC-08 | Authentication | None | Bearer JWT with scopes on all `/v1/**` | `SEC-06`, `PLAT-03` | D-5 | P5-4 |
| BC-09 | Notification timing | Synchronous stdout "email" inside the request | Sent asynchronously after commit (queue); same recipient and template; body never logged | `PLAT-05`, `OBS-04` | — | P6-2 |
| BC-10 | Log format | Pattern layout and stdout prints with PII | JSON, PII-free | `OBS-01`, `SEC-03` | — | P3-5 |
| BC-11 | Retry semantics | Re-submitting creates a new application | `Idempotency-Key` (required on `POST /v1/applications`) returns the original result | `ARCH-04` | — | P4-4, P5-3 |

## 7. Success measures

- All requirements `Done` with evidence; `mvn -B verify` green on every PR.
- Golden suite unchanged from phase 1 to the end of phase 6 except rows tied to approved `BC-*` entries.
- Phases 3–6 `must` controls in §8 move from `gap` to `pass` (app-level evidence). Live multi-region
  evidence stays a phase 8–9 item and is not claimed here.

## 8. Control traceability

| Control | Requirements |
|---------|--------------|
| ARCH-01 Separation of concerns | RT-02, RT-03, API-01 |
| ARCH-02 Extractable domain | RT-04 |
| ARCH-03 Error envelope | API-02, RSL-04 |
| ARCH-04 Idempotent writes | DATA-05, DATA-08 |
| ARCH-05 Config over hardcoding | RT-05 |
| ARCH-06 Dependency direction | RT-02, RT-03, EVT-01 |
| RES-01 Liveness/readiness | API-05 |
| RES-02 Timeouts | DATA-04, EVT-05, RSL-01 |
| RES-03 Bounded retries | DATA-08, RSL-02 |
| RES-04 Graceful shutdown | API-06 |
| RES-05 Failure isolation | DATA-06, API-05, EVT-02, EVT-04, RSL-04 |
| RES-06 Stateless | DATA-01, API-09 |
| RES-07 Autoscaling readiness | API-13, NFR-01 |
| RES-08 Multi-region (app hooks only) | RT-09, EVT-06 |
| RES-09 Circuit breaking | RSL-03 |
| RES-10 Failure-path verification | RSL-05 |
| SEC-01 No secrets | RT-05, NFR-03 |
| SEC-02 Input validation | API-03 |
| SEC-03 Safe errors/logs | RT-07, API-02, API-14, EVT-03 |
| SEC-04 Dependency hygiene | RT-01, RT-08 |
| SEC-06 AuthN/AuthZ | DATA-09, API-07, API-08 |
| SEC-07 Injection | DATA-10, NFR-04 |
| SEC-08 Headers/transport | API-09 |
| TEST-01 / TEST-03 Baseline and regression | PAR-01, PAR-02, PAR-03 |
| TEST-04 Coverage | PAR-04 |
| TEST-05 Failure/validation paths | API-03, RSL-05 |
| TEST-06 Hermetic | DATA-02, EVT-01, NFR-02 |
| OBS-01 Structured logging | RT-06 |
| OBS-02 Correlation ids | API-04 |
| OBS-03 Standard errors | API-02 |
| OBS-04 No sensitive logs | RT-07 |
| OBS-05 Health signals | API-05, API-13 |
| OBS-06 Metrics/tracing | API-11 |
| OBS-07 SLIs | API-12 |
| PLAT-03 OIDC | API-07 |
| PLAT-04 Aurora PostgreSQL (app side) | DATA-01, DATA-03, DATA-10 |
| PLAT-05 EventBridge/SQS | EVT-01…EVT-06 |
| PLAT-06 Secrets/config (app side) | RT-05, DATA-09 |
| PLAT-07 Observability (app side) | RT-06, API-11 |
| PLAT-10 Multi-region (app hooks only) | RT-09, DATA-10, EVT-06 |

Not in phases 3–6: `PLAT-01`, `PLAT-02`, `PLAT-08`, `DEL-08` (phase 7); `IAC-*`, `RES-11`, live `PLAT-10`/`RES-08` (phases 8–9).

## 9. Open decisions

See [open-decisions.md](open-decisions.md) (D-1 … D-16). None are decided.

## 10. Change log

| Date | Change |
|------|--------|
| 2026-11-22 | Initial draft (phases 3–6) |
