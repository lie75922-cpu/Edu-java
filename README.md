# Edu-java

**基于知识图谱与学习分析的中文数学智能学习平台**

> 当前状态：V0.7 Java 工程底座已形成可复用 Release Candidate；V1 正在把“数据处理 → 知识结构 → 图谱治理 → 学习状态 → 个性化推荐/路径 → 学生/教师/管理产品”真正连成一条业务链。当前中文业务 fixture 仍以离散数学为小型全栈演示，但平台不再锁死离散数学，真实数据是什么数学领域就按真实语义建模和中文展示。

最新产品基线：`docs/V1_MATH_PLATFORM_BASELINE.md`  
真实数据接入 Codex 执行单：`docs/CODEX_REAL_DATA_INTEGRATION_TASK.md`

## 1. 项目目标

平台不是单纯课程 CRUD，也不是只展示一张 Neo4j 图。

目标链路：

```text
真实学习数据
  -> 数据来源登记 / 清洗 / EDA / 质量审计
  -> 原始英文语义 + 中文展示映射
  -> KnowledgeArea / KnowledgePoint / ExerciseUnit
  -> Evidence / GraphVersion / Validator / Published Graph
  -> 学生 AnswerRecord
  -> RuleBeta 学习状态
  -> 图谱先修约束 + 错题历史 + 推荐排序
  -> 个性化推荐 / 学习路径
  -> 学生学习页面
  -> 教师学情分析
  -> 管理端数据 / 课程 / 图谱 / 权限治理
```

用户侧使用自然中文；底层 Java、数据库字段和原始科研数据可以保持英文。中文化不等于篡改原始数据。

## 2. 当前真实数据基础

DATA-0 已完成来源登记、Schema/质量审计、EDA、图关系审计、学生级划分和模型输入构造。

| 指标 | 已审计值 |
| --- | ---: |
| 匿名学生 | 247,606 |
| 学习行为 | 25,925,992 |
| Exercise metadata | 837 |
| distinct Exercise external ID | 835 |
| non-empty Topic | 40 |
| non-empty Area | 8 |
| 整体正确率 | 0.827874 |
| raw prerequisite rows | 980 |
| duplicate Exercise external-ID records | 2 |
| missing Topic rows | 20 |
| missing Area rows | 20 |

原始 prerequisite 分析图存在 self-loop 和 cycle，因此**不能直接导入 Neo4j 当成生产知识图谱**。

数据来源边界：当前执行环境未直接取得官方 PSLC/DataShop 原包，实际研究输入为已登记的第三方镜像；该 provenance 限制继续保留。

### 语义边界

```text
Area != Topic != Exercise != Question
Research Student != Platform User
Raw prerequisite Evidence != Published Graph
```

第一版真实业务映射候选：

```text
KnowledgeArea <- Area
KnowledgePoint <- Topic
ExerciseUnit <- Exercise
Question <- 仅平台自有/有权使用的具体题目
```

原始英文名称必须保留；中文名称通过版本化 display mapping 提供。

## 3. 当前 Java 工程底座

- Java 21 / Spring Boot / Spring Security / JWT / RBAC
- MySQL 8.4：业务权威数据
- Neo4j：已发布知识图查询投影，可从 MySQL/GraphVersion 重建
- Redis：可选缓存
- Flyway V001–V006
- Course / KnowledgeArea / KnowledgePoint / ExerciseUnit / Question
- AnswerRecord 幂等
- Outbox
- Mastery exactly-once
- Evidence -> Draft -> GraphValidator -> Published Graph -> Neo4j
- RuleBeta mastery，零历史保持 UNKNOWN/“暂无学习数据”
- Recommendation / Learning Path
- Teacher course authorization / analytics
- Docker Compose / OpenAPI / health-readiness / Playwright / backup-restore

## 4. V1 当前产品形态

### 学生端

- 首页：课程、学习记录、薄弱知识、平均掌握情况、图谱摘要、推荐摘要
- 课程学习：从后端 `KnowledgeArea -> KnowledgePoint` 动态生成领域和知识目录
- 知识图谱：按后端 Area 分组，查看前置/后继关系
- 个性化学习：薄弱知识、推荐理由、学习路径
- 真实服务端答题与判题闭环

**重要纠偏：**课程页和知识图谱页已经删除通过 `DM-LOGIC / DM-GRAPH / DM-ALG` 等编码判断章节的前端硬编码。

### 教师端

- 授权课程列表
- 在读/活跃学生、累计作答、整体正确率
- KnowledgePoint 班级掌握概览
- 高频错误题
- 学生 × KnowledgePoint 热力表
- 单学生作答、掌握状态、推荐上下文
- 跨课程权限隔离

### 管理端

当前拆分为：

- 课程与教学
- 数据与算法
- 教师授权
- 知识图谱治理
- 系统状态

“数据与算法”当前展示的是仓库已经冻结、可审计的 DATA-0 / MODEL-3 快照，用于让产品评审看到数据处理和算法研究工作；它**不是实时数据治理后端**，后续会由 DatasetSource / DatasetVersion / ImportRun API 替代。

## 5. 当前合成业务 fixture 的边界

为了让 CI 可以在没有 Junyi 原始 CSV 的 GitHub Runner 上完整跑通学生/教师/管理员流程，目前仍保留一个小型中文离散数学业务 fixture：

- 主课程 `DM-101 离散数学`
- 16 个知识点
- 16 个练习单元
- 16 道中文题
- 12 条已审核先修关系
- 少量合成学生/教师/管理员

它的用途是**全栈演示与自动化测试**，不是：

- 平台最终学科限制；
- 真实 Junyi 业务导入结果；
- 真实知识库规模；
- 教学效果证据。

## 6. 个性化推荐目前怎样工作

当前生产链：

```text
学生作答
  -> RuleBeta KnowledgePoint mastery
  -> 找到低于阈值的薄弱知识
  -> 查询 Published Graph 前置关系
  -> 补充未掌握 prerequisite
  -> 结合近期错误 / 复习间隔
  -> 过滤和确定性排序
  -> Recommendation Snapshot
  -> Learning Path
```

当前弱掌握阈值：`0.70`。

系统保留 UNKNOWN 语义：没有真实历史记录的知识点不伪造为 0.5 掌握度。

## 7. 算法研究结论与生产边界

MODEL-3 最终验证：

| 方法 | AUC | ACC | RMSE |
| --- | ---: | ---: | ---: |
| ExerciseRate | 0.707210 | 0.820778 | 0.368169 |
| Rasch / IRT-1PL | 0.724675 | 0.826387 | 0.362658 |
| Hierarchical Rasch + Topic | 0.710015 | 0.811279 | 0.374733 |

Rasch - ExerciseRate AUC = `+0.017465`，student-cluster bootstrap 95% CI `[0.012394, 0.022665]`。

Final Gate：`GO_RASCH_ONLY_INTEGRATION`。

这表示 Rasch 可以进入下一阶段**独立审查的辅助信号集成设计**，不表示它已经上线。当前 Java 生产 Mastery/Recommendation 仍由 RuleBeta + Published Graph 驱动。

Rasch θ 只能解释为全局能力/答题风险辅助信号，不能写成知识点掌握度。

## 8. 当前最重要的未完成工作

P0：**真实 Junyi 数学数据 → Java Business Domain**。

需要在有本地原始数据的环境完成：

1. Area / Topic / Exercise 的确定性业务导出；
2. 原始英文 label + 中文 display mapping；
3. Java 幂等、可审计 ImportRun；
4. raw prerequisite -> Evidence -> resolve/review -> GraphVersion -> Published Graph；
5. 实际导入数量与冲突报告；
6. >100 节点知识图谱的筛选/局部图，而不是默认一次画全部节点；
7. 推荐证据链可视化；
8. 正式数据治理 API。

本地真实输入路径与完整执行要求已经写入：

`docs/CODEX_REAL_DATA_INTEGRATION_TASK.md`

## 9. 当前不做/不能伪造

- 不把 Junyi Exercise 元数据变成假的 Question 题干/答案；
- 不把科研匿名学生导成平台 User；
- 不直接发布有 self-loop/cycle 的原始 prerequisite；
- 不虚构教材、视频、课件等资源中心内容；
- 不虚构学习效果提升；
- 不新增与需求无关的 LLM/Agent、Kafka、微服务、Kubernetes；
- 不为了页面看起来丰富而硬造课程数量。

## 10. Release Gate

每个 Release Candidate 都必须通过：

- backend tests
- model-service tests
- data-pipeline tests
- frontend build
- compose-config
- clean-volume full-stack startup
- API smoke
- Playwright 学生/教师/管理员真实浏览器 E2E
- 跨角色/跨课程权限隔离
- 不提交原始数据/凭据/日志/依赖目录

任何一项失败都不能写 `GO_RELEASE`。
