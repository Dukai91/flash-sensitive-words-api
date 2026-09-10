# Verification record

Verified locally on 10 September 2026 against implementation commit **3e7df79bc8ad187e8ab34f368339057afa6c4972**. The subsequent verification-record commit changes documentation only.

Environment: Windows, Java 21.0.9, Maven wrapper 3.9.11, Docker Desktop Linux engine, actual Microsoft SQL Server 2022 CU26. The container uses the pinned Java 21 runtime in the Dockerfile.

| Check | Result |
|---|---|
| `./mvnw -B -ntp clean verify -Pintegration` | 119 unit/controller/security tests and 11 MSSQL integration tests passed: **130 total**, zero failures/errors/skips |
| Normal suite inside Docker build | 119 tests passed without a database dependency |
| JaCoCo, clean combined run | **385/400 lines (96.25%)**, 154/184 branches (83.70%) |
| Actual MSSQL | Dedicated test database; encrypted connection with development certificate trust |
| Flyway | V1/V2 exact seeding and V3 Unicode/version upgrade; existing-term upgrade and collision rollback verified |
| Supplied dataset | All 228 source entries preserved and compared against the migration/database |
| Transactions | Flushed vocabulary/revision rollback; actual commit followed by simulated lost acknowledgement; reconciliation restores trusted state |
| Concurrency | Deterministic old/new snapshot observations; independent-instance duplicate race; replica version propagation |
| Failure behaviour | Real SQL lock deadline, unavailable startup/cache, configured stale expiry, invalid settings and recovery |
| HTTP contracts | Strict string JSON, duplicate/trailing input 400, pagination bounds, Unicode whitespace, body-byte 413 and safe violations |
| Production security | Missing/invalid JWT 401; scope separation and 403; Swagger denied; development database settings rejected |
| Timestamps | Create/read/update round trips preserve microsecond values and creation time |
| Container build | Completed with cached Maven downloads, stable JAR name and non-root runtime |
| Existing local database upgrade | V3 applied successfully; local volume retained |
| Deployed HTTP smoke | Passed against the rebuilt container, including the exact Flash example, Unicode CRUD, duplicate detection and matcher refresh |
| Swagger/OpenAPI | Effective text limit, Location header, routes and error contracts checked |
| Health | App and SQL containers healthy; liveness independent of database; readiness follows matcher freshness |
| Migration release tooling | Pinned `flyway:info` command sees all three successful SQL/Java migrations |
| JMH | Exploratory run completed; methodology, uncertainty and observations in [benchmark.md](benchmark.md) |
| Git | Implementation committed; generated outputs, logs and local secrets excluded |

The final deployed smoke command used Python 3:

```sh
python scripts/smoke.py --url http://127.0.0.1:8080
```

It deletes only its own uniquely named temporary term. Existing application data was retained. Integration tests use disposable containers/databases and clean up their own records.

## Vulnerability verification

Trivy **0.74.0** scanned the rebuilt application image's OS and Java dependencies:

```sh
trivy image --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 flash-sensitive-words:local
```

Result: **zero fixable HIGH/CRITICAL findings**, exit code 0.

Trivy recorded image identifier:

```text
sha256:a78c352579f466bdcd9df11bca5c3d2341fb01a8732be3a9187e19a06cd35289
```

The first scan identified critical advisories in Tomcat 10.1.55 and an ambiguous JDBC version finding. The tested image uses Tomcat 10.1.59 and Microsoft JDBC 13.4.0.jre11. No vulnerability suppression file was added.

This scan does not assert the absence of lower-severity or unfixed vulnerabilities, and results depend on the advisory database at scan time. CI repeats this gate on future changes.

## Scope and remaining external work

The GitHub Actions workflow is supplied and its test/build/smoke/scan steps have been exercised locally. **It has not run on a hosted repository**, and no remote repository or sharing link is configured yet.

Production JWT enforcement is implemented and authorization-tested with a mock decoder; integration with Flash's real issuer, audience, keys and issued tokens still needs staging verification. Private ingress, actual runtime grants, secrets, image signing, deployment, HA/backups and operational dashboards require Flash's infrastructure.

Cross-instance propagation is periodic eventual consistency, not an immediate global guarantee. Administrative edits intentionally retain the documented last-write-wins policy. The benchmark is not an HTTP load test or a production SLO.

See [review-resolution.md](review-resolution.md) for the disposition of every review finding.
