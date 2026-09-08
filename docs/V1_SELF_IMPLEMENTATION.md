# V1 产品重构：本轮直接实现与当前边界

本文件记录 PR #34 当前 head 的真实实现状态。若与较早的“中文离散数学 Demo”描述冲突，以 `docs/V1_MATH_PLATFORM_BASELINE.md` 为准。

平台当前定位：**基于知识图谱与学习分析的中文数学智能学习平台**。

中文化只针对用户侧展示，不覆盖原始科研数据；真实数据是什么数学领域，就按真实学科语义建模和展示。当前《离散数学》仍只是小型全栈 fixture，用于验证完整业务链，不代表平台被锁死为离散数学，也不代表 Junyi 真实数据已经导入 Java Business Domain。

## 已直接实现

### 1. V0.7 工程底座保留

保留并继续回归：

- Java 21 / Spring Boot / Spring Security / JWT / RBAC；
- MySQL 8.4 / Neo4j / Redis；
- Flyway V001–V006；
- Course / KnowledgeArea / KnowledgePoint / ExerciseUnit / Question；
- AnswerRecord 幂等；
- Outbox；
- RuleBeta mastery 与 UNKNOWN 语义；
- Evidence -> Draft -> GraphValidator -> Published Graph -> Neo4j；
- Recommendation / Learning Path；
- Teacher course authorization / analytics；
- Docker Compose / OpenAPI / API smoke / Playwright / release CI。

### 2. 中文产品入口与角色分区

默认前端已切到 V1 中文界面。

学生端：

- 首页；
- 课程学习；
- 知识图谱；
- 个性化学习；
- 中文题目练习与服务端判题；
- 推荐理由与学习路径。

教师端：

- 教师工作台；
- 课程概览；
- KnowledgePoint 学情；
- 高频错误题；
- 学生 × KnowledgePoint 掌握情况；
- 学生学情详情；
- 当前推荐上下文。

管理端：

- 课程与教学；
- 数据与算法；
- 教师授权；
- 知识图谱治理；
- 系统状态。

后端角色继续区分 STUDENT / TEACHER / TEACH_ADMIN / SYSTEM_ADMIN，普通教师保持课程级授权边界。

### 3. 课程结构去前端领域硬编码

已新增学生可访问接口：

- `GET /api/v1/courses/{courseId}/knowledge-areas`

学生课程页改为根据后端 `KnowledgeArea -> KnowledgePoint` 数据生成领域导航和目录。

学生知识图谱页改为使用 `KnowledgePoint.areaId + KnowledgeArea` 数据分组与布局。

已删除把 `DM-LOGIC / DM-GRAPH / DM-ALG` 等 knowledgeCode 当课程结构的前端推断逻辑。后续真实数据接入时不得恢复这种写法。

### 4. 当前中文业务 fixture

当前仍保留一套受控的小型中文 fixture，用于全栈 E2E：

- 主演示课程：《离散数学》 `DM-101`；
- 16 个知识点；
- 16 个 ExerciseUnit；
- 16 道中文平台 Question；
- 12 条已审核先修关系；
- GraphVersion -> validation -> Published Graph -> Neo4j；
- 受控学习行为用于 mastery / recommendation / learning path 演示。

这套 fixture 的作用只是验证系统链路。它不能被写成“真实 Junyi 数据已产品化”，也不能作为最终数据规模。

### 5. 数据处理与算法工作已在产品中显性化

管理端“数据与算法”目前展示仓库已经冻结、可审计的 DATA-0 / MODEL-3 研究快照，包括：

- 247,606 名匿名学生；
- 25,925,992 条学习行为；
- 837 条 Exercise metadata；
- 835 个 distinct Exercise external ID；
- 40 Topics / 8 Areas；
- 980 条 raw prerequisite；
- 数据重复、缺失、self-loop、cycle 审计事实；
- ExerciseRate / Rasch / Hierarchical Rasch 对照；
- Rasch 相对 ExerciseRate AUC +0.017465；
- 95% CI [0.012394, 0.022665]；
- Gate = `GO_RASCH_ONLY_INTEGRATION`。

`frontend/src/v1/researchSnapshot.js` 只是版本化冻结科研快照，不冒充实时数据治理 API。

当前 Java 生产推荐仍为 RuleBeta + Published Graph；Rasch 尚未接入生产，不允许把研究结果错误归因到线上推荐。

### 6. 个性化学习链保持真实后端调用

当前 V1 保留真实业务链：

```text
AnswerRecord
  -> RuleBeta mastery / UNKNOWN
  -> weak KnowledgePoint
  -> active Published Graph prerequisite
  -> Recommendation
  -> Learning Path
  -> Teacher Analytics
```

学生端可以完成：

- 在线答题；
- 服务端反馈；
- 更新学习建议；
- 查看推荐原因；
- 选择目标知识；
- 生成学习路径；
- 查看知识图谱节点及前置/后继关系。

### 7. 浏览器 E2E 与 release Gate 已跑通

PR #34 最新已验证 head（在本文件更新前）：

- SHA：`a0448ae781ba4f88b6189606bbca5f46c62f45fd`
- GitHub Actions CI run：`#158`
- run id：`34232579762`

结果：

```text
backend            PASS
python             PASS
frontend           PASS
compose-config     PASS
full-stack-release PASS
```

`full-stack-release` 内实际完成：

- 隔离 release 配置生成；
- full release stack build/start；
- readiness；
- API smoke；
- real browser Playwright E2E；
- browser artifacts upload；
- clean shutdown。

E2E JUnit：1 test / 0 failures / 0 errors。

浏览器 artifact 已生成 13 张真实页面截图，覆盖登录、学生首页、课程学习、知识点练习、练习反馈、个性化学习与路径、知识图谱、教师工作台、学生详情、管理工作台、数据与算法、教师授权、知识图谱治理。

本文件更新会产生新 head，因此最终 merge 仍必须以**更新后的最新 head CI 5/5 全绿**为准。

## 当前不能伪造为已完成的工作

以下内容仍未完成，统一由 `docs/CODEX_REAL_DATA_INTEGRATION_TASK.md` 作为下一阶段执行单：

1. 本机 Junyi 原始 CSV -> deterministic business export；
2. 真实 Area / Topic / Exercise 的中文 display mapping 与审核状态；
3. Java 幂等 / dry-run / 可审计真实数据导入与 ImportRun；
4. raw prerequisite -> Evidence -> review -> Published Graph 全量治理；
5. >100 节点真实图的筛选、局部图、搜索、路径高亮和学生状态叠加；
6. 正式 DatasetSource / DatasetVersion / ImportRun 数据治理 API；
7. 教学资源中心的正式资源实体、来源/授权、分类与知识点关联；
8. 章/节/知识集等课程层级是否需要扩展，必须由真实数据和业务需求驱动，不能前端硬造；
9. 过程性 / 诊断性 / 结果性评价与教师配置；
10. Rasch 生产辅助信号集成（如做必须 feature flag + 明确能力边界）；
11. 推荐/路径独立消融评测；
12. 真实数据接入后的最终 UI 截图和人工产品验收。

## 当前硬约束

- 不为了页面好看人工编课程；
- 不把 Junyi Exercise 伪造成具体 Question；
- 不把科研匿名学生导成平台 User；
- `Area != Topic != Exercise != Question`；
- raw prerequisite 只是 Evidence，不是真值；
- 不覆盖原始英文 label，中文使用 display mapping；
- 不把 Rasch theta 称为知识点 mastery；
- 不为了通过测试写学生 ID / 题目 ID / 课程 ID 专用逻辑；
- 任一 release job 失败都不能返回 `GO_RELEASE`。
