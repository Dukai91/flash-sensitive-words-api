# Verification record

Verified locally on 10 September 2026 using Java 21.0.9, Maven 3.9.11, Docker Desktop's Linux engine and actual Microsoft SQL Server 2022 CU26.

| Check | Result |
| --- | --- |
| `mvn clean verify` (via the included Maven wrapper) | 84 tests passed; zero failures, errors or skipped tests |
| `mvn clean verify -Pintegration` | The same 84 tests plus 5 MSSQL integration tests passed; none skipped |
| JaCoCo, combined unit/controller/integration run | 173 of 177 executable lines covered (97.7%); not a throughput measurement |
| `docker compose up --build -d` | Image built, SQL Server healthy, database initializer exited 0, application healthy |
| Container identity | Runtime UID/GID 999, user `app` |
| Flyway / Hibernate | Both migrations applied to fresh SQL Server; schema validation succeeded |
| Seed data | All 228 database entries matched the supplied list exactly and in seed order |
| Sanitization over HTTP | Upper/lower/mixed case, repeated/multiple terms, punctuation and word boundaries passed |
| Flash regression | `SELECT * FROM sensitiveWords` returned `****** * FROM sensitiveWords`, including through Swagger's interactive request UI |
| Live CRUD | Create/get/list/update/delete, duplicate 409, missing 404 and matcher refresh after each mutation passed |
| OpenAPI / Swagger | All six operations rendered; request examples and resolved error schemas checked; error examples carry the corresponding HTTP status |
| Health | General, liveness and readiness endpoints available; database included in readiness; other actuator endpoints not exposed |
| Repository | Generated build outputs and local secrets excluded; logical local Git commits |

The integration tests use a fresh disposable SQL Server container rather than H2 or a reused local database. Compose's first start also used a newly created named volume; later rebuilds confirmed startup with existing migrations. Temporary smoke-test terms were removed after verification.

The final review found and fixed Springdoc pruning the shared ProblemDetail schema before the response customizer added references to it. The schema is now registered alongside those references, with a regression assertion against the generated OpenAPI document. Status-specific examples prevent a 409/500 response from showing a generic 400 example.

Scope: this verifies the local implementation and container setup. The GitHub Actions workflow is supplied but has not been run on a hosted repository. No production deployment, hosted Git publication, load benchmark, distributed matcher synchronization or production authorization was performed or claimed.
