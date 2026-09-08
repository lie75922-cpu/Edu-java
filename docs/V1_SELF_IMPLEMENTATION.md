# V1 产品重构：本轮直接实现与当前边界

本文件记录 PR #34 当前实现状态。若与较早的“中文离散数学 Demo”描述冲突，以 `docs/V1_MATH_PLATFORM_BASELINE.md` 为准。

平台当前定位：**基于知识图谱与学习分析的中文数学智能学习平台**。

中文化只针对用户侧展示，不覆盖原始科研数据；真实数据是什么数学领域，就按真实学科语义建模和展示。当前《离散数学》仍只是小型全栈 fixture，用于验证完整业务链，不代表平台被锁死为离散数学，也不代表 Junyi 真实数据已经导入 Java Business Domain。

## 已直接实现

- 保留 Java 21 / Spring Boot / Spring Security / JWT / RBAC、MySQL / Neo4j / Redis / Flyway、AnswerRecord 幂等、Outbox、RuleBeta mastery / UNKNOWN、Evidence -> Draft -> GraphValidator -> Published Graph、Recommendation / Learning Path、Teacher course authorization / analytics、Docker / OpenAPI / Playwright / release CI。
- V1 中文学生端、教师端、管理端已经可运行。
- 后端角色继续区分 STUDENT / TEACHER / TEACH_ADMIN / SYSTEM_ADMIN。
- 新增 `GET /api/v1/courses/{courseId}/knowledge-areas`，学生课程页和知识图谱页按后端 KnowledgeArea/KnowledgePoint 数据组织，不再通过 `DM-*` knowledgeCode 在前端猜课程结构。
- 管理端“数据与算法”显示 DATA-0 / MODEL-3 冻结科研快照，同时明确生产推荐仍为 RuleBeta + Published Graph，Rasch 尚未接入生产。
- 个性化链保持真实后端调用：AnswerRecord -> RuleBeta mastery / UNKNOWN -> weak KnowledgePoint -> active Published Graph prerequisite -> Recommendation -> Learning Path -> Teacher Analytics。

## 当前 synthetic fixture

仍保留受控的小型中文离散数学 fixture 作为 E2E 数据：16 个知识点、16 个 ExerciseUnit、16 道中文平台 Question、12 条已审核先修关系。它只证明全栈链路，不代表真实 Junyi 数据已经产品化，也不代表平台最终被锁死为离散数学。

## Final Release Gate

截至本文件写入前，代码与 Codex 执行单的最终 head `4efcd351e634659263fd16de2e285bea6041cb1e` 已通过 GitHub Actions CI run #163（run id `34279102133`）：

```text
backend            PASS
python             PASS
frontend           PASS
compose-config     PASS
full-stack-release PASS
```

其中 `full-stack-release` 实际完成并通过：full release stack build/start、readiness、API smoke、真实 Playwright 浏览器 E2E、browser artifacts upload、clean shutdown。

注意：本文件本身的状态同步提交会形成新的 HEAD，因此 merge 时仍必须以 GitHub PR 页面显示的**最新 HEAD CI**为准；不得引用旧 SHA 绿灯替代最新 HEAD。

前一代码等价 head `d89650cdc64ec5938be3271aa5cb8e1d2f20d8fd` 的浏览器 artifact 已人工核验：13 张真实页面截图完整，JUnit = 1 test / 0 failures / 0 errors。

## 当前不能在本会话环境真实完成的工作

统一由 `docs/CODEX_REAL_DATA_INTEGRATION_TASK.md` 作为本机下一阶段执行单：

1. 本机 Junyi 原始 CSV -> deterministic business export；
2. 真实 Area / Topic / Exercise 中文 display mapping 与审核；
3. Java dry-run / 幂等 / 可审计 ImportRun；
4. raw prerequisite -> Evidence -> review -> Published Graph 全量治理；
5. >100 节点真实图的筛选、局部图、搜索、路径高亮和学生状态叠加；
6. DatasetSource / DatasetVersion / ImportRun 正式数据治理 API；
7. 教学资源中心；
8. 由真实数据驱动的课程层级扩展；
9. 学生/教师/管理员产品模块补齐；
10. 可选 Rasch feature flag；
11. 推荐/路径独立消融评测；
12. 真实数据接入后的最终 UI 与人工验收。

## 硬约束

- 不为了页面好看人工编课程；
- `Area != Topic != Exercise != Question`；
- 不把 Junyi Exercise 伪造成具体 Question；
- 不把科研匿名学生导成平台 User；
- raw prerequisite 只是 Evidence；
- 中文 display mapping 不覆盖原始英文 label；
- 不把 Rasch theta 称为知识点 mastery；
- 不写学生 ID / 题目 ID / 课程 ID 专用逻辑过测试；
- 任一最新 HEAD release job 失败都不能宣称可发布。
