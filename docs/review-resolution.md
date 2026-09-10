# Senior review resolutions

This records the disposition of the 30 review findings. A documented product policy is distinguished from an implemented correction; infrastructure credentials and deployment decisions cannot be supplied by repository code.

| # | Finding | Resolution |
|---|---|---|
| 1 | Unicode self-matching and duplicate identity | Shared simple fold for both input and keys; regression cases and exhaustive runtime invariants; V3 upgrades existing terms and refuses collisions |
| 2 | Uncertain commit | Invalidate local snapshot, return controlled failure, reconcile persisted revision; integration test commits then throws |
| 3 | Unbounded database waits | JDBC, pool and transaction budgets; real MSSQL blocking-lock test |
| 4 | ECS health contradiction | Dependency-free liveness used for replacement checks; explicit readiness/release policy and bounded stale reads |
| 5 | Unrestricted administration | Production JWT issuer/audience validation and read/write scopes; production DB guard, disabled Swagger and runtime grant script; external identity/network configuration still required |
| 6 | Stale replicas | Transactional singleton revision, consistent polling snapshots, freshness deadline and two-instance tests |
| 7 | JSON coercion/trailing/duplicate values | Strict parsing and 400 regressions for both DTOs |
| 8 | Pagination overflow | Validated long-arithmetic offset before persistence |
| 9 | Unicode spaces | Consistent blank/trim policy, including NBSP |
| 10 | Vague validation errors | Safe field/parameter violations without rejected values |
| 11 | Body parsing resource exposure | Streaming byte limit before JSON, including unknown Content-Length; production ingress rate/time limits documented |
| 12 | Mock-only rollback proof | Actual flushed MSSQL write and revision roll back on forced matcher failure |
| 13 | Weak concurrency proof | Readers explicitly observe old and new generations; independent-instance duplicate race test |
| 14 | Integration deployment mismatch | Dedicated application database and encrypted test connection; CI builds and smoke-tests Compose |
| 15 | Failure-path and timestamp gaps | Startup/configuration/freshness/recovery tests and exact timestamp round trips |
| 16 | Concurrency cleanup/deadlines | Bounded JDBC calls, test deadline, bounded executor termination and cleanup independent of Future retrieval |
| 17 | REQUIRES_NEW composition surprise | Removed independent nested transaction behaviour; reject ambient mutation/refresh transactions explicitly |
| 18 | Hosted Git delivery | Requires the owner's destination/visibility choice; hosted CI status must be verified after publication |
| 19 | Phrase shadowing | Preserved Flash's explicit example; general foo/foo-bar overlap documented and tested |
| 20 | Word/phrase/Unicode assumptions | Explicit scope and examples; no claim of comprehensive adversarial moderation |
| 21 | Full rebuild cost | Configurable vocabulary capacity, operation/rebuild timing, version-only unchanged polls and optional compilation benchmark |
| 22 | Narrow DB constraint guarantee | Clear API-only mutation contract, runtime table grants; no false claim of DB-side Java normalization |
| 23 | Lost administrative edits | Explicit last-write-wins contract retained; ETag would require an additional consumer/product contract |
| 24 | Swagger gaps | Structured Location, effective limit schema, 413/503 and other errors; handler metadata replaces path-substring inference; contract assertions |
| 25 | Timestamp precision | Microsecond values consistent with DATETIME2(6), verified across create/read/update |
| 26 | Package cycles | Shared rules moved to domain; DTOs no longer import entities |
| 27 | Unmeasured allocations/performance | Lazy builder and direct code-point loop; optional JMH profile and qualified local observations |
| 28 | Thin diagnostics | ID, revision, duration and reconciliation outcome logs without message contents; durable actor audit/metrics remain platform enhancements |
| 29 | Build/repository hardening | Stable artifact name, pinned runtime/build digests and action SHAs, wrapper checksum, secret exclusions, cache mount, Compose smoke and Trivy CI, Dependabot |
| 30 | README precision/walkthrough | Rewritten against actual guarantees, upgrade procedure, startup wait, production profile and verification scope |

## Deliberate boundaries

The submission includes a small standard Spring Security resource-server configuration, not a custom identity provider. Production still needs Flash-issued credentials, secrets, network enforcement and least-privilege SQL login provisioning.

Polling is eventual consistency, not instantaneous global publication. The 30-second poll and five-minute freshness defaults are visible settings requiring agreement with Flash.

Administrative updates remain last-write-wins. Implementing ETags without consumer agreement would change the API contract rather than repair an existing implementation error.

Durable audit storage, centralized metrics/tracing, load-balancer provisioning, image signing, HA/backups and deployment credentials remain explicitly described production work. These do not prevent running or reviewing the assessment.
