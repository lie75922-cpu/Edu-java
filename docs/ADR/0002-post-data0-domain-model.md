# ADR-0002：DATA-0 后领域分层与模型概念边界

- 状态：Accepted
- 决策日期：2026-09-07
- 依据：`DATA-0` 分支审计结果

## 背景

DATA-0 已确认当前 Junyi 镜像中存在 837 条 Exercise 元数据记录、835 个不同 external ID、40 个非缺失 Topic、8 个非缺失 Area；完整 ProblemLog 含 25,925,992 条交互，Medium 候选集含 10,000 名学生和 284,245 条交互。

审计同时表明：

1. Junyi Exercise 是稳定的练习/能力单元，但并不提供平台 `Question` 所需的完整题干、选项、标准答案与解析；
2. Topic/Area 是明确存在于元数据中的分类层级，但 DATA-0 没有证据支持把 Exercise 直接等同于 KnowledgePoint；
3. RCD 等模型内部的 concept ID 无法追溯地映射到当前原始 external ID，因此算法 concept 不应反向决定业务领域模型；
4. 20 条元数据缺失 Topic/Area，2 个 external ID 存在重复，不能依赖 external ID 作为数据库主键。

## 决策

### 1. Area

`Area` 作为最高层课程/学科分类证据，不定义为 KnowledgePoint。

在平台业务域中可映射为课程内的 `KnowledgeArea` 或章节级分类，但不参与细粒度认知诊断主键。

### 2. Topic

`Topic` 作为**用户可解释的知识主题层**。

V0.2 中允许将 Topic 作为第一版 `KnowledgePoint` 的来源候选，用于：

- 学生掌握度聚合；
- 教师学情统计；
- 页面展示；
- Exercise → KnowledgePoint 的初始映射。

但必须保留 `source_type=JUNYI_TOPIC`，不能声称它是人工重新标注的标准知识点体系。

缺失 Topic 的 Exercise 不自动伪造 KnowledgePoint，标记为 `UNMAPPED`，后续人工治理或数据补充。

### 3. ExerciseUnit

Junyi `Exercise` 映射为平台的 `ExerciseUnit` / 能力练习单元，而不是 `Question`。

`ExerciseUnit` 与 `KnowledgePoint` 分离：

```text
KnowledgeArea
   └─ KnowledgePoint (Topic)
          └─ ExerciseUnit (Junyi Exercise)
                 └─ Question (平台业务题目)
```

其中一个 ExerciseUnit 第一版可以映射到一个 Topic；未来平台自有题库允许多知识点映射，数据库设计不得把一对一写死。

### 4. Question

`Question` 是 Platform Business Domain 独立实体，仅来自平台题库或明确授权的数据源。

Junyi DATA-0 不提供足以生成正式 `Question` 的完整内容，因此不得把 Junyi Exercise 直接导入 `question` 表。

### 5. ModelConcept

`ModelConcept` 属于**算法适配层**，不是业务领域实体。

NCDM、RCD、ORCDF、GEAR-CD 可以因模型需要使用不同 concept/Q-matrix 图结构，但所有模型结果必须通过 Adapter 投影回平台的 KnowledgePoint/Topic 层后才能被业务系统消费。

因此禁止：

```text
RCD concept ID = platform KnowledgePoint ID
```

除非未来建立了可审计的一致映射。

## 身份规则

- 数据库主键必须使用内部 surrogate ID；
- `external_id` 只作为来源标识，不做全局唯一主键；
- 重复 external ID 进入 `IDENTITY_CONFLICT`/人工治理流程，不能静默合并；
- Research Student 永远不自动映射为 `sys_user`。

## 结果

该决策足以允许 V0.2 开始设计 Course / KnowledgePoint / ExerciseUnit / Question / Mapping 表结构，同时不阻塞后续 MODEL-0 使用模型特定概念映射。

本 ADR 不批准任何 Junyi prerequisite 进入正式 Neo4j 学习路径图；关系发布规则由 ADR-0003 定义。
