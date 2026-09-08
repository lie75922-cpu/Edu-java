# V1 中文数学智能学习平台基线

> 状态：本文件自 2026-09-08 起作为 V1 产品与工程的最新口径。旧的“离散数学产品重构”文档保留为历史过程记录；若与本文件冲突，以本文件为准。

## 1. 产品定位

V1 定位为：**基于知识图谱与学习分析的中文数学智能学习平台**。

“中文”指面向学生、教师和管理员的业务名称、按钮、提示、课程/知识主题展示、推荐理由和分析说明均使用自然中文；不意味着篡改原始科研数据，也不意味着把原始数学数据强行改造成离散数学。

当前 Junyi 研究数据主体为数学学习数据，包含算术、代数、几何、概率统计、解析几何、微积分、逻辑等领域。最终课程/领域结构必须根据真实数据和业务建模结果确定，不为了“课程数量”造课程。

## 2. 必须贯通的完整链路

```text
数据来源与登记
  -> 数据清洗 / 质量审计 / EDA
  -> 原始英文语义 + 中文展示映射
  -> 课程 / KnowledgeArea / KnowledgePoint / ExerciseUnit
  -> Evidence / GraphVersion / Validator / Published Graph / Neo4j
  -> 学生作答 AnswerRecord
  -> 学习状态 RuleBeta（生产默认）
  -> 推荐候选 + 先修关系 + 错题历史 + 排序
  -> 个性化推荐 / 学习路径
  -> 学生端展示
  -> 教师学情分析
  -> 管理端数据、课程、图谱与权限治理
```

任何新功能如果不能说明位于上述链路哪一环、输入输出是什么、如何验收，则不应优先开发。

## 3. 数据与语义边界

当前 DATA-0 已审计事实：

- 247,606 名匿名学生；
- 25,925,992 条学习行为；
- 837 条 Exercise metadata；
- 835 个不同 Exercise external ID；
- 40 个非空 Topic；
- 8 个非空 Area；
- 980 条原始 prerequisite 记录；
- 2 条重复 Exercise external ID 记录；
- 20 条缺 Topic、20 条缺 Area；
- 原始 prerequisite 分析图含 2 个 self-loop、3 个 cyclic SCC。

语义约束：

1. `Area != Topic != Exercise != Question`；
2. 第一版业务映射候选：`KnowledgeArea <- Area`，`KnowledgePoint <- Topic`，`ExerciseUnit <- Exercise`；
3. `Question` 只能是平台自有/有权使用的具体题目，不得把 Junyi Exercise 元数据伪造成题干、选项、答案或解析；
4. 科研匿名学生不得自动创建为平台业务用户；
5. 原始英文名称必须保留，中文名称作为可审计的展示映射；
6. biology 等跨学科内容必须隔离审查，不得默认为数学课程内容；
7. 原始 prerequisite 只能作为 Evidence 输入，不能直接视为 Published Graph。

## 4. 当前产品状态

### 已完成/可复用

- Java 21 / Spring Boot / Spring Security / JWT / RBAC；
- MySQL / Neo4j / Redis / Flyway；
- AnswerRecord 幂等、Outbox、Mastery exactly-once；
- Evidence -> Draft -> GraphValidator -> Published Graph；
- RuleBeta Mastery；
- Recommendation / Learning Path；
- 教师课程授权与 learning analytics；
- Docker / OpenAPI / Playwright / backup-restore；
- 学生、教师、管理员中文 V1 入口；
- 学生课程结构已改为调用后端 KnowledgeArea/KnowledgePoint，不再按 `DM-*` 编码推断章节；
- 学生知识图谱已改为按后端 Area 分组，不再写死离散数学四章；
- 管理端增加“数据与算法”审计快照，明确科研数据、质量问题和 MODEL-3 结论；
- 当前生产推荐仍是 RuleBeta + Published Graph，未虚假声称 Rasch 已上线。

### 当前仍是演示数据的部分

仓库中的 Platform Demo Seed 仍以一个小型中文离散数学课程作为**合成业务演示 fixture**。它用于跑通学生/教师/管理员、答题、图谱、推荐和路径全栈流程，不代表平台只能做离散数学，也不代表真实 Junyi 数据已经导入业务数据库。

在真实数据桥接完成前，不得把该 fixture 的 16 个知识点、16 组练习、16 道题、12 条关系描述为真实数学知识库规模。

## 5. 算法边界

MODEL-3 最终独立留出结果：

| 方法 | AUC | ACC | RMSE |
| --- | ---: | ---: | ---: |
| ExerciseRate | 0.707210 | 0.820778 | 0.368169 |
| Rasch / IRT-1PL | 0.724675 | 0.826387 | 0.362658 |
| Hierarchical Rasch + Topic | 0.710015 | 0.811279 | 0.374733 |

Rasch 相对 ExerciseRate 的 AUC 差值为 `+0.017465`，学生簇 bootstrap 95% CI `[0.012394, 0.022665]`；最终 Gate 为 `GO_RASCH_ONLY_INTEGRATION`。

解释限制：

- 该结果支持 Rasch 作为后续可审查的全局能力/答题风险辅助信号；
- 不证明知识图谱、个性化路径或教学效果有效；
- 不允许把 Rasch latent ability 直接称为“知识点掌握度”；
- Java 当前生产 MasteryProvider 仍为 RuleBeta；任何 Rasch 接入必须经过独立设计、测试和消融。

## 6. V1 当前最高优先级

P0：真实研究数据到业务系统的桥梁，包括中文展示映射、Area/Topic/Exercise 导入、provenance、idempotent import 与审计。

P0：把原始 prerequisite 通过 Evidence -> resolution -> review -> GraphVersion -> Validator -> Published Graph 正式进入图谱治理链，而不是直接导入 Neo4j。

P1：知识图谱大规模可视化能力：搜索、领域筛选、局部子图、学生掌握状态叠加、学习路径高亮。

P1：把推荐证据链可视化：掌握状态、错误历史、未满足先修、图谱版本、规则版本和推荐理由。

P1：正式“数据治理”后端与管理页面，逐步替换当前只读的冻结科研快照。

P2：教学资源中心与更完整的教师/管理员信息架构。资源模型未建立前禁止前端伪造教材、视频、课件等业务数据。

## 7. 发布 Gate

V1 只有同时满足以下条件才能视为可发布候选：

- backend tests 全绿；
- model-service tests 全绿；
- data-pipeline tests 全绿；
- frontend build 全绿；
- compose-config 全绿；
- clean-volume full-stack startup 成功；
- API smoke 成功；
- Playwright 学生/教师/管理员真实浏览器 E2E 成功；
- 跨角色、跨课程权限隔离仍为 403；
- Published Graph 无 self-loop / cycle；
- 不提交 Junyi 原始数据、凭据、日志和依赖目录；
- 不伪造题目、资源、模型效果或线上算法归因。
