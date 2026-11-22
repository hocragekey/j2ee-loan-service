# Legacy Loan Service (J2EE-style WAR)

On-premise style **Java 8** loan origination monolith used as the *before* state for a Cognition / Devin modernization demo (Capital One hypothetical scenario).

Single deployable WAR: servlets, JSP scriptlets, JDBC, static helpers, and tightly coupled domain managers — the kind of estate that still runs on data-center app servers.

## Stack

| Piece | Choice |
|-------|--------|
| Language | Java 8 |
| Packaging | Maven WAR (`loan-service.war`) |
| Web | Servlet 3.1 + JSP |
| Data | Embedded H2 (Oracle-ish table names) |
| Logging | System.out + Log4j 1.x |
| Tests | JUnit 4 (limited baseline) |

No Spring, no DI container, no microservice split.

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

## Deploy on an AWS EC2 VM (data-center stand-in)

This is the intended runtime story for the interview: one VM, one app server, one WAR — simulating an on-prem middle-tier host.

1. Build the WAR locally: `mvn -DskipTests package` → `target/loan-service.war`
2. Launch an EC2 instance (Amazon Linux 2 / RHEL / Ubuntu), open **TCP 8080** in the security group
3. Copy artifacts to the VM:
   ```bash
   scp target/loan-service.war deploy/ec2-tomcat-setup.sh ec2-user@<vm-ip>:~
   ```
4. On the VM:
   ```bash
   chmod +x ec2-tomcat-setup.sh
   sudo ./ec2-tomcat-setup.sh ./loan-service.war
   ```
5. Browse http://\<vm-public-ip\>:8080/loan-service/

The setup script installs **Java 8 + Tomcat 9**, drops the WAR into `webapps/`, and registers a `systemd` unit named `tomcat`.

H2 is in-process memory (`DB_CLOSE_DELAY=-1`). Restarting Tomcat clears data unless you change `ConfigHolder` to a file-backed JDBC URL.

## Seed customers

| ID | Notes |
|----|--------|
| C10001 | Employed, clean happy path |
| C10002 | Missing employer name (silent KYC default) |
| C10003 | Self-employed (+0.25% rate bump in pricing) |
| C10004 | Watchlist hit → eligibility `WL` |

## Application map

- `LoanApplicationManager` — god-object orchestration (KYC → bureau → collateral → eligibility → pricing → docs → underwriting → schedule → notify → audit)
- `EligibilityRules` — static, side-effecting eligibility (primary modernization extraction target)
- Web entry points: `/apply`, `/eligibility`, `/underwrite`, `/customer`, `/admin/reload`

## Known baseline gaps (intentional)

Tight coupling, inconsistent validation/error codes (`Y`/`N`/`WL`/`ERR`), mixed logging, unauthenticated admin reload, limited automated tests. Preserve existing business quirks when modernizing (see unit tests).
