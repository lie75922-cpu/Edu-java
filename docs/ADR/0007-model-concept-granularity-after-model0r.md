# ADR-0007：MODEL-0R 后的 ModelConcept 粒度与重新验证边界

- 状态：Accepted
- 决策日期：2026-09-08
- 依据：DATA-0、MODEL-0A、MODEL-0R 以及 Junyi 公开认知诊断实现

## 背景

MODEL-0R 在冻结的 40 Topic Q-matrix 上完成了泄漏受控的 warm / cold 两类实验。

Warm NCDM 的答题预测并非完全失败：AUC=0.765630，高于 validation 预先选择的 Exercise historical-rate baseline 0.748487，paired bootstrap AUC delta lower bound=0.014948。但用于“认知诊断”的 40 维 mastery 明显塌缩：

- NCDM median Topic std = 0.004668；
- NCDM mean pairwise cosine = 0.999815；
- ORCDF median Topic std = 0.001813；
- ORCDF mean pairwise cosine = 0.999987。

因此 MODEL-0R 的 `NO_GO` 不能只解释为“模型不够强”。必须重新检查 **ModelConcept / Q-matrix 语义是否与 Junyi 认知诊断研究中的概念粒度一致**。

公开实现给出强烈反证：

- RCD 官方仓库 `RCD/config.txt` 明确配置 Junyi 为 `10000 students, 835 exercises, 835 knowledge concepts`；
- InsCD 内置数据集名称为 `Junyi734`，相关认知诊断研究通常使用 734 exercises / 734 concepts 的过滤版本；
- GEAR-CD 对 Junyi 报告 835 exercises / 835 knowledge concepts；
- EduData 的 Junyi 说明明确指出：在 Junyi 中，一个 Exercise 对应一个 knowledge unit。

而本项目 MODEL-0 / MODEL-0R 使用的是业务层 40 Topic 作为模型Concept。

ADR-0002 已经定义：`ModelConcept` 属于算法适配层，不等于平台 `KnowledgePoint`。因此无需推翻业务领域模型；需要纠正的是算法层粒度。

## 决策

### 1. 40 Topic 保留为业务 KnowledgePoint 展示层

平台领域模型继续保持：

```text
KnowledgeArea
  -> KnowledgePoint (初始来源 Junyi Topic)
  -> ExerciseUnit
  -> Question
```

V0.4 规则 Mastery、推荐、学习路径不因为本 ADR 改写。

### 2. 40 Topic 不再作为下一轮唯一 ModelConcept 定义

`C40_TOPIC` 保留为历史对照，但 MODEL-1 不允许直接假设：

```text
ModelConcept == business KnowledgePoint == Junyi Topic
```

MODEL-0R 已经证明该定义在当前数据/实现下会产生严重 mastery collapse。

### 3. 下一轮至少比较两种算法概念表示

#### C40_TOPIC

历史 MODEL-0R 表示：624 个 Q-eligible Exercise -> 40 Topic。

用途仅为基准，不再作为默认最终方案。

#### C624_EXERCISE_CONCEPT

对 Medium 当前 624 个 Q-eligible / observed Exercise 建立可审计的一对一 ModelConcept：

```text
Exercise external ID
 -> stable model_concept_id
```

Q-matrix 为 624 x 624 identity-style mapping：每个 Exercise 对应一个细粒度 ModelConcept。

这里的 `ModelConcept` 是算法概念，不是平台 `KnowledgePoint`，也不是平台 `Question`。

### 4. 允许层次化投影，但不反向污染业务ID

允许研究：

```text
ExerciseModelConcept (fine)
 -> Topic KnowledgePoint (business aggregation)
 -> Area
```

模型输出必须经 Adapter 聚合/投影后才能进入业务层。

禁止把 model_concept_id 写成 `knowledge_point.id`。

### 5. prerequisite 在研究图与正式业务图之间继续隔离

- MODEL-1 可以在研究目录中把 raw Exercise prerequisite 作为 `RESEARCH_MODEL_GRAPH`，前提是 source/target external ID 唯一、可追溯，并保留 self-loop/cycle 处理记录；
- 该图不等于 V0.3 `PUBLISHED_PREREQUISITE`；
- 模型实验不得修改 Neo4j 正式图。

### 6. MODEL-0R test 已被观察，下一轮必须使用新 holdout

MODEL-0R 的 test 指标和 collapse 已经参与了本次研究决策，因此不得继续把同一 test 作为“新的最终无偏验证”。

MODEL-1 必须在训练任何新概念方案前，从完整数据中选择 **未进入原 Medium 10k 的学生**，生成新的固定 external holdout；成员列表先冻结，随后才允许模型运行。

新 holdout 的选择只能依据预注册的数据质量、序列长度和 Exercise scope 规则，不得依据模型结果或未来 test label 选择样本。

### 7. 先做概念粒度消融，再决定图模型

MODEL-1 第一阶段只用同一 NCDM 实现比较 `C40_TOPIC` 与 `C624_EXERCISE_CONCEPT`，尽量只改变 Q-matrix / ModelConcept 定义。

目的：判断 mastery collapse 的主要来源究竟是概念粒度，还是模型/数据本身。

只有细粒度概念方案通过预注册的预测与 mastery sanity gate，才允许继续 RCD / ORCDF / GEAR-CD 等图模型。

## 结果

本 ADR 不授权任何生产模型集成。

下一研究 Gate 为 `MODEL-1 Concept Resolution`：

```text
Concept audit
 -> new untouched holdout freeze
 -> NCDM concept-granularity ablation
 -> mastery sanity
 -> conditional graph-model experiments
```

若细粒度方案仍明显塌缩，则停止继续堆叠复杂图模型，平台继续使用 V0.4 透明规则 Mastery，并重新评估 Junyi 是否适合作为认知诊断研究数据。