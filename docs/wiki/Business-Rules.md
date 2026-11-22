# Business Rules

Every rule below is the **current** behavior, with the source location. Numbers are hardcoded unless marked *(config)*. "Quirk" means behavior that looks unintended but is locked by tests or the README; see [Known Quirks and Defects](Known-Quirks-and-Defects.md).

## KYC (`kyc/KycChecker`, `kyc/WatchlistScanner`)

| Rule | Result |
|------|--------|
| Customer `null` | `ERR` |
| First or last name blank | `ERR` (audit `KYC missing name`) |
| SSN last-4 blank or not 4 chars | Logged to stdout only; **continues** |
| Customer id in `{C10004}` or last name in `{LISTMAN, SANCTIONED}` (case-insensitive) | `WL` |
| Employer name blank | If employment status also blank → set to `EMPLOYED`; audit `employer missing; defaulted employment`. Quirk: silent default |
| Otherwise | `OK` |

The watchlist is two hardcoded sets; there is no external sanctions or screening call.

## Credit score (synthetic bureau)

`credit/CreditBureauClient.pullFico`: when `bureau.mock` *(config, default `Y`)* is `Y`, the score comes from `CreditScoreCalculator.synthesize` and is round-tripped through the pipe format `SCORE|MOCK|LOCAL`. When not `Y`, returns `0` (live bureau never wired), which then fails the FICO floor.

`credit/CreditScoreCalculator.synthesize`:

| Step | Adjustment |
|------|-----------|
| Base | 640 |
| Annual income ≥ 100,000 | +40 |
| Annual income ≥ 70,000 (and < 100,000) | +20 |
| Existing monthly debt / monthly income > 35% | −30 |
| … and > 45% | additional −40 |
| Employment `SELF_EMPLOYED` | −15 |
| SSN last-4 numeric | + (last4 mod 17) |
| Clamp | 300 – 850 |

Monthly income here uses `ROUND_HALF_UP` to 2 dp; DTI here excludes the proposed loan payment (unlike eligibility DTI).

## Collateral and LTV (`collateral/*`)

| Collateral type (case-insensitive) | Adjusted value |
|------------------------------------|----------------|
| `AUTO` | stated × 0.90 |
| `HOME` or `RE` | stated × 0.95 |
| `NONE`, `UNSECURED`, or missing (defaults to `NONE`) | 0 |
| Any other value | stated × 1.00 (no haircut) |

- Adjusted value rounded to 2 dp.
- `LTV % = requestedAmount × 100 / adjustedValue`, rounded to 4 dp. If adjusted value ≤ 0 or amount missing → LTV `0`.
- Quirk: unsecured products therefore show **LTV 0**, not "n/a".

## Eligibility (`eligibility/EligibilityRules.evaluate`)

Evaluated in this order; first failing rule wins.

| # | Rule | Outcome | `lastError` |
|---|------|---------|-------------|
| 0 | Application `null` / customer `null` | `ERR` | `no customer` |
| 1 | Compute DTI (always, even if later rules fail) | sets `dtiPct`, audit `dti=…` | |
| 2 | FICO missing or < `fico.floor` *(config, 620)* | `N` | `fico below floor` |
| 3 | DTI > limit: `dti.heloc` *(config, 45)* for `HELOC`, else `dti.default` *(config, 43)* | `N` | `dti exceeds limit <n>` |
| 4 | FICO 620–659 (hardcoded, ignores `fico.floor`) and product ≠ `PERSONAL` and LTV > 80 | `N` | `mid-fico ltv>80` |
| 4a | FICO 620–659 and product = `PERSONAL` | passes regardless of LTV (quirk, audit `mid-fico personal exception`) | |
| 5 | Requested amount missing or ≤ 0 | `ERR` (quirk: checked last, so a zero-amount request with bad FICO returns `N`) | |
| 6 | Otherwise | `Y` | |

**DTI formula (eligibility):**

```
monthlyIncome = annualIncome / 12            (2 dp, HALF_UP; 0 if income ≤ 0)
proposedPmt   = amount / term (2 dp) + amount × 0.01 / 12 (2 dp)   (0 if amount null or term ≤ 0)
DTI %         = (monthlyDebt + proposedPmt) × 100 / monthlyIncome  (4 dp; 0 if income = 0)
```

Quirks: the payment estimate is a "1% fudge", not the real amortized payment; zero income yields DTI `0` and therefore passes the DTI rule.

Product-level `MIN_FICO` / `MAX_LTV` in `RATE_TBL` are **not used** by eligibility.

## Pricing (`pricing/*`)

**Base rate** (`InterestRateTable.baseRateFor`): `RATE_TBL.BASE_RATE` for the product (4 dp); if no row, hardcoded fallback; if unknown product, **12.0000%**.

| Product | Base rate | `RATE_TBL.MIN_FICO` (unused) | `RATE_TBL.MAX_LTV` (unused) |
|---------|-----------|------------------------------|-----------------------------|
| AUTO | 5.9900 | 620 | 100 |
| MORTGAGE | 6.7500 | 640 | 95 |
| HELOC | 7.2500 | 660 | 85 |
| PERSONAL | 9.9900 | 660 | 0 |

**Adjustments** (`RateAdjustmentHelper.adjust`), applied cumulatively, result rounded to 4 dp:

| Condition | Adjustment |
|-----------|-----------|
| Employment `SELF_EMPLOYED` | +0.2500 (undocumented outside code; locked by test) |
| FICO ≥ 740 | −0.1250 |
| FICO < 660 | +0.5000 |
| LTV > 90 | +0.3750 |

Pricing runs for every application that reaches the bureau step, including ineligible ones.

## Document checklist (`manager/DocumentManager.buildChecklist`)

| Condition | Documents added |
|-----------|-----------------|
| Always | `GOV_ID`, `INCOME_PROOF` |
| Customer `SELF_EMPLOYED` | `TAX_RETURN_2YR`, `PNL_STATEMENT` |
| Product `MORTGAGE` or `HELOC` | `APPRAISAL`, `TITLE_REPORT` |
| Product `AUTO` | `VEHICLE_TITLE` |

Each document is inserted into `DOC_CHKLIST` with `REQ_FLG='Y'`, `RCVD_FLG='N'`. Persist failures are printed and swallowed.

## Underwriting (`underwriting/UnderwritingEngine.decide`)

| Eligibility | Condition | Decision | Reason | Stipulations | Compliance event |
|-------------|-----------|----------|--------|--------------|------------------|
| `WL` | | `D` | `watchlist` | | `UW_DECLINE_WL` |
| `N` or `ERR` | | `D` | `lastError` or `not eligible` | | `UW_DECLINE` |
| `Y` | amount > 250,000 | `R` | `amount requires manual review` | always | `UW_REFER` |
| `Y` | amount ≤ 250,000 | `A` | `auto-approve` | if FICO < 680 | `UW_APPROVE` |
| anything else | | `E` | `unknown elig <code>` | | none |

Approved amount = requested amount; approved rate = final rate. There is no counter-offer logic.

## Payment schedule (`schedule/*`)

Built only for decisions `A` and `R`.

- Monthly rate `r = annualRate / 100 / 12` (10 dp).
- Payment `P × r(1+r)^n / ((1+r)^n − 1)` with `MathContext(16)` for the power, rounded to 2 dp. Zero rate → principal / n.
- Each installment: interest = balance × r (2 dp), principal = payment − interest; **last installment** absorbs the remaining balance so the schedule ends at exactly 0.
- Term ≤ 0 or amount null → empty schedule.

## Notification (`notify/*`)

If `notify.enabled` *(config, default `Y`)*: builds a plain-text email (first name, application id, decision code, eligibility code, final rate) and **prints it to stdout**; recipient is the customer email or `ops@localhost`. Audit `NOTIFY email queued to <email>`. No SMTP or queue exists.

## Audit and compliance (`audit/*`)

- `AuditLogger.log` writes a Log4j INFO line **and** an `AUDIT_EVT` row (message truncated to 500 chars). DB failures are swallowed.
- `ComplianceEventWriter.write` prints `COMPLIANCE_EVT|<applId>|<code>` to stdout and writes an audit row with type `COMPLIANCE`.
- Event types in use: `APPL`, `KYC`, `BUREAU`, `ELIG`, `COMPLIANCE`, `NOTIFY`.
