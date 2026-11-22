# HTTP Interface

Context path: `/loan-service`. All endpoints are server-rendered HTML (form posts → JSP). There is **no JSON API**, no authentication, and no health endpoint. Mappings are in `src/main/webapp/WEB-INF/web.xml`.

| Method | Path | Servlet | Behavior | View |
|--------|------|---------|----------|------|
| GET | `/` | welcome file | Menu page | `index.jsp` |
| GET | `/apply` | `LoanApplyServlet` | Show application form | `apply.jsp` |
| POST | `/apply` | `LoanApplyServlet` | Run full `processApplication` | `underwriting-result.jsp` (or `apply.jsp` with error) |
| GET | `/eligibility` | `EligibilityServlet` | Show quick-check form | `eligibility.jsp` |
| POST | `/eligibility` | `EligibilityServlet` | Run `checkEligibilityOnly` | `eligibility-result.jsp` |
| GET | `/underwrite?applId=` | `UnderwriteServlet` | Load stored application by id | `underwriting-result.jsp`; plain text `applId required` if missing |
| GET | `/customer?customerId=` | `CustomerServlet` | Look up a customer | `customer.jsp` |
| GET | `/admin/reload` | `AdminReloadServlet` | **Drops all DB objects** and reloads schema + seed. No auth, triggered by GET | plain text `OK reloaded schema/seed` |

## Parameters

### `POST /apply`

| Param | Parsing | Default / failure behavior |
|-------|---------|----------------------------|
| `customerId` | raw | blank → re-render form with `customerId required` |
| `productCode` | trimmed, upper-cased | not validated |
| `amount` | `MoneyHelper.parseMoney` (strips commas) | blank or invalid → `0` → eligibility `ERR` |
| `termMonths` | `Integer.parseInt` | invalid → `36` silently |
| `purposeCode` | raw | none |
| `collateralType` | raw | missing → `NONE` |
| `collateralValue` | `parseMoney` | invalid → `0` |

### `POST /eligibility`

`customerId`, `productCode`, `amount`, `termMonths`, `collateralType`, `collateralValue` with the same parsing (done in the manager). Blank `customerId` → `ERR` / `missing customer`. Note `productCode` is **not** upper-cased on this path before `EligibilityRules`, which upper-cases internally.

## Responses

- Success and business errors both return HTTP 200 with an HTML page. Error codes surface only as text (`ERR`, `WL`, `lastError`).
- Unhandled exceptions fall through to the Tomcat default error page (stack trace exposure depends on container config).
- `GET /underwrite` shows only persisted columns: customer details, document checklist, schedule and `lastError` are not reloaded, so the page shows fewer details than the original `POST /apply` result.

## Views

All JSPs use scriptlets and `<%= %>` expressions with no HTML escaping. `customer.jsp` echoes the `customerId` request value back into an input attribute (reflected XSS risk).
