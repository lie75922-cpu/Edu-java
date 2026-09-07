# ADR-0003：Prerequisite证据与正式知识图发布规则

- 状态：Accepted
- 决策日期：2026-09-07
- 依据：DATA-0图审计

## 背景

DATA-0 对多个关系来源分别审计后确认：

- Exercise元数据解析视图：833个可用于身份敏感分析的节点、约979条候选边、69个弱连通分量；存在2个自环和1个三节点循环，合计3个 cyclic SCC；
- RCD `K_Directed`：与当前原始 external ID 不具备可追溯映射；
- relationship annotation：只有评分证据，未审计出可直接发布的方向与阈值；
- Topic/Area层级本身无循环，但它表达的是分类层级，不等同于学习先修关系。

因此，外部关系不能在导入时直接成为正式 `PREREQUISITE`。

## 决策

### 1. 分离 Evidence 与 Published Graph

MySQL保存关系生命周期与证据，Neo4j只保存经发布的查询投影。

关系至少区分：

- `RAW_PREREQUISITE_EVIDENCE`
- `ANNOTATION_EVIDENCE`
- `REFERENCE_GRAPH_EDGE`
- `PUBLISHED_PREREQUISITE`

外部数据导入时默认只能进入 Evidence 状态。

### 2. 自环规则

任何 `source == target` 的先修关系：

- 原始证据必须保留；
- 标记 `REJECTED_SELF_LOOP`；
- 不得进入 `PUBLISHED_PREREQUISITE`；
- 不得用于学习路径拓扑排序。

DATA-0 已观测到的两个自环按此规则处理，而不是删除原始记录。

### 3. 循环规则

任何多节点有向循环：

- 原始边全部保留；
- 标记所在冲突组 `CONFLICT_CYCLE`；
- 在人工/规则审查完成前，冲突组内相关边不得发布；
- Graph Publish Validator 必须对 PUBLISHED 候选图执行 DAG 检查；
- 检测到循环则发布失败。

DATA-0 中 `adding_and_subtracting_radicals → radical_multiplication_and_division → simplifying_radicals → adding_and_subtracting_radicals` 的三节点循环属于该规则覆盖范围。

### 4. 重复身份规则

存在重复 Exercise external ID 时：

- 不基于 external ID 自动合并边；
- 身份冲突记录不进入自动发布图；
- 关系必须先解析到内部唯一 ExerciseUnit ID。

### 5. relationship annotation规则

`Similarity_avg`、`Difficulty_avg`、`Prerequisite_avg` 等评分在没有正式阈值和方向定义前：

- 只保留为 Evidence；
- 不自动生成生产边；
- MODEL-0可以把它作为实验特征或辅助监督，但必须与正式业务图分开评价。

### 6. RCD图规则

RCD numeric concept graph 在无法建立 traceable ID mapping 前：

- 只允许作为 REFERENCE_GRAPH；
- 不导入平台 KnowledgePoint；
- 不用于正式推荐或学习路径。

## Neo4j发布要求

只有满足以下条件的关系才允许投影为：

```text
(:KnowledgePoint|:ExerciseUnit)-[:PREREQUISITE]->(...)
```

1. 两端内部业务ID唯一；
2. 无自环；
3. 无重复边；
4. 所属发布候选图无有向循环；
5. relation source / evidence可追溯；
6. 发布版本通过GraphValidator；
7. 状态为 `PUBLISHED`。

## 结果

V0.2可以建立关系证据表和图版本机制，但在新的关系治理/模型实验完成前，不需要强行把Junyi原始prerequisite转成正式知识路径。
