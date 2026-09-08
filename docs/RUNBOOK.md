# Runbook — V0.7 Release Candidate

This runbook describes the local/reviewer Compose deployment. It records recovery behavior honestly; it is not a production SLA or a cloud operations manual.

## Health semantics

| Endpoint/status | Meaning | Operator action |
| --- | --- | --- |
| `/actuator/health/liveness` → `UP` | Backend process can serve | Investigate only if not `UP` |
| `/actuator/health/readiness` → `UP` | MySQL and Neo4j are reachable; Redis is reachable | Safe to run acceptance flows |
| readiness → `DEGRADED` (HTTP 200) | Redis is unavailable; it is an optional cache | Restore Redis; core MySQL/Neo4j-backed paths remain the documented behavior |
| readiness → `DOWN` (HTTP 503) | MySQL or Neo4j is unavailable | Do not claim the platform is ready; restore the critical dependency |
| `/api/v1/system/version` | Version/build metadata only | Use for evidence and mismatch diagnosis |

Every HTTP response includes `X-Request-Id`. API error and request logs carry the same correlation value; do not put tokens, passwords, or private values in a request ID.

## Routine checks

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml ps
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health/readiness
Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/v1/system/version
```

The API contract is at `http://localhost:8080/openapi.yaml`. It documents the JWT bearer scheme but does not bypass authorization.

## Restart drills

Run the API smoke before and after each drill when collecting release evidence:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run smoke
```

### Backend restart retains MySQL data

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml restart backend
```

Wait for readiness, then re-run smoke. Answer records, mastery, recommendation snapshots, and GraphVersion metadata remain in MySQL.

### Frontend restart and deep-link fallback

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml restart frontend
Invoke-WebRequest -UseBasicParsing http://localhost:8081/release-check/deep-link
```

The second command must return the SPA document rather than an Nginx 404.

### Neo4j restart and MySQL-authoritative reprojection

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml restart neo4j
```

After Neo4j is healthy, log in as `demo-admin` through the UI or use the authenticated endpoint:

```text
POST /api/v1/admin/graph-projections/rebuild-published
```

It reads only MySQL's active `PUBLISHED` GraphVersion plus approved `knowledge_relation` snapshot and writes a verified Neo4j projection. It does not create a version, modify a Published Graph, or switch a course's active graph pointer. Re-run graph query and API smoke afterward.

### MySQL fault drill

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml stop mysql
```

Readiness must become HTTP 503/`DOWN`; a 200/`UP` result is a release blocker. Restore the service:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml start mysql
```

Wait for readiness before restarting workers or rerunning smoke.

### Redis fault drill

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml stop redis
```

The documented result is readiness `DEGRADED` (HTTP 200), not a false `UP` or a claim that Redis is authoritative. Restore it with:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml start redis
```

Wait until readiness returns `UP` and run smoke. If the observed behavior differs, record it as a blocker rather than changing the runbook to fit the result.

## Backup and restore

MySQL is the backup priority. Neo4j is a rebuildable Published Graph projection. The portable `ops` profile uses an ephemeral MySQL client container, so no secret needs to appear in an operator command line:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-backup
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-validate
```

The generated SQL file is `artifacts/mysql-v07-release-readiness.sql`. The POSIX scripts remain available for an explicitly chosen alternate backup path, but the `ops` profile is the documented cross-platform release procedure.

To restore, first make the target intentionally clean and start MySQL only:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml down -v --remove-orphans
docker compose --env-file .env.release -f docker-compose.full.yml up -d mysql
```

Then restore and validate using the same profile:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-restore
docker compose --env-file .env.release -f docker-compose.full.yml --profile ops run --rm mysql-validate
```

The validation prints Flyway successful-migration rows and business counts for users, active courses, AnswerRecord, mastery, Published GraphVersion, and approved Published relations. Start the remaining services and invoke `POST /api/v1/admin/graph-projections/rebuild-published` as a system admin; then query the real graph and run smoke.

The restore command writes into the running target database. It does not delete the target itself; the explicit `down -v` above is the separate destructive clean-target step and must only be used after confirming the backup file exists.

## Diagnostics and safe handling

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml ps
docker compose --env-file .env.release -f docker-compose.full.yml logs --no-color backend mysql neo4j redis frontend
```

Do not paste `.env.release`, JWTs, Authorization headers, MySQL credentials, Neo4j credentials, or password verifier values into tickets, logs, issue comments, screenshots, or commits.
