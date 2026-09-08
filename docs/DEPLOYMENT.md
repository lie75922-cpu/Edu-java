# Deployment — V0.7 Release Candidate

## Scope and inventory

`docker-compose.full.yml` is the reproducible local/reviewer release stack. It contains only the existing modular-monolith platform and its real dependencies:

| Service | Image/build | Role |
| --- | --- | --- |
| `mysql` | `mysql:8.4` | Authoritative business, audit, mastery, recommendation and Published Graph metadata store |
| `redis` | `redis:7.4-alpine` | Optional cache; it is reported as degraded, not a replacement source of truth |
| `neo4j` | `neo4j:5.26-community` | Versioned Published Graph read-model projection only |
| `backend` | reproducible multi-stage Java 21/Maven build | Spring Boot modular monolith, Flyway, JWT/RBAC, workers and APIs |
| `frontend` | reproducible Node build → Nginx runtime | Vue production bundle, SPA deep-link fallback and `/api` proxy |
| `e2e` | optional `test` profile | API smoke and Playwright browser acceptance only |

`docker-compose.yml` remains the development infrastructure-only compose entrypoint. `model-service` is not included in the full stack and is not needed for login, learning, teacher analytics, graph query, mastery, recommendation or learning path.

## Prerequisites

- Docker Engine / Docker Desktop with Compose v2;
- at least 8 GiB container memory available (Neo4j's heap/page cache are bounded in compose);
- a free local host port set for each published service, or altered values in `.env.release`.

No cloud account, Kubernetes, Kafka, external model endpoint, Windows absolute path, or prebuilt backend JAR is required.

## Release configuration

Never commit `.env.release`. It contains local-only private values and is already ignored by Git.

PowerShell:

```powershell
Set-Location D:\Code\java\Edu-java-v07-20260908
.\scripts\generate-local-release-env.ps1 -Path .env.release
```

POSIX shell:

```sh
scripts/generate-local-release-env.sh .env.release
```

For a managed environment, copy `.env.example` to a non-repository secret store and replace every `<required-private-value>` placeholder. The full compose definition requires `JWT_SECRET`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, and `NEO4J_PASSWORD`; release mode additionally rejects placeholders and known development values before the backend begins serving.

`JWT_SECRET` must be at least 32 bytes. `CORS_ALLOWED_ORIGINS` is a comma-separated allow-list; `*` is rejected in release mode. `JWT_ISSUER` and `VITE_API_BASE_URL` are externalized. The normal same-origin deployment uses `VITE_API_BASE_URL=/api/v1` and needs no CORS hop; a separate frontend deployment must build with its externally supplied API URL and configure the matching allowed origin.

`APP_DEMO_SEED_ENABLED=true` is only for the local/reviewer demo. Set it to `false` outside that context. Demo credentials are public local fixture credentials, never deploy credentials.

## Clean full-stack startup

The following intentionally removes only volumes owned by this compose project. Do not run it against a deployment whose data must be retained.

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml down -v --remove-orphans
docker compose --env-file .env.release -f docker-compose.full.yml up --build -d
```

Wait for readiness:

```powershell
for ($i = 0; $i -lt 90; $i++) {
  try {
    $response = Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health/readiness -TimeoutSec 3
    if ($response.StatusCode -eq 200) { break }
  } catch { }
  Start-Sleep -Seconds 1
}
```

Expected local endpoints:

| Endpoint | Purpose |
| --- | --- |
| `http://localhost:8081/` | Vue/Nginx frontend |
| `http://localhost:8080/actuator/health/liveness` | process liveness |
| `http://localhost:8080/actuator/health/readiness` | MySQL/Neo4j/Redis-aware readiness |
| `http://localhost:8080/api/v1/system/version` | build/version metadata |
| `http://localhost:8080/openapi.yaml` | OpenAPI 3 document with bearer scheme |
| `http://localhost:7474/` | Neo4j local operator UI (not application authority) |

The clean startup runs Flyway migrations `V001` through `V006`, then optionally installs the isolated synthetic demo fixture. Do not insert Junyi research students into `sys_user` as a substitute for this fixture.

## Full-stack acceptance commands

The test profile starts no model service and executes against the real Compose network:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run smoke
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run e2e
```

Browser JUnit XML, traces, and failure screenshots are written below `artifacts/` and intentionally ignored by Git.

## MySQL backup helpers

The `ops` profile runs short-lived MySQL client containers on the same Compose network; it does not add an application service or expose credentials in a command line. The helpers write only beneath ignored `artifacts/`:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-backup
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-validate
```

The backup path is `artifacts/mysql-v07-release-readiness.sql`. For a clean restore target, start only `mysql`, run `mysql-restore`, validate it, then start the remaining stack and use the system-admin reprojection action described in [RUNBOOK.md](RUNBOOK.md).

## Shutdown

Preserve volumes for an ordinary stop:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml down
```

Use `down -v` only for an explicitly intended clean-volume reset or after a verified backup.
