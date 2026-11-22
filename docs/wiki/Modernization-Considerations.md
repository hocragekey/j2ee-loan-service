# Modernization Considerations

Input for the path-forward discussion. Nothing here is decided. The target platform is fixed by [`standards/platform.md`](https://github.com/hocragekey/enterprise-engineering-standards/blob/main/standards/platform.md) (`PLAT-09`: no alternate stacks); the open questions are about **how** to get there and **how far to decompose**.

## Fixed target (from standards)

| Concern | Target | Local stand-in |
|---------|--------|----------------|
| Compute | ECS on Fargate, ≥2 regions, multi-AZ (`PLAT-01`, `PLAT-10`, `RES-11`) | local process / container |
| Ingress | ALB per region + TLS + WAF; Route 53 or Global Accelerator (`PLAT-02`) | localhost |
| AuthN/Z | OIDC/OAuth2 (`PLAT-03`, `SEC-06`) | test token |
| Data | Aurora PostgreSQL Global Database + write forwarding, schema per service (`PLAT-04`) | local PostgreSQL / H2 in PG mode |
| Events / queues | EventBridge, SQS + DLQ (`PLAT-05`) | in-process fake |
| Secrets / config | Secrets Manager, SSM Parameter Store (`PLAT-06`) | env vars |
| Observability | CloudWatch structured logs, X-Ray or OTel (`PLAT-07`) | JSON to stdout |
| Delivery | GitHub Actions → ECR image → CloudFormation (`PLAT-08`, `IAC-01`) | `mvn test`, `docker build` |

## Natural seams in the current code

| Seam | Current code | Notes |
|------|-------------|-------|
| Decision core (pure rules) | `eligibility`, `credit/CreditScoreCalculator`, `collateral`, `pricing/RateAdjustmentHelper`, `underwriting`, `schedule` | Mostly pure functions on `LoanApplication`; main blockers are the static `EligibilityRules` and its audit side effect |
| Customer data | `customer`, `CustomerDao`, `CUST_ACCT` | Read-only in practice; holds PII |
| Application record | `LoanDao`, `LN_APPL`, `DOC_CHKLIST` | Needs idempotent ids (`ARCH-04`) for write forwarding |
| Rates reference data | `InterestRateTable`, `RATE_TBL` | Small, read-mostly |
| Notifications | `notify` | Synchronous stdout today; candidate for EventBridge/SQS |
| Audit / compliance | `audit` | Every module writes directly; candidate for events or a shared library |
| Bureau / KYC | `CreditBureauClient`, `KycChecker`, `WatchlistScanner` | Mocked; need timeouts, retries, failure isolation once real |
| Presentation | JSPs + servlets | Replace with JSON API (`ARCH-03`) and decide UI separately |

## Paths to discuss

| Option | Shape | Pros | Cons |
|--------|-------|------|------|
| A. Modernize in place (modular monolith) | One Spring Boot (or similar) service on Java 17/21: JSON API, PostgreSQL, health, structured logs, container, CloudFormation | Fastest route out of the data center; keeps behavior in one place; lowest risk to quirks | Still one deployable; decomposition deferred |
| B. Modular monolith, then strangle | A first, with internal module boundaries enforced; extract services (e.g. notifications, customer) later behind the same API | Incremental; each extraction is a small PR | Two phases of work |
| C. Decompose now | Separate services per seam (customer, decisioning, application, notification) with schema per service | Matches end-state ownership | Most effort and risk; distributed transactions around `processApplication`; harder to prove behavior parity |

All options need the same foundation work first: characterization tests for the untested rules ([Testing](Testing.md#gaps)), replacing `ConfigHolder`, removing `/admin/reload`, and PostgreSQL-compatible SQL.

## Open decisions

1. **Path:** A, B or C above.
2. **Runtime/framework:** Java 17 or 21; Spring Boot vs. other (standards do not lock the framework).
3. **UI:** keep server-rendered pages, build a separate frontend, or API-only?
4. **Quirks:** which items in [Known Quirks and Defects](Known-Quirks-and-Defects.md) to preserve, and which defects (D1–D17) to fix and when.
5. **Service tier:** confirm `standard` vs `tier-0` (changes several control severities).
6. **External integrations:** real credit bureau, KYC/sanctions provider and email/notification channel, or keep mocks for now.
7. **Data:** what goes in each schema; whether the customer table is owned by this service or another system.
8. **Identity:** which OIDC provider and what roles (applicant, underwriter, admin).
9. **Integration environment:** keep the EC2 host during the migration or replace it with the first Fargate environment.
10. **Spec format:** PRD / spec per phase before implementation.
