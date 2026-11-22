# Loan Service Wiki

Reference wiki for the legacy **j2ee-loan-service** monolith. It describes what the application does today, how it is built and run, and where it stands against [hocragekey/enterprise-engineering-standards](https://github.com/hocragekey/enterprise-engineering-standards). It is the shared baseline for planning the move out of the data center.

This wiki describes the **current state** only. It does not choose a migration approach; open decisions are listed in [Modernization Considerations](Modernization-Considerations.md).

## At a glance

| Item | Current state |
|------|---------------|
| Purpose | Loan origination: KYC, credit pull, collateral, eligibility, pricing, document checklist, underwriting, payment schedule, notification, audit |
| Products | `AUTO`, `MORTGAGE`, `HELOC`, `PERSONAL` |
| Runtime | Java 8, Servlet 3.1 + JSP, packaged as one WAR (`loan-service.war`) |
| App server | Apache Tomcat 9 on a long-lived EC2 VM (data-center stand-in); Jetty for local dev |
| Data | Embedded in-memory H2 (`MODE=Oracle`), schema + seed loaded at startup, lost on restart |
| Config | Static mutable `ConfigHolder` map with hardcoded defaults |
| Logging | Mix of `System.out` and Log4j 1.2.17 |
| Delivery | Manual `mvn package` → Ansible copies WAR to Tomcat; no CI, no container, no IaC |
| Tests | 11 JUnit 4 tests (3 classes), all passing |
| Size | 41 Java classes (~1.9k lines), 6 JSPs, 5 tables |
| Service tier | Not declared in README → **standard** per standards repo default |

## Pages

| Page | What it covers |
|------|----------------|
| [Architecture](Architecture.md) | Packages, layering, object wiring, request lifecycle, diagrams |
| [Loan Processing Flow](Loan-Processing-Flow.md) | Step-by-step `processApplication` and `checkEligibilityOnly` |
| [Business Rules](Business-Rules.md) | Every rule and magic number: eligibility, credit, collateral, pricing, underwriting, documents, amortization |
| [Status and Decision Codes](Status-and-Decision-Codes.md) | Stringly-typed codes and the states they produce |
| [HTTP Interface](HTTP-Interface.md) | Servlets, parameters, responses, JSP views |
| [Data Model](Data-Model.md) | Tables, columns, seed data, what reads/writes each table |
| [Configuration and Operations](Configuration-and-Operations.md) | Config keys, logging, local run, integration environment, Ansible deploy |
| [Testing](Testing.md) | Existing tests, what they lock, coverage gaps |
| [Known Quirks and Defects](Known-Quirks-and-Defects.md) | Behavior to preserve vs. defects to decide on |
| [Standards Gap Assessment](Standards-Gap-Assessment.md) | Control-by-control `pass` / `gap` / `n/a` against the enterprise standards |
| [Modernization Considerations](Modernization-Considerations.md) | Target platform (fixed by standards), seams, and open decisions to agree on |

## Quick start (local)

```bash
mvn test                 # 11 tests, no network needed after dependencies resolve
mvn jetty:run            # http://localhost:8080/loan-service/
```

Seed customers: `C10001` (clean), `C10002` (missing employer, declines on FICO), `C10003` (self-employed), `C10004` (watchlist).

## Provenance

Written from a full read of the source at `main` (`ded92cb`), a local `mvn test` run, and read-only smoke calls against the integration environment on 2026-11-22. Standards revision: `enterprise-engineering-standards@750ab9e`.
