# Testing

Run with `mvn test` (JUnit 4, Surefire 2.22.2). Baseline on 2026-11-22: **11 tests, 0 failures**. Tests use the real in-memory H2 and static `AppContext`; there are no mocks or fakes.

| Test class | Tests | What it locks |
|------------|-------|---------------|
| `eligibility/EligibilityRulesTest` | `helocAllowsHigherDtiThanDefaultProducts`, `ficoBelow620IsHardDecline`, `midFicoRequiresLtvAtMost80ForSecuredProducts`, `midFicoPersonalUnsecuredStillEligibleWhenDtiOk` | DTI caps 43/45, FICO floor message, mid-FICO LTV 80 rule, PERSONAL exception |
| `manager/LoanApplicationManagerTest` | `watchlistCustomerReturnsWlAndDeclines`, `happyPathApprovesSeedCustomer`, `selfEmployedGetsUndocumentedRateBump`, `missingEmployerStillProcessesAsEmployed` | End-to-end orchestration with seed data, +0.25% self-employed bump, employment defaulting |
| `schedule/AmortizationCalculatorTest` | `monthlyPaymentHappyPath`, `scheduleLengthMatchesTermAndPaysDown`, `zeroRateSplitsPrincipalEvenly` | Payment range, schedule pays to 0, zero-rate path |

## Gaps

No tests for:

- Servlets / HTTP behavior, JSP rendering, `/admin/reload`
- `UnderwritingEngine` refer threshold (> 250,000) and stipulations (FICO < 680)
- `RateAdjustmentHelper` FICO ≥ 740, FICO < 660 and LTV > 90 adjustments
- `CollateralValuator` haircuts and `LtvCalculator`
- `CreditScoreCalculator` scoring table; `bureau.mock=N` path
- `KycChecker` name/SSN rules; `WatchlistScanner` last-name match
- `DocumentManager` checklist combinations
- Persistence round-trip (`LoanDao`), insert→update fallback, `/underwrite` reload
- Any dependency failure (DB down, bureau failure) or invalid input path

The monthly-payment assertion is range-based (380–400) rather than exact, and the happy-path test only checks that a final rate exists, so small numeric regressions would pass. Several tests depend on shared static state (`AppContext.reload()` in `setUp`).
