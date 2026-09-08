# ADR-0011：V0.7 Release Readiness 与全栈验收边界

- 状态：Accepted
- 决策日期：2026-09-08
- 适用阶段：V0.7

## 1. 背景

截至 V0.6，平台已具备：

- JWT/RBAC、课程/知识点/练习/题库/答题；
- AnswerRecord / Outbox；
- Evidence → Draft → Validate → Published Graph → Neo4j；
- Rule Mastery、Recommendation、Learning Path；
- Evidence re-resolution；
- Teacher-Course assignment、跨课程授权、教师学情分析。

但当前 `docker-compose.yml` 仍主要启动 MySQL/Redis/Neo4j 基础设施，尚不能从一个干净环境一条命令启动完整可演示平台。V0.7 的任务因此不是继续扩业务，而是把已有能力做成**可重复部署、可浏览器验收、可运维说明、可交付展示**的 Release Candidate。

MODEL-3 与 V0.7 并行；V0.7 不等待任何模型结果，也不接入实验模型。

## 2. 核心决策

V0.7 定义为：

```text
Build
 -> clean environment startup
 -> Flyway migrate
 -> synthetic demo seed
 -> browser E2E
 -> health/readiness
 -> failure/restart/restore drill
 -> documentation
 -> Release Candidate Gate
```

不增加新的教育业务域，不以“功能数量”作为完成度。

## 3. Full-stack Compose

保留现有基础设施 compose 能力，同时新增明确的全栈启动方案，例如：

- `compose.full.yml` / `docker-compose.full.yml`；或
- 通过 compose profiles 实现等价能力。

至少包含：

- MySQL 8.4；
- Redis 7.4；
- Neo4j；
- Spring Boot backend；
- Vue frontend 静态服务（Nginx或等价）；

`model-service` 在 V0.7 不是生产依赖。若保留容器，只能作为 optional research/profile service，后端核心业务在其不存在时仍正常。

### 3.1 Backend image

当前 backend Dockerfile 依赖外部先生成 JAR。V0.7 应改为可复现构建之一：

- multi-stage Docker build；或
- CI 产出 JAR 后明确 COPY artifact。

必须保证新开发者不依赖本机特定路径/JDK/Maven缓存才能构建。

### 3.2 Frontend image

新增生产构建镜像：

```text
Node build stage
 -> static artifact
 -> Nginx / equivalent runtime
```

API base URL通过环境/构建配置管理，不能硬编码开发机地址。

## 4. 配置与密钥

### Development

允许 `.env.example` 提供明确开发默认值/说明。

### Release profile

不得依赖：

- 默认JWT secret；
- 默认MySQL root密码；
- 默认Neo4j密码；
- 仓库内真实凭据。

必须：

- 启动时校验 JWT secret 等关键配置；
- 文档说明必须设置的环境变量；
- `.env` 继续 gitignore；
- 日志不能输出 secret/JWT/password hash。

## 5. Health / Readiness / Observability

V0.7 不引入完整 Prometheus/Grafana 基础设施，除非已有证据需要。

至少提供：

- liveness；
- readiness；
- build/version info；
- MySQL连接状态；
- Neo4j必要状态；
- Redis若为可降级依赖，则健康语义要真实说明；
- request/correlation ID；
- 统一错误响应不泄露内部SQL/stacktrace。

健康检查不能永远返回200掩盖关键依赖失败。

## 6. OpenAPI / API discoverability

为当前稳定API提供 OpenAPI/Swagger 或等价规范。

至少覆盖：

- auth；
- student course/answer；
- graph query/governance；
- mastery/recommendation/path；
- teacher assignment/analytics。

敏感管理API的文档不等于绕过授权；Swagger只能调用正常安全链。

## 7. Demo data boundary

新增**合成 Platform Demo Seed**，明确与Junyi Research Data分离。

Demo 至少包含：

- 1 SYSTEM_ADMIN；
- 2 TEACHER；
- 2 Course；
- 每课程若干KnowledgePoint / ExerciseUnit / Question；
- Teacher-Course assignment；
- 若干STUDENT enrollment；
- 一个可发布的prerequisite DAG；
- 受控答题/Rule Mastery/Recommendation演示数据。

所有账号/密码仅为本地 demo，文档显著标记；不能声称为真实生产用户/指标。

Demo seed必须幂等或可在clean database稳定重建。

## 8. Browser E2E

使用 Playwright 或等价浏览器自动化，必须在真实全栈服务上执行，不使用mock后端冒充验收。

核心E2E：

### Student flow

```text
login
 -> course
 -> knowledge/exercise/question
 -> answer
 -> AnswerRecord
 -> mastery visible
 -> recommendation/path visible
```

### Teacher flow

```text
teacher login
 -> assigned course only
 -> overview
 -> KP analytics
 -> heatmap
 -> student detail
```

必须验证 Teacher-A 不能通过UI/API访问 Teacher-B 的课程。

### Admin/Graph flow

至少一个最小路径证明：

```text
Draft relation/evidence
 -> validate
 -> publish
 -> student graph query
```

如果完整Publish异步等待会使浏览器测试过重，可由API fixture完成前置，但必须使用真实MySQL/Neo4j和真实发布worker，不可直接伪造Neo4j结果。

## 9. Clean-start / restart / failure drills

至少验证：

1. 空volume从零启动，Flyway V001→V006成功；
2. backend重启后业务数据不丢；
3. Neo4j重启后active Published Graph仍可查询或可按权威MySQL重建；
4. Redis不可用时按实际设计降级/失败，结果与文档一致；
5. MySQL不可用时readiness失败而不是假健康；
6. frontend刷新深链接不404。

## 10. Backup / Restore

MySQL 是业务权威，V0.7提供最小可执行备份/恢复Runbook：

- `mysqldump` 或等价；
- restore到干净实例；
- Flyway schema history一致性检查；
- 关键业务计数/查询验证。

Neo4j不是业务唯一真相。优先验证从MySQL Published Graph metadata/relation重新投影，而不是把Neo4j备份当核心恢复手段。

## 11. Performance / resource evidence

不做虚假的生产压测SLA。

至少记录 full-stack demo 环境：

- build时间；
- startup时间；
- backend RSS/heap；
- 基础并发答题/API smoke；
- Teacher analytics已有受控P50/P95可复用并补HTTP层证据。

任何性能数字必须写明硬件、数据规模与测试方式。

## 12. CI

现有：

- backend test；
- python test；
- frontend build；
- compose config。

V0.7新增至少一个 release/full-stack Gate：

```text
build images
 -> docker compose up
 -> wait readiness
 -> seed synthetic demo
 -> API smoke / browser E2E
 -> collect diagnostics
 -> docker compose down
```

如果GitHub-hosted runner资源不足，可拆为轻量PR Gate + documented local full-stack acceptance，但最终Gate前必须有至少一次可复核真实全栈运行证据。

## 13. Release documentation

至少新增/更新：

- `docs/DEPLOYMENT.md`
- `docs/RUNBOOK.md`
- `docs/DEMO_GUIDE.md`
- `docs/ARCHITECTURE.md`（若现有不足则完善）
- README快速启动与当前真实状态。

必须能让一个没有参与开发的人知道：

- 怎么启动；
- 用什么demo账号；
- 怎么跑E2E；
- 怎么看健康；
- 怎么备份/恢复；
- 哪些模块是规则、哪些是实验模型；
- 当前已知限制。

## 14. 非目标

V0.7明确不做：

- MODEL-3模型生产接入；
- 新推荐算法；
- 新知识图算法；
- LLM / Agent；
- Kafka / Kubernetes；
- 微服务拆分；
- 云厂商绑定部署；
- 为简历堆无实际价值的中间件。

## 15. Gate

只允许：

- `GO_RELEASE_CANDIDATE`
- `CONDITIONAL_GO`
- `NO_GO`

Gate通过只说明平台达到可重复演示/部署的Release Candidate，不等于真实生产SLA认证。
