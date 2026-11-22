<!--
Derived from hocragekey/enterprise-engineering-standards templates/pr-body.md (DEL-02).
List at least the `must` controls touched by this change. Mark sections n/a rather than deleting them.
-->

## Summary

<!-- What changed and why. Call out any business-behavior change prominently. -->

## Standards evidence

| Control ID | How met | Evidence (path / test) |
|------------|---------|------------------------|
| | | |
| | | |

## Tests

- [ ] Documented test command passed locally or in CI (`DEL-03`)
- Command: `mvn -B verify`
- Notes:

## Resiliency / cyber (if applicable)

- [ ] Health/readiness still works locally (`RES-01`)
- [ ] No secrets committed (`SEC-01`)
- [ ] Validation / error envelope covered (`SEC-02`, `ARCH-03`)
- [ ] Failure path tested where touched (`RES-10`, `TEST-05`)

## Platform alignment — if applicable

- [ ] Target compute/data/messaging match [platform.md](https://github.com/hocragekey/enterprise-engineering-standards/blob/main/standards/platform.md) (no alternate-stack proposal)
- [ ] ECS/Fargate + ECR intent documented (`PLAT-01`, `DEL-08`) or n/a
- [ ] Multi-region active-active target documented (`PLAT-10`, `RES-08`) or n/a
- [ ] Aurora PostgreSQL Global Database + write forwarding / write policy (`PLAT-04`) or n/a
- [ ] Idempotent writes where required (`ARCH-04`) or n/a
- [ ] Config/secrets provider path clear (`PLAT-06`)

## Infrastructure (CloudFormation) — if applicable

- [ ] Templates updated / added (`IAC-01`, `IAC-02`)
- [ ] No secrets in templates or param files (`IAC-03`)
- [ ] Local validation run (`IAC-05`): `________________`
- [ ] Live deploy: done / not in scope this PR

## Deferred / out of scope

<!-- `should` items or platform work not done in this PR (e.g. live multi-region, live stack deploy) -->

## Risk and rollback

- Risk:
- Rollback:
