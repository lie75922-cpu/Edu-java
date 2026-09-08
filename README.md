# Edu-java

**基于知识图谱与个性化学习分析的 Java 智能教学平台**

> 当前阶段：**V0.6 教师课程授权与学情分析已完成；MODEL-0A / MODEL-0R / MODEL-1 实验保持既有归档结论。**

本项目按真实软件工程与研究流程推进：

```text
真实数据审计
  → 领域建模
  → Java核心业务
  → 关系证据治理 / Published Graph
  → 可解释Rule Mastery / Recommendation / Learning Path
  → 受控模型实验与失败归档
  → 教师分析 / 最终部署与工程收尾
```

原则不是“为了有 AI 而接一个模型”，而是：**数据、模型、业务能力都必须有可复核证据；模型没有证明增量价值时，平台继续使用透明规则 fallback。**

---

## 1. 当前产品能力

### V0.2 — 在线学习核心闭环 ✅

```text
注册 / JWT登录 / RBAC
  → Course
  → KnowledgePoint
  → ExerciseUnit
  → Question
  → AnswerRecord
  → Outbox
```

已实现：

- Spring Security + JWT + BCrypt；
- Course / KnowledgeArea / KnowledgePoint；
- ExerciseUnit / Exercise-Knowledge mapping；
- 平台 Question / Option；
- CourseEnrollment；
- 在线答题、服务端判题、AnswerRecord；
- `clientRequestId` 幂等；跨 Question 复用同一幂等键返回冲突；
- 受控 Junyi 目录导入与冲突审计。

### V0.3 — Knowledge Relation Governance + Published Graph ✅

正式学习图：

```text
(:KnowledgePoint)-[:PREREQUISITE]->(:KnowledgePoint)
```

流程：

```text
Raw prerequisite Evidence
  → identity / mapping resolution
  → Draft candidate
  → manual review
  → GraphValidator
  → READY
  → GRAPH_REBUILD_REQUEST
  → versioned Neo4j projection
  → projection verification
  → active GraphVersion switch
```

关键能力：

- MySQL 是 Evidence / Relation / GraphVersion 权威；
- Neo4j 只是可重建 Published Graph 查询投影；
- self-loop / cycle / duplicate / cross-course 等阻断发布；
- 投影失败时新版本为 `PROJECTION_FAILED`，旧 Published Graph 继续在线；
- predecessor / successor / path / prerequisite-subgraph 查询；
- 管理端 Evidence / Draft / Validation / Publish 治理。

### V0.4 — Rule Mastery + Recommendation + Learning Path ✅

默认 `MasteryProvider`：

```text
RuleBasedMasteryProvider
algorithmVersion = RULE_BETA_1_1_V1
mastery = (correct_count + 1) / (attempt_count + 2)
```

说明：这是**透明规则 baseline / fallback，不是 AI 模型结果**。

无答题历史：

```text
UNKNOWN
```

不会把先验 0.5 展示为学生真实掌握度。

已实现：

- `MASTERY_UPDATE_REQUEST` 专用 worker；
- AnswerRecord 级 exactly-once mastery 更新；
- 多 KnowledgePoint Exercise 独立更新；
- Mastery current/history；
- Recommendation：Candidate → Filter → Rank → Explanation；
- `REC_RULE_V1` 可审计 recommendation snapshot；
- 基于 active Published Graph 的 DAG learning path；
- 学生端 mastery、弱项、推荐原因、路径、历史页面。

### V0.5 — Evidence Re-resolution Hardening ✅

解决：Evidence 最初因映射缺失/歧义无法解析，而后管理员修复映射后仍永久卡住的问题。

```text
immutable Raw Evidence
  → auditable resolution recomputation
  → targeted editable Draft reconciliation
  → later Validate / Publish
```

已实现：

- Flyway V005 resolution history；
- UNMAPPED / AMBIGUOUS / SELF_LOOP / RESOLVED 状态重解析；
- stale Evidence link 解绑与 evidence_count 重算；
- 新 candidate pair 创建/聚合；
- repeated apply 幂等；
- dry-run 零持久化副作用；
- PUBLISHED / ARCHIVED relation 与 active Neo4j 图完全不被重写。

### V0.6 — Teacher Course Authorization + Learning Analytics ✅

先修复课程级权限，再开放教师分析：

```text
ACTIVE teacher-course assignment
  -> assigned ACTIVE Course only
  -> teaching content / Graph / Evidence governance
  -> course overview / KnowledgePoint analytics
  -> paginated student heatmap / high-error Question / student detail
```

已实现：

- Flyway V006 `course_teacher_assignment` 和最小分析索引；
- SYSTEM_ADMIN / TEACH_ADMIN 的 Course lifecycle 与教师分配；
- 普通 TEACHER 仅访问 ACTIVE assigned Course，且既有 Knowledge、Exercise、Question、Graph、Evidence 间接资源均回溯课程后鉴权；
- Platform Business Domain 教师课程列表、概览、OBSERVED / UNKNOWN mastery、分页 heatmap、高错题、学生作答 / mastery history / recommendation context；
- 教师工作台和教师分配管理界面；
- MySQL 8.4 Testcontainers 跨课程 403 演练与受控本地查询测量。

---

## 2. 数据与领域基线

### DATA-0 ✅

当前 Junyi 镜像真实审计：

- ProblemLog：**25,925,992** 条交互；
- 匿名学生：**247,606**；
- Exercise metadata：**837** 行 / **835** 个不同 external ID；
- Topic：**40**；
- Area：**8**。

冻结 Medium：

- 10,000 学生；
- 284,245 原始 Medium 交互；
- 模型派生输入移除 17 条完全重复后 284,228；
- 624 observed/Q-eligible Exercise；
- student-level 7,000 / 1,000 / 2,000 split，成员交叉 0。

已知数据边界：

- 官方 DataShop 原始包未在当前执行环境直接取得，本地输入使用已登记第三方镜像；
- raw prerequisite / relationship annotation 仅作为 Evidence；
- 2 个重复 Exercise external ID；
- 原始 prerequisite 存在 self-loop / cycle；
- Junyi Research Student 永不自动映射为平台 `sys_user`。

### 业务领域分层

```text
Course
 └─ KnowledgeArea
     └─ KnowledgePoint      # 当前可解释业务层：Junyi Topic来源
         └─ ExerciseUnit    # Junyi Exercise / 练习能力单元
             └─ Question    # 平台自有/授权的具体题目
```

重要：

```text
ExerciseUnit != Question
ExerciseUnit != KnowledgePoint
ModelConcept != business KnowledgePoint ID
Research Student != platform User
```

模型内部 Concept 通过 Adapter 投影到业务 KnowledgePoint，不反向污染平台主键。

---

## 3. 模型研究状态

项目没有把论文数字写成自己的结果，也没有因为实验失败修改 test split。

### MODEL-0A — Zero-history Cold Start ✅ 负实验归档

- NCDM AUC **0.756775**；
- ORCDF AUC **0.638144**；
- RCD：`NOT_COMPARABLE_ON_COMMON_GRAPH`；
- GEAR-CD：smoke only；
- DOA：N/A。

结论：`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`。

它只说明传统 student-specific CDM 不应通过“未见学生 + zero representation”直接做最终模型选择，不代表认知诊断整体失败。

### MODEL-0R — Warm / Calibrated Cold Re-evaluation ✅ 负实验归档

Warm（原 Medium 10k，per-student chronological 60/20/20）：

| 方法 | Test AUC |
| --- | ---: |
| Exercise historical rate | 0.748487 |
| NCDM C40 Topic | **0.765630** |
| ORCDF-NCD | 0.470608 |

NCDM 对 Exercise-rate 有小幅稳定预测增益，但完整 40-Topic mastery 指标被判定 collapse；cold k=5 也未超过 Exercise-rate baseline。

Final Gate：`NO_GO`。

### MODEL-1 — Concept Granularity Gate ✅ fine路线停止

另冻结与 Medium 10k **零重叠**的 fresh external holdout 5k：

| 方法 | Test AUC |
| --- | ---: |
| Exercise historical rate | 0.708358 |
| NCDM C40 Topic | **0.719500** |
| NCDM Fine Exercise-Concept | 0.708524 |

Fine route 相对 Exercise baseline 的 paired-bootstrap lower bound 为负，不能证明增量，因此：

`NO_GO_CONCEPT_LAYER`

不会继续在 fine identity-style Concept 上堆 RCD / ORCDF / GEAR-CD。

独立审查同时发现：历史 `mastery_sanity` 对完整 student×concept 矩阵计算 collapse，没有区分某 student 是否在 train/history 中真正观察过该 Concept。无历史 embedding≈0.5 在业务语义应是 `UNKNOWN`，不能直接作为真实 mastery 参与解释。

因此 C40 仍未获生产批准，但需要最后一次 **observability-aware student signal audit**，而不是继续换复杂模型。

### MODEL-2 — Personalization Signal Stop Gate ▶ 下一阶段

只回答：

1. 排除 UNKNOWN 维度后 observed Topic mastery 是否仍塌缩；
2. C40 的预测增益是否真正依赖 student-specific embedding；
3. StudentGlobal / Rasch / TopicBeta 等简单方法是否能证明稳定个体信号。

主要统计改用 **student-cluster paired bootstrap**。

MODEL-2 是强制停止 Gate，结果只允许：

- `GO_C40_FINAL_VALIDATION`
- `GO_SIMPLE_HIERARCHICAL_ROUTE`
- `STOP_ML_DIAGNOSIS_RULE_ONLY`

如果没有可靠个体化信号，ML认知诊断路线正式停止；平台继续使用已验证可工作的 Rule Mastery + Published KG + Recommendation。

---

## 4. V0.6 Teacher Analytics ✅

V0.6 先消除了早期实现中 TEACHER 课程权限过宽的问题，并新增：

```text
course_teacher_assignment
```

并将普通 TEACHER 收紧到 **ACTIVE assigned Course only**。

随后已实现：

- assigned course list；
- course overview；
- KnowledgePoint mastery summary；
- paginated student × KnowledgePoint heatmap；
- high-error questions；
- student detail / recent answers / mastery history / recommendation context。

既有 Question / Exercise / Knowledge / Graph / Evidence 管理入口已回归验证跨 Course 越权拒绝。

Junyi Research Student / ProblemLog 不会作为教师端班级成员展示。

---

## 5. 技术栈

### Backend

- Java 21
- Spring Boot 4.1.1
- Spring Security / JWT / BCrypt
- Spring Data JPA
- Flyway
- MySQL 8.4
- Spring Data Redis / Redis 7.4
- Neo4j + Java Driver
- Testcontainers

### Frontend

- Vue 3.5.x
- Vite 8.x

### Research / Model

- Python
- FastAPI
- PyTorch
- Junyi research data
- NCDM / ORCDF controlled experiments

### Infrastructure

- Docker Compose
- GitHub Actions CI

---

## 6. Repository Structure

```text
Edu-java/
├── backend/         # Spring Boot业务主系统
├── frontend/        # Vue学生/教师/管理端
├── model-service/   # Python模型服务与受控实验
├── data-pipeline/   # DATA-0 / Research Data Domain
├── docs/            # ADR、Scope、实验Gate、实现与验收记录
└── docker-compose.yml
```

---

## 7. 关键文档

### Data / Domain

- `docs/DATA-0_IMPLEMENTATION.md`
- `docs/DATA0_ARCHITECT_REVIEW.md`
- `docs/ADR/0001-data-domain-boundary.md`
- `docs/ADR/0002-post-data0-domain-model.md`

### Graph

- `docs/ADR/0003-prerequisite-evidence-and-published-graph.md`
- `docs/ADR/0005-mysql-authority-and-versioned-neo4j-projection.md`
- `docs/V0.3_IMPLEMENTATION.md`
- `docs/V0.5_IMPLEMENTATION.md`

### Personalization

- `docs/ADR/0006-mastery-provider-and-rule-fallback.md`
- `docs/V0.4_SCOPE_AND_ACCEPTANCE.md`
- `docs/V0.4_IMPLEMENTATION.md`

### Model Research

- `docs/ADR/0004-cognitive-diagnosis-evaluation-protocol.md`
- `docs/MODEL-0R_PLAN.md`
- `docs/ADR/0007-model-concept-granularity-after-model0r.md`
- `docs/MODEL-1_CONCEPT_GATE_PLAN.md`
- `docs/ADR/0008-observability-aware-mastery-and-personalization-signal-gate.md`
- `docs/MODEL-2_PERSONALIZATION_SIGNAL_GATE.md`

### Teacher Analytics

- `docs/ADR/0009-teacher-course-authorization-and-analytics-boundary.md`
- `docs/V0.6_SCOPE_AND_ACCEPTANCE.md`
- `docs/V0.6_AUTHORIZATION_MATRIX.md`
- `docs/V0.6_IMPLEMENTATION.md`

---

## 8. 工程原则

1. **Evidence First**：真实数据事实先于架构和模型结论。
2. **Research / Business 分域**：科研匿名学生不进入平台用户体系。
3. **MySQL authority**：业务事实、Graph lifecycle、Mastery/Recommendation审计以MySQL为准。
4. **Neo4j projection**：只查询已验证 Published Graph，不作为第二业务真相。
5. **Model optional**：模型必须证明增量价值；失败时平台仍由规则 fallback 正常工作。
6. **UNKNOWN ≠ 0.5**：没有历史证据的 mastery 不伪装成数值掌握度。
7. **Test integrity**：测试集被观察后不能继续作为下一轮无偏最终验证集。
8. **No hidden hardcoding**：不按 student/exercise/test ID 硬编码结果。
9. **No big raw data in Git**：原始行为数据、派生大表、checkpoint、runtime和凭据不提交。
10. **Gate-driven development**：每个阶段有明确 DoD、真实测试、Final Gate，并在 Gate 后停止再评审。
