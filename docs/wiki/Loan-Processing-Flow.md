# Loan Processing Flow

Two entry points in `LoanApplicationManager` carry all business behavior.

## `processApplication` (`POST /apply`)

```mermaid
flowchart TD
    A[Start] --> B{applicationId empty?}
    B -- yes --> B1["id = 'LA' + currentTimeMillis"]
    B -- no --> C
    B1 --> C["status=IN_PROCESS; audit 'process start'"]
    C --> D{customer loaded?}
    D -- no --> D1["status=ERR, elig=ERR, lastError='customer not found'\n(not persisted)"] --> Z[Return]
    D -- yes --> E["KycChecker.verify"]
    E -- WL --> E1["elig=WL, status=DECLINED\nINSERT LN_APPL\nunderwrite → D\nnotify"] --> Z
    E -- ERR --> E2["elig=ERR, status=ERR\n(not persisted, no notify)"] --> Z
    E -- OK --> F["CreditBureauClient.pullFico → ficoScore"]
    F --> G["CollateralValuator.value → adjusted value, ltvPct"]
    G --> H["EligibilityRules.evaluate → Y / N / ERR, dtiPct"]
    H --> I["InterestRateTable.baseRateFor → baseRate"]
    I --> J["RateAdjustmentHelper.adjust → finalRate"]
    J --> K["DocumentManager.buildChecklist → INSERT DOC_CHKLIST"]
    K --> L["UnderwritingEngine.decide → A / D / R"]
    L -- A or R --> M["PaymentScheduleBuilder.build\nstatus = APPROVED or REFER"]
    L -- D --> N["status = DECLINED"]
    M --> O
    N --> O["INSERT LN_APPL (on any exception → UPDATE)"]
    O --> P["NotificationDispatcher.notifyDecision"]
    P --> Q["audit 'process end status=…'"] --> Z
```

Notes on ordering that the current behavior depends on:

1. Pricing, documents and underwriting run **even when eligibility is `N` or `ERR`**. A declined application still gets a base/final rate and a document checklist.
2. `DOC_CHKLIST` rows are written **before** the `LN_APPL` row.
3. On the watchlist path the `LN_APPL` row is inserted **before** underwriting runs, so `DECISION_CD` is stored as `NULL` even though the in-memory result is `D`.
4. Customer-not-found and KYC `ERR` paths return without persisting the application or sending a notification.
5. The insert-then-update fallback catches **any** exception from `insert`, not only duplicate keys.

## `checkEligibilityOnly` (`POST /eligibility`)

Quick check without persisting an application.

1. Builds a `LoanApplication` with id `"EL" + currentTimeMillis`, parsing amount, term (default 36 on parse failure) and collateral from strings.
2. Customer not found → `ERR`.
3. KYC: `WL` → returns `WL`. **KYC `ERR` is ignored** here (unlike `processApplication`), so evaluation continues.
4. Credit pull → collateral valuation → `EligibilityRules.evaluate`.
5. Returns `Y` / `N` / `ERR` / `WL`.

Side effects: no `LN_APPL` row, but audit rows are written (`BUREAU`, `ELIG`, `KYC`) under the `EL…` id.

## Sequence (happy path)

```mermaid
sequenceDiagram
    participant U as Browser
    participant S as LoanApplyServlet
    participant M as LoanApplicationManager
    participant K as KycChecker
    participant B as CreditBureauClient
    participant C as CollateralValuator
    participant E as EligibilityRules
    participant P as Pricing
    participant D as DocumentManager
    participant W as UnderwritingEngine
    participant DB as H2
    U->>S: POST /apply (form)
    S->>M: processApplication(app)
    M->>DB: AUDIT_EVT process start
    M->>DB: SELECT CUST_ACCT
    M->>K: verify → OK
    M->>B: pullFico (synthetic)
    M->>C: value → LTV
    M->>E: evaluate → Y
    M->>P: base rate (RATE_TBL) + adjustments
    M->>D: buildChecklist → INSERT DOC_CHKLIST
    M->>W: decide → A
    M->>M: build amortization schedule
    M->>DB: INSERT LN_APPL
    M->>M: notify (stdout email)
    M->>DB: AUDIT_EVT process end
    S->>U: underwriting-result.jsp
```

## Observed outcomes (seed data)

From the integration environment and local tests on 2026-11-22, `POST /eligibility` with `AUTO`, 20,000 over 48 months, `AUTO` collateral 28,000:

| Customer | Synthetic FICO | Result | Why |
|----------|----------------|--------|-----|
| C10001 | 661 | `Y` | Clean path |
| C10002 | 619 | `N` | FICO below 620 floor (income 68k, DTI 37% → −30) |
| C10003 | 665 | `Y` | Self-employed −15 on score; +0.25% on rate when applying |
| C10004 | — | `WL` | Customer id on hardcoded watchlist |

FICO values are computed by `CreditScoreCalculator` (see [Business Rules](Business-Rules.md#credit-score-synthetic-bureau)).
