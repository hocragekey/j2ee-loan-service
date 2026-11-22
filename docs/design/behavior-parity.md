# Behavior parity: business-rule inventory, quirks and golden results

**Source revision read:** `main@38dc10a` (all 41 classes, 6 JSPs, `schema.sql`, `data.sql`).
**Purpose:** list every business behavior phases 3–6 must keep (`TEST-01`, `TEST-03`), separate the
quirks we keep on purpose from the technical defects we fix, and fix the expected outputs
that the new stack must reproduce.

Characterization and golden-file tests are being added in a parallel phase‑1 PR. This document is
the spec those tests implement. When that PR merges, the "Locked by" column should point at its
test names. Until then it shows only the 11 existing tests.

## 1. Parity strategy

1. **Lock first (phase 1, parallel PR).** Every `BR-*` below gets a characterization test with exact
   (not range) assertions, plus a golden test for the 4 seed customers × 4 products (§4).
2. **Port tests together with the code (phase 3).** When a class moves into a module (technical design §3),
   its tests move with it unchanged except for constructor and DI wiring. A PR fails review if it changes an
   expected value without a matching `BC-*` entry in the PRD.
3. **Domain golden suite (P3-4).** The golden cases run against the pure domain (no DB, no HTTP) through
   `OriginationService` with in-memory adapters.
4. **Persistence golden suite (P4-2/P4-3).** The same cases run against PostgreSQL (Testcontainers), and the
   stored rows are compared field by field (ids and timestamps excluded).
5. **API golden suite (P5-3).** The same cases run through `POST /v1/applications` and `POST /v1/eligibility`,
   asserting the JSON fields that map to the legacy outputs.
6. **Shadow compare (phase 9, out of scope here).** The golden inputs run against the legacy integration host
   and the new stack, and the decisions are diffed.

Golden comparisons exclude `applicationId`, timestamps and log text. They compare FICO, LTV, DTI, base
rate, final rate, eligibility code, decision code, status, first installment, document list and last error.

## 2. Business-rule inventory

Path prefix `src/main/java/com/capitalone/loansvc/` omitted.

| ID | Rule (current behavior) | Source | Locked by (today) |
|----|-------------------------|--------|-------------------|
| BR-01 | Customer lookup trims the id; empty id → no customer | `manager/CustomerManager.java` | — |
| BR-02 | Full flow: customer not found → `statusCode=ERR`, `eligibilityCode=ERR`, `lastError="customer not found"`; nothing persisted, no notification | `manager/LoanApplicationManager.java` | — |
| BR-03 | KYC: missing first or last name → `ERR` (audit `KYC missing name`). In the full flow: `status=ERR`, `elig=ERR`, return early, not persisted | `kyc/KycChecker.java`, `LoanApplicationManager` | — |
| BR-04 | KYC: SSN last‑4 empty or not 4 chars → logged only; processing continues | `kyc/KycChecker.java:25` | — |
| BR-05 | Watchlist hit when customer id ∈ {`C10004`} or last name ∈ {`LISTMAN`,`SANCTIONED`} (trimmed, case-insensitive) → `WL` (audit `KYC watchlist hit`) | `kyc/WatchlistScanner.java` | `LoanApplicationManagerTest.watchlistCustomerReturnsWlAndDeclines` |
| BR-06 | Employer missing: if employment status is also empty it is treated as `EMPLOYED`; audit `employer missing; defaulted employment` whenever the employer is missing | `kyc/KycChecker.java:30-36` | `LoanApplicationManagerTest.missingEmployerStillProcessesAsEmployed` (status only) |
| BR-07 | Full flow, watchlist: `elig=WL`, `status=DECLINED`, `lastError=watchlist`, row inserted, then decision `D` ("watchlist"), compliance `UW_DECLINE_WL`, notification sent. No FICO pull, pricing, documents or schedule | `LoanApplicationManager` | partial |
| BR-08 | Synthetic FICO (when `bureau.mock=Y`, the only configured mode): start 640; +40 if income ≥ 100,000, else +20 if ≥ 70,000; debt-only DTI (debt ÷ income/12) > 35 → −30, and > 45 → a further −40; self-employed −15; + (SSN last‑4 mod 17); clamp to 300..850. With the mock off it returns 0 | `credit/CreditScoreCalculator.java`, `credit/CreditBureauClient.java` | — |
| BR-09 | Collateral: type defaults to `NONE`; adjusted value = stated × 0.90 (`AUTO`), × 0.95 (`HOME`/`RE`), 0 (`NONE`/`UNSECURED`), unchanged for any other type; rounded to 2dp | `collateral/CollateralValuator.java` | — |
| BR-10 | LTV % = round4(amount × 100 ÷ adjusted value); 0 when adjusted value ≤ 0 or amount is null | `collateral/LtvCalculator.java` | — |
| BR-11 | DTI % = (monthly debt + estimated payment) × 100 ÷ (annual income ÷ 12, 2dp); estimated payment = amount ÷ term (2dp) + amount × 0.01 ÷ 12 (2dp); 0 when term ≤ 0 | `eligibility/EligibilityRules.java` | `EligibilityRulesTest.helocAllowsHigherDtiThanDefaultProducts` (range) |
| BR-12 | Eligibility, in this order: FICO null or < `fico.floor` (620) → `N` "fico below floor"; DTI > limit (43, HELOC 45) → `N` "dti exceeds limit 43" / "…45"; FICO 620–659 and product ≠ `PERSONAL` and LTV > 80 → `N` "mid-fico ltv>80"; amount null or ≤ 0 → `ERR`; otherwise `Y` | `eligibility/EligibilityRules.java` | `EligibilityRulesTest` (4 tests) |
| BR-13 | Base rate: `RATE_TBL` by upper-cased product → else fallback map (AUTO 5.99, MORTGAGE 6.75, HELOC 7.25, PERSONAL 9.99) → else 12.0000 | `pricing/InterestRateTable.java` | — |
| BR-14 | Rate adjustments: self-employed +0.25; FICO ≥ 740 −0.125, else FICO < 660 +0.50; LTV > 90 +0.375; round4 | `pricing/RateAdjustmentHelper.java` | `LoanApplicationManagerTest.selfEmployedGetsUndocumentedRateBump` |
| BR-15 | Document checklist: `GOV_ID`, `INCOME_PROOF`; self-employed adds `TAX_RETURN_2YR`, `PNL_STATEMENT`; MORTGAGE/HELOC add `APPRAISAL`, `TITLE_REPORT`; AUTO adds `VEHICLE_TITLE`; stored with required=Y, received=N | `manager/DocumentManager.java` | — |
| BR-16 | Underwriting: `WL` → `D` "watchlist" (+`UW_DECLINE_WL`); `N`/`ERR` → `D` with reason = last error or "not eligible" (+`UW_DECLINE`); `Y` and amount > 250,000 → `R` "amount requires manual review", stipulations = true (+`UW_REFER`); `Y` otherwise → `A` "auto-approve", stipulations = FICO < 680 (+`UW_APPROVE`); anything else → `E` | `underwriting/UnderwritingEngine.java` | partial |
| BR-17 | Status: `A` → `APPROVED`, `R` → `REFER`, else `DECLINED`. A payment schedule is built for `A` and `R` using the final rate | `LoanApplicationManager` | `happyPathApprovesSeedCustomer` |
| BR-18 | Amortization: monthly rate = annual ÷ 100 ÷ 12 (10dp); payment = P·r(1+r)^n ÷ ((1+r)^n − 1), `MathContext(16)` for the power, 2dp HALF_UP; zero rate → P ÷ n; the last installment absorbs the remainder | `schedule/AmortizationCalculator.java` | `AmortizationCalculatorTest` (range) |
| BR-19 | Decision notification (when `notify.enabled=Y`): sent for every decided application, including declines and WL; recipient = customer email, else `ops@localhost`; body = fixed template (`Dear <first name or "Customer">`, application id, decision, eligibility, rate if present) | `notify/*` | — |
| BR-20 | Compliance events `UW_APPROVE`, `UW_REFER`, `UW_DECLINE`, `UW_DECLINE_WL`, recorded as an audit row of type `COMPLIANCE` | `audit/ComplianceEventWriter.java` | — |
| BR-21 | Audit trail rows (`APPL` start/end, `BUREAU`, `ELIG`, `KYC`, `NOTIFY`, `COMPLIANCE`); message truncated to 500 chars; audit failure never fails the flow | `audit/*`, `db/AuditDao.java`, call sites | — |
| BR-22 | Quick eligibility: customer not found → `ERR`; WL → `WL`; KYC `ERR` is **ignored** and evaluation continues; FICO pull + collateral + eligibility; nothing persisted except audit rows | `LoanApplicationManager.checkEligibilityOnly` | `watchlistCustomerReturnsWlAndDeclines` |
| BR-23 | Input parsing: apply upper-cases the product; unparsable amount/collateral → 0; unparsable term → 36; apply requires `customerId` (form error); quick check requires `customerId` (`ERR` "missing customer") | `web/LoanApplyServlet.java`, `web/EligibilityServlet.java`, `util/MoneyHelper.java` | — |
| BR-24 | Get application by id returns only the persisted columns (no customer, documents, schedule or decision reason) | `web/UnderwriteServlet.java`, `db/LoanDao.findById` | — |
| BR-25 | Customer view shows id, first/last name, employer, employment status, income, monthly debt, email (not SSN last‑4, not phone) | `customer.jsp` | — |
| BR-26 | Decision descriptions: A "Approved", D "Declined", R "Refer to underwriter", other "Error / incomplete" | `underwriting/DecisionCodes.java` | — |

## 3. Quirk register

Recommendation (D-8): **preserve all quirks.** "Preserve" means the domain keeps the behavior. Where an
item is marked *API change*, only the HTTP transport rejects the input earlier (PRD `BC-*`).

| ID | Quirk | Disposition (proposed) |
|----|-------|------------------------|
| Q-01 | Unparsable term silently becomes 36 | Domain: n/a (typed input). **API change:** `400 VALIDATION_FAILED` (D-10, BC-03) |
| Q-02 | Unparsable money becomes 0, then `ERR` only if FICO and DTI checks pass | Domain: preserve check order. **API change:** amount must be > 0 (D-10, BC-03) |
| Q-03 | Quick check ignores KYC `ERR` (missing name), but the full flow stops on it | Preserve |
| Q-04 | Weak SSN only logged | Preserve (log without PII, TD-05) |
| Q-05 | Missing employer + empty status → treated as `EMPLOYED` | Preserve, implemented without mutating the customer (TD-06) |
| Q-06 | Mid-FICO band hard-coded 620–659, independent of `fico.floor` | Preserve |
| Q-07 | `PERSONAL` skips the mid-FICO LTV rule | Preserve |
| Q-08 | Secured product with collateral `NONE` → LTV 0 → passes the mid-FICO LTV rule | Preserve |
| Q-09 | Unknown collateral types get no haircut | Preserve |
| Q-10 | Unknown product → base rate 12% and default DTI limit | Preserve in domain. **API change:** `productCode` enum → `400` (BC-03) |
| Q-11 | Declined (`N`) applications are still priced, get a document checklist and are persisted | Preserve |
| Q-12 | `RATE_TBL.MIN_FICO` / `MAX_LTV` stored but never used | Preserve (columns kept, still unused) |
| Q-13 | Amount sanity check runs last (amount 0 with FICO 600 → `N`, not `ERR`) | Preserve in domain |
| Q-14 | Live-bureau mode returns FICO 0 → decline | Mode unreachable today. With real adapters, a bureau failure returns `503` instead of a decline (BC-06) |
| Q-15 | Synthetic FICO depends on SSN last‑4 mod 17 | Preserve (it is the local stand-in, D-7) |
| Q-16 | Synthetic-FICO DTI uses debt only; eligibility DTI adds the estimated payment | Preserve |
| Q-17 | Approve sets stipulations when FICO < 680; refer always sets them | Preserve |
| Q-18 | Refer threshold is strictly > 250,000 | Preserve |
| Q-19 | Referred applications get a payment schedule | Preserve |
| Q-20 | Quick checks write audit rows | Preserve (keyed by `eligibilityCheckId`) |
| Q-21 | Declines (including WL) send a decision notification | Preserve |
| Q-22 | Seed C10002 always declines (synthetic FICO 619 < 620) | Preserve (follows from BR-08) |

## 4. Golden results (legacy code, verified)

Produced by running `LoanApplicationManager.processApplication` and `checkEligibilityOnly` from compiled
`main@38dc10a` on a fresh in-memory H2 with seed data (a throwaway harness, not committed). Inputs:

| Product | Amount | Term | Collateral type | Collateral value |
|---------|--------|------|-----------------|------------------|
| AUTO | 25000 | 60 | AUTO | 30000 |
| MORTGAGE | 300000 | 360 | HOME | 400000 |
| HELOC | 50000 | 120 | HOME | 300000 |
| PERSONAL | 10000 | 36 | NONE | 0 |

| Cust | Product | FICO | LTV % | DTI % | Base | Final | Elig | Dec | Status | 1st pmt | Documents | Last error | Quick |
|------|---------|------|-------|-------|------|-------|------|-----|--------|---------|-----------|------------|-------|
| C10001 | AUTO | 661 | 92.5926 | 24.6196 | 5.9900 | 6.3650 | Y | A | APPROVED | 487.57 | GOV_ID, INCOME_PROOF, VEHICLE_TITLE | — | Y |
| C10001 | MORTGAGE | 661 | 78.9474 | 33.0434 | 6.7500 | 6.7500 | Y | R | REFER | 1945.79 | GOV_ID, INCOME_PROOF, APPRAISAL, TITLE_REPORT | — | Y |
| C10001 | HELOC | 661 | 17.5439 | 24.8914 | 7.2500 | 7.2500 | Y | A | APPROVED | 587.01 | GOV_ID, INCOME_PROOF, APPRAISAL, TITLE_REPORT | — | Y |
| C10001 | PERSONAL | 661 | 0 | 22.6449 | 9.9900 | 9.9900 | Y | A | APPROVED | 322.62 | GOV_ID, INCOME_PROOF | — | Y |
| C10002 | AUTO | 619 | 92.5926 | 44.7794 | 5.9900 | 6.8650 | N | D | DECLINED | — | GOV_ID, INCOME_PROOF, VEHICLE_TITLE | fico below floor | N |
| C10002 | MORTGAGE | 619 | 78.9474 | 56.1764 | 6.7500 | 7.2500 | N | D | DECLINED | — | GOV_ID, INCOME_PROOF, APPRAISAL, TITLE_REPORT | fico below floor | N |
| C10002 | HELOC | 619 | 17.5439 | 45.1471 | 7.2500 | 7.7500 | N | D | DECLINED | — | GOV_ID, INCOME_PROOF, APPRAISAL, TITLE_REPORT | fico below floor | N |
| C10002 | PERSONAL | 619 | 0 | 42.1078 | 9.9900 | 10.4900 | N | D | DECLINED | — | GOV_ID, INCOME_PROOF | fico below floor | N |
| C10003 | AUTO | 665 | 92.5926 | 24.4091 | 5.9900 | 6.6150 | Y | A | APPROVED | 490.50 | GOV_ID, INCOME_PROOF, TAX_RETURN_2YR, PNL_STATEMENT, VEHICLE_TITLE | — | Y |
| C10003 | MORTGAGE | 665 | 78.9474 | 31.4545 | 6.7500 | 7.0000 | Y | R | REFER | 1995.91 | GOV_ID, INCOME_PROOF, TAX_RETURN_2YR, PNL_STATEMENT, APPRAISAL, TITLE_REPORT | — | Y |
| C10003 | HELOC | 665 | 17.5439 | 24.6364 | 7.2500 | 7.5000 | Y | A | APPROVED | 593.51 | GOV_ID, INCOME_PROOF, TAX_RETURN_2YR, PNL_STATEMENT, APPRAISAL, TITLE_REPORT | — | Y |
| C10003 | PERSONAL | 665 | 0 | 22.7576 | 9.9900 | 10.2400 | Y | A | APPROVED | 323.80 | GOV_ID, INCOME_PROOF, TAX_RETURN_2YR, PNL_STATEMENT | — | Y |
| C10004 | any of 4 | — | — | — | — | — | WL | D | DECLINED | — | (none) | watchlist | WL |

`LTV 0` above is the legacy `BigDecimal.ZERO` (scale 0); the PostgreSQL column stores `0.0000`. Golden
comparisons use `compareTo`, not `equals`, for decimals.

## 5. Technical defects to fix (not quirks)

These are defects in the implementation, not business rules. Fixing them is in scope for phases 3–6. The
ones a client can observe are listed as behavior changes in PRD §6.

| ID | Defect | Fix (phase/PR) | Observable? |
|----|--------|----------------|-------------|
| TD-01 | Ids are `LA`/`EL` + `currentTimeMillis()`, so two requests in the same millisecond collide. A failed insert then falls back to `update`, which **overwrites the other application**, and `update` swallows its own errors | UUID ids; one insert inside a transaction; no fallback; idempotency key for retries (P4-3, P4-4) | Yes: BC-01 |
| TD-02 | Watchlist path inserts the row **before** the decision, so `DECISION_CD` is stored as NULL even though the response shows `D` | Persist after the decision (P4-3) | Yes: BC-04 |
| TD-03 | Decision reason, approved amount/rate, stipulations and last error are never persisted. Get-by-id cannot show them, and the schedule is lost | Persist the decision; compute the schedule on read (P4-3) | Yes: BC-05 (additive) |
| TD-04 | DB errors are swallowed or turned into `null`. A DB outage on customer lookup looks like "customer not found" | Propagate as `DependencyUnavailableException` → `503` (P4-2, P5-1) | Yes: BC-06 |
| TD-05 | PII in logs: full email body and recipient address on stdout and in `AUDIT_EVT`; customer id in the weak-SSN log; bad money and bureau payloads echoed | Redacting logs; audit stores ids and codes only (P3-5). Phase 2 may land part of this first | Yes (logs only) |
| TD-06 | `EligibilityRules` is static, writes audit rows and mutates the application; `KycChecker` mutates the customer object | Pure policies returning result records (P3-4) | No |
| TD-07 | Document and audit writes in the full flow are separate auto-commit statements whose failures are swallowed, so an application can be stored without its checklist | One transaction per application create (P4-3) | No |
| TD-08 | No connect, query or lock timeouts | Hikari plus statement/lock timeouts (P4-2) | No |
| TD-09 | Schema is created at startup on every JVM; reset uses `DROP ALL OBJECTS` | Flyway migrations; tests use fresh Testcontainers DBs (P4-1) | No |
