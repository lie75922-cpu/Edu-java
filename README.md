# Edu-java

**基于知识图谱与个性化学习分析的 Java 智能教学平台**

> 当前阶段：**V0.7 已达到 `GO_RELEASE_CANDIDATE`；MODEL-3 最终外部验证得到 `GO_RASCH_ONLY_INTEGRATION`。** Java 生产系统当前仍使用可解释 `RuleBeta` 维护 KnowledgePoint Mastery；Rasch 已证明学生全局能力信号的稳定预测价值，但尚未接入生产，且不能冒充 KnowledgePoint Mastery。

本项目遵循 **Evidence First → Design → Implementation → Independent Review → Gate**：模型没有证明增量价值时，不为了“有 AI”而强行进入业务链路。

---

## 1. 项目现在是什么

系统已经形成学生端、教师端、管理端和独立研究模块：

```text
Student / Teacher / Admin (Vue)
            │ REST + JWT
            ▼
      Spring Boot Modular Monolith
            │
 ┌──────────┼───────────┐
 ▼          ▼           ▼
MySQL      Redis       Neo4j
Authority  optional    Published Graph projection

Research Data / Models
Junyi → data-pipeline → MODEL-0A/0R/1/2/3
```

核心学习闭环：

```text
答题 → AnswerRecord → Mastery → 薄弱知识
    → Published Knowledge Graph → 推荐 → Learning Path
    → 教师学情下钻
```

---

## 2. 已完成功能

### V0.2 在线学习核心闭环 ✅

- Spring Security + JWT + BCrypt + RBAC；
- Course / KnowledgeArea / KnowledgePoint；
- ExerciseUnit / Exercise-Knowledge mapping；
- Question / Option；
- CourseEnrollment；
- 在线答题、服务端判题、AnswerRecord；
- `clientRequestId` 幂等；同一幂等键跨 Question 复用返回 Conflict；
- 受控目录导入与冲突审计。

### V0.3 Knowledge Graph Governance ✅

正式业务图：

```text
(:KnowledgePoint)-[:PREREQUISITE]->(:KnowledgePoint)
```

发布链：

```text
Raw Evidence
→ identity / mapping resolution
→ Draft relation
→ review
→ GraphValidator
→ READY
→ GRAPH_REBUILD_REQUEST
→ versioned Neo4j projection
→ verification
→ active GraphVersion switch
```

已实现 self-loop、duplicate、cross-course、missing/inactive node、cycle 等校验；新版本投影失败时旧 Published Graph 继续在线。

### V0.4 Rule Mastery + Recommendation + Learning Path ✅

当前生产 Mastery：

```text
RuleBasedMasteryProvider
algorithmVersion = RULE_BETA_1_1_V1
mastery = (correct_count + 1) / (attempt_count + 2)
```

无答题历史统一为 `UNKNOWN`，不把先验 0.5 伪装成真实掌握度。

推荐链：

```text
Candidate Generator
→ Filter
→ Ranker
→ Explanation Builder
```

推荐综合当前 Mastery、错误历史、Published Graph 前置关系、可用 ExerciseUnit / Question，并保存版本化 Recommendation Snapshot 与 ReasonCode。

Learning Path 只读取当前 active Published Graph，通过 prerequisite subgraph + 已掌握过滤 + DAG 拓扑排序生成。

### V0.5 Evidence Re-resolution ✅

- UNMAPPED / AMBIGUOUS / SELF_LOOP / RESOLVED 可审计重解析；
- dry-run 零持久化副作用；
- stale Evidence link 解绑与 `evidence_count` 重算；
- repeated apply 幂等；
- Published / Archived GraphVersion 和 active Neo4j 图不会被重解析偷偷改写。

### V0.6 Teacher Authorization + Analytics ✅

- `course_teacher_assignment`；
- SYSTEM_ADMIN / TEACH_ADMIN 管理 Course lifecycle 与教师分配；
- 普通 TEACHER 只访问 ACTIVE assigned Course；
- Question / Exercise / Knowledge / GraphVersion / Evidence 等间接资源均回溯 Course 后授权；
- assigned course list；
- course overview；
- KnowledgePoint OBSERVED / UNKNOWN 学情；
- 分页 Student × KnowledgePoint heatmap；
- high-error questions；
- student detail、recent answers、mastery history、recommendation context。

### V0.7 Release Readiness ✅ `GO_RELEASE_CANDIDATE`

- backend + frontend/Nginx + MySQL 8.4 + Redis 7.4 + Neo4j 全栈 Compose；
- backend / frontend 多阶段 Docker build；
- clean volumes 自动执行 Flyway V001–V006；
- release secret/config 校验；
- liveness / readiness / version / OpenAPI / request correlation；
- 合成 Platform Demo Seed，Junyi Research Student 不进入业务用户体系；
- 真实 API smoke + Playwright 浏览器 E2E；
- Teacher A → Course B 跨课程访问 403；
- backend/frontend/Neo4j 重启与 MySQL/Redis 故障演练；
- MySQL backup → clean restore；
- Neo4j 可从 MySQL Published relation 重新投影；
- GitHub Actions 增加 `full-stack-release` Gate。

受控本地 clean-volume 启动到 readiness：**68.7 s**。这些数据只代表 reviewer/local Compose Release Candidate，不构成生产 SLA。

---

## 3. 数据与知识图谱规模

### Junyi Research Data

当前真实科研数据来自 Junyi 镜像，主体为基础数学学习数据，并含少量逻辑与生物内容；**不是大学《离散数学》数据集**。

- 匿名学习者：**247,606**；
- ProblemLog：**25,925,992** 条交互；
- Exercise metadata：**837** 行；
- distinct Exercise external ID：**835**；
- Area：**8**；
- Topic：**40**。

Area 审计包括 arithmetic、algebra、geometry、analytic-geometry、probability-statistics、calculus、logics，以及少量 biology。

### Knowledge Graph / prerequisite Evidence

必须区分科研 Evidence Graph 与业务 Published Graph：

```text
Research Evidence Graph:
Exercise ─PREREQUISITE_EVIDENCE→ Exercise

Business Published Graph:
KnowledgePoint ─PREREQUISITE→ KnowledgePoint
```

DATA-0 固定科研图 scope：

- **815 Exercise nodes**；
- **979 raw prerequisite Evidence edges**。

Medium 10k 学生实验诱导图：

- **624 Exercise nodes**；
- **763 unique directed prerequisite edges**；
- 最大弱连通分量覆盖约 **94.39%** 节点；
- 原始投影中存在 self-loop / cyclic SCC，因此不能直接作为 Published Graph。

业务侧目前使用 `Area → KnowledgePoint(Topic) → ExerciseUnit(Exercise) → Question` 的领域分层；完整 Junyi Evidence 不会未经治理直接进入 Neo4j。

### Medium / Holdout

- Medium：10,000 students / 284,245 raw interactions；
- 去完全重复后：284,228；
- observed/Q-eligible Exercise：624；
- 原 student split：7,000 / 1,000 / 2,000，成员交叉 0；
- MODEL-1 external cohort：独立 5,000 students；
- MODEL-3 final holdout：再独立 5,000 students，与前两批均零重叠，最终只评估一次。

Research Student 永不自动映射为平台 `sys_user`。

---

## 4. 模型研究最终结论

### MODEL-0A — Zero-history Cold Start ✅

- NCDM AUC 0.756775；
- ORCDF AUC 0.638144；
- 结论：`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`。

这说明零历史 unseen student 不能用来直接选择传统 student-specific CDM，不代表整体认知诊断失败。

### MODEL-0R — Warm / Calibrated Cold ✅

Warm：

| Method | Test AUC |
| --- | ---: |
| ExerciseRate | 0.748487 |
| NCDM C40 | **0.765630** |
| ORCDF-NCD | 0.470608 |

NCDM 有小幅响应预测增益，但 Topic mastery 未达到生产诊断要求。

### MODEL-1 — Concept Granularity Gate ✅

独立 external 5k：

| Method | Test AUC |
| --- | ---: |
| ExerciseRate | 0.708358 |
| NCDM C40 | **0.719500** |
| Fine Exercise-as-Concept | 0.708524 |

`NO_GO_CONCEPT_LAYER`：一题一 Concept 的 fine 路线未证明增量，停止继续堆 RCD / ORCDF / GEAR-CD。

### MODEL-2 — Personalization Signal Gate ✅

- C40 ORIGINAL embedding AUC 0.719500；
- POP_MEAN 0.706657；ZERO 0.704477；PERMUTED 0.697232；
- student identity 确实包含真实预测信号；
- 但 observed exposure≥3 下，C40 mastery AUC 0.615875，低于 RuleBeta 0.633877；
- Rasch/IRT-1PL AUC 0.728041，高于 ExerciseRate 0.708358，student-cluster 95% CI 全为正。

Final Gate：`GO_SIMPLE_HIERARCHICAL_ROUTE`。

### MODEL-3 — Untouched Final Holdout ✅

最终独立 5,000 students，只消费一次 final holdout；每学生 chronological 60% calibration / 40% evaluation，全局参数冻结，evaluation 不更新个人参数。

| Candidate | AUC | ACC | RMSE | Log Loss | Brier |
| --- | ---: | ---: | ---: | ---: | ---: |
| ExerciseRate | 0.707210 | 0.820778 | 0.368169 | 0.429899 | 0.135549 |
| **Rasch/IRT-1PL** | **0.724675** | **0.826387** | **0.362658** | **0.420147** | **0.131521** |
| Hierarchical Rasch + Topic deviation | 0.710015 | 0.811279 | 0.374733 | 0.442709 | 0.140425 |

Primary student-cluster paired bootstrap（1000 resamples）：

- Rasch − ExerciseRate AUC Δ **+0.017465**, 95% CI **[0.012394, 0.022665]**；
- Hierarchical − Rasch AUC Δ **−0.014660**, 95% CI **[-0.017391, -0.011977]**。

Final Gate：**`GO_RASCH_ONLY_INTEGRATION`**。

重要语义：Rasch 证明的是 `student ability - exercise difficulty` 的响应预测价值。它是**学生全局能力模型**，不是 KnowledgePoint mastery。后续即使接入，也不能直接替换 `MasteryProvider`；当前生产 KnowledgePoint Mastery 仍为 RuleBeta。

---

## 5. 技术栈

### Backend
- Java 21
- Spring Boot 4.1.1
- Spring Security / JWT / BCrypt
- Spring Data JPA
- Flyway
- MySQL 8.4
- Redis 7.4
- Neo4j
- Testcontainers

### Frontend
- Vue 3.5.x
- Vite 8.x
- Nginx release image
- Playwright E2E

### Research
- Python
- PyTorch
- FastAPI model boundary
- NCDM / ORCDF controlled experiments
- Rasch / IRT-1PL final validation

### Infrastructure
- Docker Compose
- GitHub Actions
- full-stack release CI

---

## 6. Repository Structure

```text
Edu-java/
├── backend/         # Spring Boot business system
├── frontend/        # Vue student / teacher / admin UI
├── model-service/   # research models and experiments
├── data-pipeline/   # DATA-0 / Research Data Domain
├── e2e/             # API smoke + Playwright release tests
├── scripts/         # release env / backup / restore helpers
├── docs/            # ADR / scope / implementation / runbook
├── docker-compose.yml
└── docker-compose.full.yml
```

---

## 7. Quick Start

Release-like local demo：

```bash
cp .env.example .env.release
# 推荐使用 scripts/generate-local-release-env.sh 或 PowerShell 版本生成安全本地值
docker compose --env-file .env.release -f docker-compose.full.yml up --build -d
```

详细说明：

- `docs/DEPLOYMENT.md`
- `docs/RUNBOOK.md`
- `docs/DEMO_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/V0.7_IMPLEMENTATION.md`

---

## 8. 关键研究/架构文档

### Data / Domain
- `docs/DATA-0_IMPLEMENTATION.md`
- `docs/DATA0_ARCHITECT_REVIEW.md`
- `docs/ADR/0001-data-domain-boundary.md`
- `docs/ADR/0002-post-data0-domain-model.md`

### Knowledge Graph
- `docs/ADR/0003-prerequisite-evidence-and-published-graph.md`
- `docs/ADR/0005-mysql-authority-and-versioned-neo4j-projection.md`
- `docs/V0.3_IMPLEMENTATION.md`
- `docs/V0.5_IMPLEMENTATION.md`

### Personalization
- `docs/ADR/0006-mastery-provider-and-rule-fallback.md`
- `docs/V0.4_IMPLEMENTATION.md`

### Research
- `docs/ADR/0004-cognitive-diagnosis-evaluation-protocol.md`
- `docs/ADR/0007-model-concept-granularity-after-model0r.md`
- `docs/ADR/0008-observability-aware-mastery-and-personalization-signal-gate.md`
- `docs/ADR/0010-simple-hierarchical-ability-route-after-model2.md`
- `docs/MODEL-3_SIMPLE_HIERARCHICAL_FINAL_VALIDATION_PLAN.md`

### Teacher / Release
- `docs/ADR/0009-teacher-course-authorization-and-analytics-boundary.md`
- `docs/V0.6_IMPLEMENTATION.md`
- `docs/ADR/0011-release-readiness-and-full-stack-e2e.md`
- `docs/V0.7_IMPLEMENTATION.md`

---

## 9. Engineering Principles

1. **Evidence First**：真实数据事实先于模型与架构结论。
2. **Research / Business 分域**：科研匿名学生不进入平台用户体系。
3. **MySQL authority**：业务事实、Graph lifecycle、Mastery/Recommendation审计以 MySQL 为准。
4. **Neo4j projection**：只保存可重建 Published Graph，不作为第二业务真相。
5. **Mastery ≠ Ability**：KnowledgePoint Mastery 与 Rasch global student ability 严格分离。
6. **UNKNOWN ≠ 0.5**：无历史证据不能伪装成数值掌握度。
7. **Model optional**：模型必须证明增量价值；失败时 RuleBeta 路径正常工作。
8. **Test integrity**：holdout 一旦消费，不再作为下一轮调参数据。
9. **No hidden hardcoding**：不按 student/exercise/test ID 硬编码结果。
10. **Gate-driven delivery**：每个阶段有 DoD、真实测试、Final Gate，并在 Gate 后停止再审查。
