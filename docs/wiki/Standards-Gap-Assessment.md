# Standards Gap Assessment

Assessment of the current `main` (`ded92cb`) against [enterprise-engineering-standards@750ab9e](https://github.com/hocragekey/enterprise-engineering-standards), following `templates/compliance-report.md`.

## Context

- **Date:** 2026-11-22
- **Service tier:** standard (not declared in README; standards default)
- **Scope of review:** full service (code, build, deploy scripts)
- **Method:** full source read, `mvn test`, read-only smoke calls to integration

This is a baseline. No fixes are included; "In-scope now?" is left for the modernization plan.

## Summary

| Domain | pass | gap | n/a |
|--------|------|-----|-----|
| Architecture (ARCH) | 0 | 6 | 0 |
| Resiliency (RES) | 0 | 11 | 0 |
| Security (SEC) | 1 | 7 | 0 |
| Testing (TEST) | 2 | 2 | 2 |
| Delivery (DEL) | 1 | 6 | 2 |
| Observability (OBS) | 0 | 7 | 0 |
| IaC (IAC) | 0 | 9 | 1 |
| Platform (PLAT) | 0 | 9 | 1 |

## Findings

### Architecture

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| ARCH-01 Separation of concerns | must | gap | Parsing/defaulting in servlets and manager; `DocumentManager` mixes rules and SQL; JSPs read domain objects |
| ARCH-02 Extractable domain logic | must | gap | Rules callable without HTTP, but `EligibilityRules` is static, writes audit rows and needs H2 initialized (`EligibilityRulesTest` calls `DbUtil.initSchema()`) |
| ARCH-03 Standard API error envelope | must | gap | No JSON API; errors are HTML text or Tomcat default pages |
| ARCH-04 Idempotent writes | must (PLAT-10 target) | gap | Timestamp ids; no idempotency key (D3, D4) |
| ARCH-05 Configuration over hardcoding | must | gap | `ConfigHolder` static map; many hardcoded thresholds |
| ARCH-06 Dependency direction | should | gap | No ports/interfaces; domain classes `new` their DAOs |

### Resiliency

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| RES-01 Liveness and readiness | must | gap | No health endpoints (`/health` → 404) |
| RES-02 Timeouts | must | gap | No JDBC connect/query timeouts |
| RES-03 Bounded retries | must | gap | None; insert→update fallback is not a retry policy |
| RES-04 Graceful shutdown | must | gap | `contextDestroyed` only prints |
| RES-05 Dependency failure isolation | must | gap | DB failures swallowed or thrown raw; no controlled error |
| RES-06 Stateless request handling | must | gap | Per-JVM in-memory H2 is the system of record |
| RES-07 Autoscaling readiness | must | gap | No health signals, stateful, no scaling notes |
| RES-08 Multi-region active-active | must | gap | Single EC2, single region |
| RES-09 Circuit breaking | should | gap | None |
| RES-10 Failure-path verification | must | gap | No dependency-failure tests |
| RES-11 Multi-AZ | must | gap | Single instance, default VPC |

### Security

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| SEC-01 No secrets in source | must | pass | PEMs and inventory gitignored; DB creds are H2 defaults (`sa`/empty) in code, which must move to Secrets Manager once a real DB exists |
| SEC-02 Input validation | must | gap | Only `customerId` presence checked; silent defaults (D8) |
| SEC-03 Safe error/log content | must | gap | PII in logs (D10); `printStackTrace` |
| SEC-04 Dependency hygiene | must | gap | Pinned versions, but EOL/CVE-affected Log4j 1.2.17 and H2 1.4.200 (D14) |
| SEC-05 Least privilege | should | gap | No permissions note; SG open to 0.0.0.0/0 |
| SEC-06 AuthN/AuthZ | must | gap | No auth anywhere, including destructive `/admin/reload` (D1) |
| SEC-07 Injection / unsafe defaults | must | gap | SQL is parameterized (good); unescaped JSP output (D2) |
| SEC-08 Headers and transport | should | gap | HTTP only; no security headers |

### Testing

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| TEST-01 Baseline before change | must | pass | 11 characterization tests pass |
| TEST-02 Tests for new logic | must | n/a | No new logic yet |
| TEST-03 Regression for preserved behavior | must | gap | Large areas unlocked ([Testing](Testing.md#gaps)) |
| TEST-04 Coverage | should | n/a | No changes yet |
| TEST-05 Failure/validation paths | must | gap | None tested |
| TEST-06 Hermetic tests | must | pass | In-memory H2, no network at test time |

### Delivery

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| DEL-01 PR required | must | gap | Both commits on `main` were direct pushes; no branch protection observed |
| DEL-02 Standards evidence in PR | must | n/a | No PRs yet |
| DEL-03 Automated checks before merge | must | gap | No CI |
| DEL-04 Human approval | must | gap | Not enforced |
| DEL-05 Small changes | should | n/a | |
| DEL-06 Reproducible build and run | must | pass | README documents `mvn test` / `jetty:run` |
| DEL-07 Artifact hygiene | must | gap | `.gitignore` is good, but `deploy/ansible-bring-up.sh` hardcodes a personal Windows path and host IP |
| DEL-08 Container image to ECR | must | gap | No Dockerfile; WAR copied to EC2 |
| DEL-09 CI on GitHub Actions | should | gap | No workflow |

### Observability

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| OBS-01 Structured logging | must | gap | Plain pattern layout and `System.out` |
| OBS-02 Correlation ids | must | gap | None |
| OBS-03 Standard error responses | must | gap | See ARCH-03 |
| OBS-04 No sensitive data in logs | must | gap | Email bodies/addresses logged (D10) |
| OBS-05 Health signals | must | gap | None documented or exposed |
| OBS-06 Metrics/tracing | should | gap | None |
| OBS-07 SLIs | should | gap | None |

### Infrastructure as Code

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| IAC-01 CloudFormation | must | gap | AWS CLI script + Ansible; no templates |
| IAC-02 Templates with the change | must | gap | Resources created imperatively |
| IAC-03 Parameters, no secrets | must | gap | No templates |
| IAC-04 Least-privilege IAM | must | gap | No IAM defined; SG wide open |
| IAC-05 Local validation | must | gap | No templates to validate |
| IAC-06 Outputs/naming | should | gap | |
| IAC-07 Change sets | should | n/a | Nothing deployed via CloudFormation |
| IAC-08 Multi-AZ / multi-region defaults | must | gap | |
| IAC-09 Deletion protection | should | gap | |
| IAC-10 Guard | should | gap | |

### Approved platform

| Control | Sev | Result | Evidence / gap |
|---------|-----|--------|----------------|
| PLAT-01 ECS on Fargate | must | gap | Tomcat on EC2 |
| PLAT-02 ALB/API Gateway, TLS, WAF | must | gap | Public IP on port 8080, HTTP |
| PLAT-03 OIDC/OAuth2 | must | gap | No auth |
| PLAT-04 Aurora PostgreSQL Global Database | must | gap | In-memory H2; H2-specific SQL |
| PLAT-05 EventBridge / SQS | must when async | gap | Notifications and compliance events are synchronous stdout prints; natural candidates for events |
| PLAT-06 Secrets Manager / SSM | must | gap | `ConfigHolder` is the explicitly named anti-pattern; admin reload servlet |
| PLAT-07 CloudWatch / X-Ray or OTel | must (logs) | gap | stdout only, unstructured |
| PLAT-08 ECR + GitHub Actions + CloudFormation | must | gap | None |
| PLAT-09 No alternate-stack proposals | must | n/a | Applies to the plan, not the code |
| PLAT-10 Multi-region active-active | must | gap | Single region, single host |

## Explicit non-claims

- [x] Nothing in this assessment is deployed or remediated; it is a read-only baseline.
- [x] Integration environment checks were read-only HTTP calls; no infrastructure was inspected via AWS APIs.

## Local verification

- Run command: `mvn jetty:run`
- Test command: `mvn test`
- Result: 11 tests, 0 failures (2026-11-22)
