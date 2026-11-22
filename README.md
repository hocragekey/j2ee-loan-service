# Legacy Loan Service (J2EE-style WAR)

On-premise style **Java 8** loan origination monolith (Capital One hypothetical scenario for Cognition / Devin interview demos).

Single deployable WAR: servlets, JSP scriptlets, JDBC, and domain managers for loan eligibility and underwriting.

## Deployed environments (for agents / Devin)

| Environment | Base URL | Notes |
|-------------|----------|--------|
| **integration** | http://34.226.211.125:8080/loan-service/ | Live EC2 + Tomcat stand-in for on-prem. Use this to verify servlet/UI behavior after changes. |
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
./deploy/provision-integration-ec2.sh
```

Creates key pair + SG (22/8080 open for demo), launches **t3.small** Amazon Linux 2023 tagged `Name=loan-svc-integration`, and writes `ansible/inventories/integration/hosts.yml` (gitignored). PEM lands in `deploy/secrets/` (gitignored).

### 3. Configure and deploy with Ansible (WSL)

OpenSSH rejects PEMs on `/mnt/c` (DrvFs is world-writable). Copy the key into the WSL home first, then run the helper:

```bash
# inside WSL
cp /mnt/c/Users/<you>/repos/cognition-interview/j2ee-loan-service/deploy/secrets/loan-svc-integration.pem ~/.ssh/
chmod 600 ~/.ssh/loan-svc-integration.pem
# point inventory ansible_ssh_private_key_file at ~/.ssh/loan-svc-integration.pem
bash deploy/run-ansible-site.sh
# later WAR-only from ansible/: ansible-playbook -i inventories/integration/hosts.yml deploy-app.yml
```

`run-ansible-site.sh` sets `ANSIBLE_*` env vars because Ansible ignores `ansible.cfg` on world-writable DrvFs paths.

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
