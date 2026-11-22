# Legacy Loan Service (J2EE-style WAR)

On-premise style **Java 8** loan origination monolith (Capital One hypothetical scenario for Cognition / Devin interview demos).

Single deployable WAR: servlets, JSP scriptlets, JDBC, and domain managers for loan eligibility and underwriting.

| Attribute | Value |
|-----------|-------|
| Service tier | **`standard`** (assumed — pending owner confirmation; see [enterprise-engineering-standards](https://github.com/hocragekey/enterprise-engineering-standards)) |
| Standards | [hocragekey/enterprise-engineering-standards](https://github.com/hocragekey/enterprise-engineering-standards) |
| CI | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — `mvn -B verify` on Java 8 |

## Deployed environments (for agents / Devin)

| Environment | Base URL | Notes |
|-------------|----------|--------|
| **integration** | http://34.226.211.125:8080/loan-service/ | Live EC2 + Tomcat stand-in for on-prem. Use this to verify servlet/UI behavior after changes. Reachable only from the CIDRs allowed in its security group (`SEC-05`, see below). |
| dev | http://loan-svc-dev.internal.capitalone.example:8080/loan-service/ | Placeholder — not a real host (demo estate inventory only). |
| prod | http://loan-svc.prod.capitalone.example:8080/loan-service/ | Placeholder — not a real host (do not target for automation). |

**Agent guidance:** Prefer the **integration** environment for smoke checks (`GET /`, `POST /eligibility`, `GET /customer?customerId=C10001`). Do not attempt to deploy to or call the placeholder `dev` / `prod` URLs. Redeploy to integration with Ansible (`ansible/site.yml` or `deploy-app.yml`) after building `target/loan-service.war`.

## Stack

| Piece | Choice |
|-------|--------|
| Language | Java 8 |
| Packaging | Maven WAR (`loan-service.war`) |
| Web | Servlet 3.1 + JSP |
| Data | Embedded H2 (Oracle-ish table names) |
| Logging | Log4j 1.x |
| Tests | JUnit 4 |

## Local build & test

```bash
mvn test
mvn package
```

Quick UI against embedded Jetty (dev only):

```bash
mvn jetty:run
# if 8080 is busy: mvn jetty:run "-Djetty.port=8088"
```

Open http://localhost:8080/loan-service/

## CI and change control

- **CI (`DEL-03`, `DEL-09`):** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `mvn -B verify` (Temurin JDK 8, Ubuntu 24.04) on every pull request to `main`, every push to `main`, and on manual dispatch. Actions are pinned to commit SHAs. Reproduce locally with `mvn -B verify`.
- **Pull requests (`DEL-01`, `DEL-02`):** all changes land on `main` through a pull request. The [PR template](.github/pull_request_template.md) follows the standards repo's `templates/pr-body.md`; list the control IDs the change touches and the evidence.
- **Branch protection (`DEL-01`, `DEL-04`):** `main` must have a ruleset that requires a pull request with at least one approving human review, requires the `mvn verify (Java 8)` status check to pass, and blocks force pushes and deletion. This is a repository-admin setting (Settings → Rules → Rulesets); it is not stored in the repo.

## Integration environment (legacy operating model)

Demo story: the **EC2 VM is already provisioned** (data-center stand-in). This repository configures it with **Ansible** and deploys one WAR to Tomcat. One-time AWS CLI bootstrap creates the host; day-2 work is Ansible.

```text
AWS CLI (once)  →  EC2 integration host
Ansible site.yml →  Java 8 + Tomcat + loan-service.war
HTTP             →  http://<public-ip>:8080/loan-service/
```

### 1. Build the WAR

```bash
mvn -DskipTests package   # → target/loan-service.war
```

### 2. Provision the integration EC2 (once)

Requires authenticated AWS CLI (`aws login`). From Git Bash or WSL:

```bash
chmod +x deploy/provision-integration-ec2.sh
./deploy/provision-integration-ec2.sh                          # SSH + 8080 open to your current public IP /32 only
INGRESS_CIDR=203.0.113.0/24 ./deploy/provision-integration-ec2.sh   # or an explicit office/VPN CIDR
```

Creates key pair + SG, launches **t3.small** Amazon Linux 2023 tagged `Name=loan-svc-integration`, and writes `ansible/inventories/integration/hosts.yml` (gitignored). PEM lands in `deploy/secrets/` (gitignored).

Inbound access (`SEC-05`) is controlled by these environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `INGRESS_CIDR` | caller's public IPv4 from `https://checkip.amazonaws.com` + `/32` | CIDR allowed on both ports |
| `SSH_INGRESS_CIDR` | `INGRESS_CIDR` | CIDR allowed on TCP 22 |
| `APP_INGRESS_CIDR` | `INGRESS_CIDR` | CIDR allowed on TCP 8080 |
| `ALLOW_OPEN_INGRESS` | `false` | must be `true` to accept `0.0.0.0/0` |
| `UPDATE_EXISTING_SG_INGRESS` | `false` | when the SG already exists: add the CIDRs above and revoke `0.0.0.0/0` on 22/8080 (otherwise the script only warns) |

Other overrides: `KEY_NAME`, `SG_NAME`, `INSTANCE_NAME`, `INSTANCE_TYPE`. If your public IP changes, re-run with `UPDATE_EXISTING_SG_INGRESS=true` (this adds the new IP; remove stale `/32` rules in the console).

#### Permissions (`SEC-05`)

- **Application:** no cloud permissions; it only uses the in-process H2 database.
- **Provision script (AWS principal):** `sts:GetCallerIdentity`; `ec2:DescribeAvailabilityZones`, `ec2:DescribeVpcs`, `ec2:DescribeImages`, `ec2:DescribeInstances`, `ec2:DescribeInstanceStatus`, `ec2:DescribeKeyPairs`, `ec2:CreateKeyPair`, `ec2:DeleteKeyPair`, `ec2:DescribeSecurityGroups`, `ec2:CreateSecurityGroup`, `ec2:AuthorizeSecurityGroupIngress`, `ec2:RevokeSecurityGroupIngress`, `ec2:RunInstances`, `ec2:CreateTags`. Run it once with a scoped role, not long-lived admin keys.
- **Ansible:** SSH as `ec2-user` with sudo on the integration host, from an address inside `SSH_INGRESS_CIDR`.

### 3. Configure and deploy with Ansible (WSL)

OpenSSH rejects PEMs on `/mnt/c` (DrvFs is world-writable). Copy the key into the WSL home first, then run the helper:

```bash
# inside WSL, from the repo checkout (e.g. /mnt/c/<path-to>/j2ee-loan-service)
bash deploy/ansible-bring-up.sh     # copies the PEM to ~/.ssh, fixes the inventory key path, waits for SSH, runs site.yml
# or, if the PEM and inventory are already in place:
bash deploy/run-ansible-site.sh
# later WAR-only from ansible/: ansible-playbook -i inventories/integration/hosts.yml deploy-app.yml
```

`run-ansible-site.sh` sets `ANSIBLE_*` env vars because Ansible ignores `ansible.cfg` on world-writable DrvFs paths.

Both scripts locate the repo from their own path and read the host from the inventory; nothing user- or host-specific is hardcoded. Optional overrides:

| Variable | Default | Used by |
|----------|---------|---------|
| `LOAN_SVC_ROOT` | parent of `deploy/` | both |
| `INVENTORY_FILE` | `ansible/inventories/integration/hosts.yml` | both |
| `WAR_FILE` | `target/loan-service.war` | both |
| `KEY_NAME` | `loan-svc-integration` | `ansible-bring-up.sh` |
| `SRC_PEM` | `deploy/secrets/$KEY_NAME.pem` | `ansible-bring-up.sh` |
| `SSH_KEY_FILE` | `~/.ssh/$KEY_NAME.pem` | `ansible-bring-up.sh` |
| `INTEGRATION_HOST` | `ansible_host` from the inventory | `ansible-bring-up.sh` |
| `SSH_USER` | `ec2-user` | `ansible-bring-up.sh` |

### 4. Hit the app / “API” surface

- UI: http://34.226.211.125:8080/loan-service/ (current integration public IP; refresh via provision script if the instance is replaced)
- Servlets: `POST /loan-service/eligibility`, `POST /loan-service/apply`, `GET /loan-service/customer?customerId=C10001`

H2 is in-process memory (`DB_CLOSE_DELAY=-1`). Restarting Tomcat clears data unless you change `ConfigHolder` to a file-backed JDBC URL.

### Manual shell fallback

[`deploy/ec2-tomcat-setup.sh`](deploy/ec2-tomcat-setup.sh) remains as the older “scp a script onto the box” path if Ansible is unavailable.

## Seed customers

| ID | Notes |
|----|--------|
| C10001 | Employed |
| C10002 | Employed |
| C10003 | Self-employed |
| C10004 | Seeded customer |

## Application map

- `LoanApplicationManager` — application processing entry point
- `EligibilityRules` — loan eligibility evaluation
- Web entry points: `/apply`, `/eligibility`, `/underwrite`, `/customer`, `/admin/reload`
