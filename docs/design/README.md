# Design specs: replatform phases 3–6

Spec-driven design for moving `j2ee-loan-service` from the legacy Java 8 WAR to the target in
[hocragekey/enterprise-engineering-standards](https://github.com/hocragekey/enterprise-engineering-standards)
(standards revision `750ab9e`). Covers phases 3–6 of the approved
*Standards Assessment and Migration Sequence* (runtime/structure, PostgreSQL data layer,
API/operability/auth, events/resilience). Phases 0–2 and 7–9 are out of scope here.

| Document | Purpose |
|----------|---------|
| [prd-replatform.md](prd-replatform.md) | **What** and **why**: numbered requirements, acceptance criteria, control IDs, status |
| [technical-design-replatform.md](technical-design-replatform.md) | **How**: module structure, class→module map, API, schema, events, config, resilience, PR plan |
| [behavior-parity.md](behavior-parity.md) | Business-rule inventory, quirk register, golden results from the legacy code, parity test strategy |
| [open-decisions.md](open-decisions.md) | Decisions that are **not yet made**, each with a recommendation |
| [openapi/loan-service-v1.yaml](openapi/loan-service-v1.yaml) | OpenAPI 3.1 sketch of the `/v1` JSON API |

## How these documents are used

1. The user reviews and approves the PRD and the open decisions. Until then every requirement is `Proposed`.
2. Each implementation PR (listed in the technical design, section 13) cites the PRD requirement IDs it
   implements and the control IDs it closes, and moves those requirements to `In progress` / `Done`
   in the same PR.
3. Any intentional behavior change must first be added to the PRD behavior-change register (`BC-*`)
   and approved; parity tests are then updated in the same PR as the change.
4. Spec changes go through PRs like code (`DEL-01`, `DEL-04`).

## Status legend (PRD)

| Status | Meaning |
|--------|---------|
| `Proposed` | Drafted, awaiting user approval |
| `Blocked: D-n` | Cannot be finalized until open decision D-n is made |
| `Approved` | Accepted by the user; ready to build |
| `In progress` | An open PR implements it |
| `Done` | Merged with evidence (test or doc path) |
| `Deferred` | Moved out of phases 3–6 |
