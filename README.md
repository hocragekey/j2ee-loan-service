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
