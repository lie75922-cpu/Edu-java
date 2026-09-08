# Architecture — V0.7 Release Candidate

```text
Browser
  │
  ▼
Vue production bundle / Nginx
  ├── SPA deep-link fallback
  └── /api proxy with X-Request-Id
          │
          ▼
Spring Boot modular monolith
  ├── JWT / RBAC / course-level authorization
  ├── Student learning and AnswerRecord
  ├── Outbox workers: mastery and graph projection are separate consumers
  ├── RuleBeta mastery, recommendation, learning path
  ├── Teacher analytics and assignment administration
  ├── health/readiness/version/OpenAPI
  └── recovery: reproject Published Graph from MySQL
          │                     │                    │
          ▼                     ▼                    ▼
       MySQL 8.4             Neo4j 5.26          Redis 7.4
       authority              Published read       optional cache
                              projection
```

## Authority and data boundaries

MySQL is authoritative for platform users, courses, enrollments, AnswerRecord, mastery/history, recommendation snapshots, GraphVersion, relation evidence, validated Published relation snapshots, and the active graph pointer.

Neo4j stores only the query projection of a MySQL `PUBLISHED` GraphVersion. A projection succeeds before MySQL switches the active pointer. If Neo4j fails, the previous active Published Graph remains the business truth and can be reprojected from MySQL. No Neo4j-internal identifier is a business ID.

Redis is not a business authority. V0.7 calls an outage `DEGRADED` in readiness rather than hiding it or making core business data unavailable by declaration.

The `model-service` and `data-pipeline` are research boundaries. They are absent from the full-stack release dependency path. Junyi Research Student and ProblemLog data never become platform `sys_user` records or teacher analytics population.

## Authorization

```text
SYSTEM_ADMIN / TEACH_ADMIN
  -> Course lifecycle and teacher assignments; all authorized teaching/graph paths

TEACHER + ACTIVE course_teacher_assignment + ACTIVE Course
  -> only the assigned course's teaching, graph, and analytics paths

STUDENT + ACTIVE course_enrollment + ACTIVE Course
  -> own course read/write, answer, mastery, recommendation, learning path
```

Course-level checks occur again after indirect-resource lookup (Question, KnowledgePoint, GraphVersion, Evidence, student detail). The frontend does not replace API authorization.

## Lifecycle

```text
Answer submission transaction
  -> AnswerRecord
  -> MASTERY_UPDATE_REQUEST
  -> exactly-once RuleBeta mastery update
  -> recommendation snapshot bound to active Published GraphVersion

Graph governance
  -> Draft
  -> validate DAG
  -> GRAPH_REBUILD_REQUEST
  -> Neo4j projection verification
  -> Published + active pointer switch
```

V0.7 adds deployment/recovery verification around these existing lifecycles; it does not introduce MODEL-3, LLM/Agent, Kafka, Kubernetes, microservices, or a new recommendation/graph algorithm.
