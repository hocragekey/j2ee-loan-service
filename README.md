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

## Target AWS infrastructure (CloudFormation)

The multi-region target (ECS/Fargate + ECR, ALB with TLS and WAF, Aurora PostgreSQL Global Database with write forwarding, Route 53 latency routing) is templated in [`infra/`](infra/README.md): stack order, parameters, integration deploy commands, and local validation (`infra/scripts/validate.sh`). These templates are not deployed by this repository; the legacy EC2/Tomcat flow above stays in place until cutover.

## Container

Interim runtime for the current WAR (`DEL-08`, `PLAT-01`): [`Dockerfile`](Dockerfile) builds and tests with Maven 3.9 + Amazon Corretto 8, then runs Tomcat 9 on the Amazon Corretto 8 JRE (Amazon Linux 2023) as non-root UID 10001. Base images and the Tomcat tarball are pinned by digest / SHA-512. Requires Docker 23+ (BuildKit).

```bash
GIT_SHA=$(git rev-parse HEAD)
docker build --platform linux/amd64 -t loan-service:$GIT_SHA .
# behind a Maven Central 429: add --build-arg MAVEN_MIRROR_URL=https://maven-central.storage-download.googleapis.com/maven2/
# behind a Docker Hub 429:    add --build-arg BASE_REGISTRY=mirror.gcr.io/library  (same digests)
docker run --rm -p 8080:8080 loan-service:$GIT_SHA
# http://localhost:8080/loan-service/
```

- JVM heap follows the container memory limit (`-XX:MaxRAMPercentage=75.0`); override with `-e CATALINA_OPTS=...`.
- `HEALTHCHECK` probes `GET /loan-service/` until Phase 5 adds `/health/live` and `/health/ready` (`RES-01`).
- H2 is in-memory, so each container starts from the seed data and loses writes on restart (same as Tomcat on EC2 today).
- CI: [`.github/workflows/container.yml`](.github/workflows/container.yml) builds the image on every PR and push to `main`, tags it with the commit SHA (no `latest`), smoke-tests it, and runs Trivy. Fixable HIGH/CRITICAL OS findings fail the build; Java library findings are report-only until the Log4j/H2 upgrade (`SEC-04`).

### Push to ECR (integration)

Account `118938645536`, repository `loan-svc-integration`, primary region `us-east-1`, replicated to `us-west-2`. Build from a clean checkout so the tag matches the code. Tags are immutable: never re-push a SHA.

```bash
GIT_SHA=$(git rev-parse HEAD)
ECR=118938645536.dkr.ecr.us-east-1.amazonaws.com

# One-time, only if the `ecr` stacks from infra/ aren't deployed yet (they own the
# repositories). Pre-creating us-west-2 keeps immutable tags + scan-on-push on the replica too.
for r in us-east-1 us-west-2; do
  aws ecr create-repository --region $r --repository-name loan-svc-integration \
    --image-tag-mutability IMMUTABLE --image-scanning-configuration scanOnPush=true
done

# One-time: replicate us-east-1 -> us-west-2. This call REPLACES the registry's whole
# replication configuration, so check `aws ecr describe-registry --region us-east-1` first
# and merge any existing rules. Only images pushed after this is set are replicated.
# Without replication, push the same tag to both regions instead (infra/README.md#ecr-replication).
aws ecr put-replication-configuration --region us-east-1 --replication-configuration \
  '{"rules":[{"destinations":[{"region":"us-west-2","registryId":"118938645536"}],"repositoryFilters":[{"filter":"loan-svc-integration","filterType":"PREFIX_MATCH"}]}]}'

# Every release: build, tag with the SHA, push once to us-east-1.
docker build --platform linux/amd64 -t loan-service:$GIT_SHA .
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $ECR
docker tag loan-service:$GIT_SHA $ECR/loan-svc-integration:$GIT_SHA
docker push $ECR/loan-svc-integration:$GIT_SHA

# Confirm the image landed in both regions (replication usually takes under a minute).
for r in us-east-1 us-west-2; do
  aws ecr describe-images --region $r --repository-name loan-svc-integration --image-ids imageTag=$GIT_SHA
done
```

The CI equivalent (GitHub OIDC role, no stored AWS keys) is documented at the bottom of `container.yml`, not enabled.

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
