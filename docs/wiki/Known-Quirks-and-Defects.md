# Known Quirks and Defects

The README asks that existing business quirks be preserved during modernization. This page separates **business quirks** (keep unless the business decides otherwise) from **technical defects** (fix as part of modernization) so each can be decided explicitly. Items marked *locked* are asserted by an existing test.

## Business quirks (preserve by default)

| # | Behavior | Where | Locked |
|---|----------|-------|--------|
| Q1 | HELOC DTI cap 45% vs 43% for all other products | `EligibilityRules` | *locked* |
| Q2 | Mid-FICO (620–659) PERSONAL loans skip the LTV ≤ 80 rule | `EligibilityRules` | *locked* |
| Q3 | Self-employed borrowers get +0.25% rate | `RateAdjustmentHelper` | *locked* |
| Q4 | Missing employer with blank status is treated as `EMPLOYED` | `KycChecker` | *locked* |
| Q5 | Eligibility DTI uses a crude payment estimate (amount/term + 1%/12), not the amortized payment | `EligibilityRules.estimatePayment` | |
| Q6 | Mid-FICO band is hardcoded 620–659 regardless of `fico.floor` | `EligibilityRules` | |
| Q7 | Non-positive amount returns `ERR` only after FICO/DTI/LTV pass | `EligibilityRules` | |
| Q8 | Zero/unknown income → DTI 0 → passes DTI rule | `MoneyHelper.pct` | |
| Q9 | Unknown collateral types get no haircut; unknown products price at 12% | `CollateralValuator`, `InterestRateTable` | |
| Q10 | Declined applications still get a rate and document checklist | `LoanApplicationManager` | |
| Q11 | `RATE_TBL.MIN_FICO` / `MAX_LTV` exist but are ignored | `LoanDao.lookupBaseRate` | |
| Q12 | Synthetic FICO depends on SSN last-4 mod 17 | `CreditScoreCalculator` | |

## Technical defects (candidates to fix)

| # | Defect | Impact | Where |
|---|--------|--------|-------|
| D1 | `/admin/reload` is an unauthenticated GET that drops all data | Anyone (or a crawler/CSRF) can wipe the database | `AdminReloadServlet` |
| D2 | JSPs print request and DB values without escaping | Reflected/stored XSS | all JSPs, esp. `customer.jsp` |
| D3 | Application ids are `LA`/`EL` + `currentTimeMillis` | Collisions under concurrency; not idempotent | `LoanApplicationManager` |
| D4 | Insert failure of any kind falls back to `UPDATE`; `update` swallows errors | Silent data loss or overwrite | `LoanApplicationManager`, `LoanDao` |
| D5 | Watchlist path persists before underwriting | `DECISION_CD` stored `NULL` for watchlist declines | `LoanApplicationManager` |
| D6 | KYC `ERR` ignored in `checkEligibilityOnly` but terminal in `processApplication` | Quick check and full apply can disagree | `LoanApplicationManager` |
| D7 | Customer-not-found / KYC `ERR` applications are not persisted or audited at end | Gaps in the audit trail | `LoanApplicationManager` |
| D8 | Invalid `termMonths` silently becomes 36; invalid amounts become 0 | Customer gets a decision on terms they did not request | servlets, manager |
| D9 | Live bureau path returns FICO 0 | Turning off the mock declines everyone | `CreditBureauClient` |
| D10 | Full email body and addresses logged to stdout and audit | PII in logs | `NotificationDispatcher` |
| D11 | Errors swallowed across DAOs/audit (`printStackTrace`, `System.out`) | Failures invisible to operators | `db/*`, `audit/*` |
| D12 | New JDBC connection per call; no pool or timeouts | Latency and exhaustion under load | `DbUtil` |
| D13 | In-memory H2 | Data lost on restart; cannot scale beyond one instance | `ConfigHolder` `db.url` |
| D14 | Log4j 1.2.17 and H2 1.4.200 have published CVEs; Java 8 runtime | Vulnerability exposure | `pom.xml` |
| D15 | `/underwrite` reload shows fewer fields than the original result (schedule, docs, reason not stored) | Inconsistent view of a decision | `LoanDao`, `underwriting-result.jsp` |
| D16 | Reprocessing an existing application id (manager API only; servlets always mint a new id) re-inserts checklist rows | Duplicate `DOC_CHKLIST` rows | `DocumentManager` |
| D17 | Integration SG opens SSH and 8080 to the internet over plain HTTP | Exposure of the integration host | `deploy/provision-integration-ec2.sh` |
