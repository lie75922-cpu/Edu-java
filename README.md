# Edu-java

基于知识图谱与图认知诊断的个性化学习平台。

> 当前阶段：**DATA-0 已通过条件审查，进入 MODEL-0 + V0.2 并行阶段**。

本仓库遵循“先数据事实、再系统设计、后编码实现”的开发顺序。Junyi 等公开教育数据只用于离线研究/模型实验，不直接冒充平台真实用户或完整在线题库；平台业务数据与科研数据严格分域。

## DATA-0 结论

DATA-0 对当前 Junyi 镜像完成了真实 Schema、全量 ProblemLog、关系图、采样、成本和模型就绪度审计。

已确认：

- 全量 ProblemLog：25,925,992 条交互；
- 匿名学生：247,606；
- Exercise 元数据：837 行 / 835 个不同 external ID；
- Topic：40；Area：8；
- Medium：10,000 名学生 / 284,245 条交互；
- Medium 学生级 70/10/20 split，成员交叉为 0；
- Final Gate：`PASSED WITH CONDITIONS`。

条件与边界详见：

- `docs/DATA0_ARCHITECT_REVIEW.md`
- `docs/ADR/0002-post-data0-domain-model.md`
- `docs/ADR/0003-prerequisite-evidence-and-published-graph.md`

DATA-0 不代表 Junyi prerequisite 已经成为正式知识图，也不代表模型已经验证有效。

## 当前领域基线

```text
Course
 └─ KnowledgeArea        # Junyi Area来源的高层分类
     └─ KnowledgePoint   # 第一版可由Junyi Topic提供来源
         └─ ExerciseUnit # Junyi Exercise / 能力练习单元
             └─ Question # 平台自有/授权的具体题目
```

关键边界：

- ExerciseUnit ≠ Question；
- ExerciseUnit ≠ KnowledgePoint；
- ModelConcept 属于算法 Adapter，不直接成为业务主键；
- Research Student 不自动映射为 `sys_user`；
- raw prerequisite / annotation / RCD graph 默认都是 Evidence，不是 Published Graph。

## 当前技术基线

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA / Neo4j / Redis
- Flyway
- MySQL 8.4
- Redis 7.4
- Neo4j 2026.07.1 Community（开发环境）
- Python + FastAPI
- Vue 3.5.42 + Vite 8.2.2

## 仓库结构

```text
Edu-java/
├── backend/         # Spring Boot 在线业务主系统
├── frontend/        # Vue 前端
├── model-service/   # 独立 Python 模型服务
├── data-pipeline/   # Research Data Domain / DATA-0与MODEL-0输入准备
├── docs/            # 软件工程、ADR、实验计划与验收文档
└── docker-compose.yml
```

## 核心文档

- `docs/DATA-0_IMPLEMENTATION.md`：DATA-0实施基线
- `docs/DATA0_ARCHITECT_REVIEW.md`：DATA-0独立审查与放行结论
- `docs/ADR/0001-data-domain-boundary.md`：Research/Platform数据域边界
- `docs/ADR/0002-post-data0-domain-model.md`：Area/Topic/ExerciseUnit/Question领域分层
- `docs/ADR/0003-prerequisite-evidence-and-published-graph.md`：关系证据与正式图发布规则
- `docs/MODEL-0_PLAN.md`：NCDM / ORCDF / RCD / GEAR-CD受控实验计划
- `docs/V0.2_SCOPE_AND_ACCEPTANCE.md`：V0.2网站业务闭环范围和DoD

## 开发原则

1. 不根据“理想字段”反推数据，任何模型输入必须来自实际可获得字段或平台真实运行数据。
2. MySQL 是在线业务事实的权威数据源；Neo4j 是已发布知识图的查询投影。
3. 认知诊断与推荐解耦；模型异常不能阻断登录、课程、答题等核心业务。
4. 模型必须绑定数据版本、知识Schema/图版本和适用范围；不对未见知识体系伪装泛化。
5. Junyi 数据按非商业研究/学习/求职Demo口径使用并保留来源限制。
6. 原始大数据、学生行为主体、模型checkpoint和环境凭据不进入Git仓库。
7. test split冻结后不得根据结果重新划分。
8. 原始关系有自环/循环/身份冲突时保留Evidence，但不能静默进入Published Graph。

## 下一阶段：两条线并行

### MODEL-0

先完成 Medium 派生模型输入预检，再按统一split依次验证：

1. NCDM baseline；
2. ORCDF 主图模型候选；
3. RCD 条件性同数据适配；
4. GEAR-CD 仅环境与小规模 smoke，未经审查不进行高成本全量训练。

### V0.2

完成不依赖AI模型的最小网站业务闭环：

```text
登录 → 课程 → KnowledgePoint → ExerciseUnit → Question → 答题 → AnswerRecord → Outbox
```

V0.2暂不做正式推荐、学习路径、教师热力图或LLM/Agent。
