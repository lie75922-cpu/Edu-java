# Demo Guide — Synthetic Platform Business Domain

## Boundary

The V0.7 demo fixture is synthetic and uses the `DEMO-*` namespace only. It does not load, copy, transform, or present Junyi Research Student identities as platform users. It exists only when `APP_DEMO_SEED_ENABLED=true`.

All listed accounts are local demonstration identities with the shared local-only password `LocalDemoOnly!2026`. They are not deployment credentials and must not be enabled in a public environment.

| Username | Role | Assigned/enrolled course |
| --- | --- | --- |
| `demo-admin` | `SYSTEM_ADMIN` | Platform administrator |
| `demo-teacher-a` | `TEACHER` | `DEMO-ALG-101` only |
| `demo-teacher-b` | `TEACHER` | `DEMO-GEO-201` only |
| `demo-student-alice` | `STUDENT` | `DEMO-ALG-101` |
| `demo-student-bob` | `STUDENT` | `DEMO-ALG-101` |
| `demo-student-carol` | `STUDENT` | `DEMO-ALG-101` |
| `demo-student-dave` | `STUDENT` | `DEMO-GEO-201` |

The seed provides two courses, active enrollments and assignments, active KnowledgePoints/ExerciseUnits/Questions, a small `Algebraic foundations → Applied algebra` prerequisite DAG, one controlled Alice answer, RuleBeta mastery, a recommendation snapshot, and a learning-path context. The fixture is idempotent within its own `DEMO-*` namespace.

## Start

Follow [DEPLOYMENT.md](DEPLOYMENT.md) to generate `.env.release`, clean-start the Compose stack, and wait for readiness. Open `http://localhost:8081/`.

## Student walk-through

1. Sign in as `demo-student-alice`.
2. Open **进入课程** for `DEMO-ALG-101`.
3. Choose **开始答题**, submit a choice, and observe the server judgement.
4. Open **我的学习** to see RuleBeta mastery. `UNKNOWN` is intentionally not rendered as a fabricated numeric score.
5. Choose **生成推荐快照**. The snapshot names its active `GraphVersion` and rule version.
6. Enter the target ID for **Applied algebra** (visible in the mastery list) and choose **查询学习路径**.

The UI states that RuleBeta is an explainable fallback, not an experimental research-model result.

## Teacher walk-through

1. Sign in as `demo-teacher-a`.
2. Open **教师工作台**. Only the assigned algebra course is listed.
3. Show **overview**, **KnowledgePoint 学情**, the **Student × KnowledgePoint 掌握度热力表**, and open an enrolled student detail.
4. The UI does not show geometry. The automated release check also sends Teacher A directly to Course B's analytics endpoint and requires HTTP 403.

## Administrator walk-through

1. Sign in as `demo-admin`.
2. Open **管理** and the **教师课程分配** section; load the algebra course assignment list.
3. Scroll to **V0.3 Knowledge Relation Governance** and load the demo GraphVersion.
4. Use **知识图** to query the Published Graph. For recovery, the system-admin-only reprojection endpoint rebuilds Neo4j from MySQL Published relation data without mutating the version lifecycle.

## Automated acceptance

The same synthetic data is used by the real-stack API smoke and Playwright browser suite:

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run smoke
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run e2e
```

Do not claim a browser E2E result until both commands complete successfully against a clean stack.
