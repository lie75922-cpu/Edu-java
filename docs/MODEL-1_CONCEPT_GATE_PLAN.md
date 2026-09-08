# MODEL-1 Concept Resolution Gate

## 1. 目标

MODEL-1 不是“再跑一个更复杂模型”。

目标是先验证：MODEL-0R 的严重 mastery collapse 是否主要由 **把 40 个业务 Topic 直接当成模型Concept** 引起。

只有概念层验证通过，才继续图认知诊断模型。

## 2. 已冻结事实

历史证据保持不变：

- MODEL-0A：zero-history cold-start 负实验；
- MODEL-0R：40-Topic warm/cold 实验；
- Warm NCDM AUC 0.765630，显著高于 Exercise-rate baseline 0.748487；
- 但 NCDM 40-Topic mastery median std 0.004668、mean pairwise cosine 0.999815；
- ORCDF 更严重塌缩；
- 原 MODEL-0R test 已被观察，不再作为下一轮新的最终无偏测试集。

业务域继续使用 Topic KnowledgePoint；本阶段只改变算法 `ModelConcept`。

## 3. WP-M1.1 — Concept provenance audit

必须生成：

```text
model-service/experiments/model1/reports/concept_audit.md
model-service/experiments/model1/manifests/model_concept_catalog_v1.json
```

至少核对：

1. DATA-0 raw Exercise external IDs；
2. Medium observed / Q-eligible Exercise IDs；
3. Exercise -> Topic 映射；
4. RCD Junyi 835 exercise / 835 concept 设置；
5. InsCD Junyi734 数据语义；
6. GEAR-CD Junyi exercise/concept统计；
7. raw prerequisite Exercise 图。

不得因为公开论文用了835 concept就直接假定本地每个raw row一一可用。必须在本地身份层重新验证。

输出至少包括：

- raw unique Exercise external IDs；
- Medium observed Exercise；
- Q-eligible Exercise；
- one-to-one Exercise->Topic coverage；
- duplicate identity；
- missing Topic；
- model concept candidate count；
- prerequisite model graph nodes/edges/self-loop/cycle/components。

## 4. WP-M1.2 — 新 external holdout 冻结

因为 MODEL-0R test 已参与研究决策，本轮必须创建新 holdout。

候选池：完整数据中 **不属于原 JUNYI_MID 10,000 学生** 的匿名学生。

预注册规则：

1. 只保留当前模型 Exercise scope 内的eligible interactions；
2. 学生至少有 20 条 eligible chronological interactions；
3. 不使用 correct label 决定是否入选，只使用身份、Exercise scope、时间有效性和序列长度；
4. 按 eligible sequence length 分层抽样；
5. 目标 5,000 名学生；若不足则使用所有满足条件者并报告；
6. random seed 固定为 `20260908`；
7. 成员列表和 manifest 必须在任何 MODEL-1 训练前生成并冻结。

生成：

```text
model-service/experiments/model1/manifests/junyi_external_holdout_v1.json
model-service/experiments/model1/reports/holdout_freeze.md
```

Holdout一旦冻结，不能因模型效果调整成员。

## 5. WP-M1.3 — 只做 NCDM 概念粒度消融

第一轮禁止同时更换网络结构。

同一个可审计 NCDM 实现，同一优化设置，同一 fresh holdout，比较：

### C40_TOPIC

历史业务Topic概念层：

```text
624 Exercise -> 40 Topic
```

### C624_EXERCISE_CONCEPT

细粒度算法概念层：

```text
624 Exercise -> 624 ModelConcept
Q = identity-style 624 x 624
```

如果 fresh holdout 实际eligible Exercise数不同，C624名称按冻结后的实际数量记录，不能为了名字强凑624。

## 6. Fresh holdout评测协议

对新holdout每名学生按时间：

```text
first 60% -> fit/train history
next 20% -> validation
last 20% -> final test
```

短序列舍入规则必须预先固定并记录。

不得使用future interaction拟合过去状态。

Test只在配置完全冻结后读取一次。

必须包含：

- Global-rate baseline；
- Exercise-rate baseline；
- Topic-rate baseline；
- NCDM C40；
- NCDM fine-concept。

## 7. 评价指标

### Response prediction

- AUC
- ACC
- RMSE
- Log Loss
- paired bootstrap delta vs best validation-selected non-personalized baseline

### Diagnosis / mastery sanity

细粒度Concept至少记录：

- per-concept coverage；
- mastery min/P05/median/P95/max；
- per-concept student std distribution；
- median concept std；
- pairwise cosine distribution；
- DOA或等价future-response agreement（只有定义成立时）；
- 20名匿名学生的细粒度 mastery sanity case；
- 按Topic聚合后的业务可解释视图。

## 8. Concept Gate

为了避免再根据test结果临时改标准，预注册以下最小门槛。

Fine-concept方案只有同时满足以下条件，才进入图模型阶段：

1. Test AUC 高于 validation-selected best non-personalized baseline，paired-bootstrap AUC delta lower bound > 0；
2. median per-concept student std >= 0.010；
3. mean pairwise mastery cosine <= 0.995；
4. 至少 95% 的eligible ModelConcept在训练集中有有效学生覆盖；
5. mastery输出不存在大规模NaN/常数列；
6. Topic聚合后能够给出稳定、非塌缩的业务解释视图。

这些阈值是针对 MODEL-0R 已观察到的 collapse 预先设定的诊断门槛，不代表通用学术标准。

## 9. WP-M1.4 — 条件图模型阶段

只有 WP-M1.3 通过后才执行。

优先顺序：

### RCD common-graph adaptation

不复用无法追溯的 opaque numeric RCD concept graph。

构建本项目自己的可审计映射：

```text
Exercise external ID -> sequential model_concept_id
```

raw Exercise prerequisite只作为 `RESEARCH_MODEL_GRAPH`。

必须保留：

- external ID mapping；
- self-loop/cycle处理；
- source provenance；
- 与正式 Published Graph 的隔离声明。

然后再判断RCD是否可公平运行。

### ORCDF fine-concept

只有 NCDM fine-concept通过 Concept Gate 后才重新尝试，避免在错误Concept层上继续堆图传播。

### GEAR-CD

仍为条件高成本路线。先重新审计fine-concept输入和运行时，不直接完整Medium训练。

## 10. Cold-start

MODEL-1 第一优先级是解决 warm diagnosis concept collapse。

如果 warm fine-concept 未通过，不继续cold-start复杂模型。

如果warm通过，再单独设计 MODEL-1C cold-start，不与本Gate混在一次test使用中。

## 11. 输出目录

```text
model-service/experiments/model1/
├── README.md
├── configs/
├── manifests/
├── reports/
│   ├── concept_audit.md
│   ├── holdout_freeze.md
│   ├── ncdm_concept_ablation.md
│   ├── mastery_sanity.md
│   ├── graph_readiness.md
│   ├── bad_cases.md
│   ├── cost.md
│   └── final_gate.md
└── tests/
```

## 12. Final Gate

只允许：

- `GO_FINE_CONCEPT_GRAPH_MODELS`
- `CONDITIONAL_GO`
- `NO_GO_CONCEPT_LAYER`

含义：

- `GO_FINE_CONCEPT_GRAPH_MODELS`：细粒度Concept解决了主要collapse并超过统计baseline，可以授权RCD/ORCDF等下一轮；
- `CONDITIONAL_GO`：预测改善但mastery仍有风险，只允许进一步诊断，不允许生产集成；
- `NO_GO_CONCEPT_LAYER`：细粒度概念仍无法形成有意义诊断，停止继续堆复杂图模型。

结束后停止，不修改Java `MasteryProvider`生产路由，不覆盖V0.4规则Mastery。