# ADR-0008：Observability-aware Mastery 与个体化信号 Gate

- 状态：Proposed
- 决策日期：2026-09-08
- 依据：MODEL-0A、MODEL-0R、MODEL-1 实验与独立代码审查

## 1. 背景

截至 MODEL-1，项目已经得到三轮相互独立但不能简单合并解释的证据：

1. MODEL-0A 证明“完全未见学生 + zero-history representation”不适合作为传统 NCDM / ORCDF 的最终模型选择协议；
2. MODEL-0R 在原 Medium 10k 上使用 per-student chronological 60/20/20 后，C40_TOPIC NCDM 的答题预测优于 Exercise-rate baseline，但完整 40 维 mastery 被预注册的 collapse 指标判定为 FLAGGED；
3. MODEL-1 在与 Medium 10k 零重叠的 fresh external holdout 5k 上再次观察到：
   - Exercise-rate test AUC = 0.708358；
   - C40_TOPIC NCDM test AUC = 0.719500，paired-bootstrap lower bound = +0.009695；
   - C_FINE_EXERCISE NCDM test AUC = 0.708524，lower bound = -0.001122；
   - fine route 未证明预测增益，因此 `NO_GO_CONCEPT_LAYER` 按预注册 Gate 成立。

MODEL-1 独立审查进一步发现：当前 `mastery_sanity` 对完整 student × concept 矩阵直接计算每列标准差和全向量 cosine，而没有区分某个学生在 train/history 中是否真正接触过该 Concept。

对于当前一行仅一个非零 Q-entry 的 NCDM：

```text
student-concept 未被该学生的 train/history Exercise 命中
  -> 该 student embedding dimension 没有直接 student gradient
  -> 值可能长期停留在初始化附近
  -> sigmoid 后接近 0.5
```

因此，尤其在 `C_FINE_EXERCISE` 的 624 维空间中，大量未观测维度会以“接近 0.5 的数值”进入完整向量统计。业务 V0.4 对无历史 KnowledgePoint 的正式语义却是 `UNKNOWN`，并不会把 0.5 当作真实掌握度。

这形成了研究层与业务层的语义不一致：

```text
Model numeric prior ≠ Observed mastery evidence
```

## 2. 外部方法学依据

### 2.1 NCDM

NCDM 的目标是根据学生与习题交互学习可解释的知识概念掌握向量，并通过 Q-matrix 限定每道题所需知识概念：

- Wang et al., “Neural Cognitive Diagnosis for Intelligent Education Systems”, AAAI 2020.
- DOI: `10.1609/aaai.v34i04.6080`

当前项目的 `NCDMAdapter` 已与 InsCD 1.3.1 `Default + NCD_IF` 源码语义逐项核对：student mastery 为 `sigmoid(student_embedding)`，interaction 为 discrimination × (student - difficulty) × Q-mask 后进入单调 MLP。

### 2.2 Q-matrix repeated-measure / identifiability 风险

经典认知诊断理论长期强调，一个 attribute/concept 需要由多个 item 重复测量，才能获得可识别、可解释的属性掌握结论。多个严格可识别性条件要求每个 attribute 至少被 2～3 个 item 测量。

因此，虽然公开 Junyi RCD 配置使用 `835 exercises / 835 concepts`，但“每个 Exercise = 唯一 Concept”的 identity-style Q 不能仅凭数量一致就被视为适合本平台的可解释 mastery 体系。MODEL-1 的 fine 预测结果也没有证明该路线具有增量价值。

参考：
- Chen et al. / Liu et al. 等关于 Q-matrix identifiability 的统计分析；
- Kim, Köhn & Chiu, “Identifiability conditions in cognitive diagnosis”, British Journal of Mathematical and Statistical Psychology, 2025, DOI `10.1111/bmsp.70020`。

### 2.3 Oversmoothing 指标

ORCDF（KDD 2024）指出，多种 CDM 可能学习到过于相似的 mastery，并提出 MND（mean normalized difference）衡量学生 mastery 差异。

但本项目必须额外区分：

- 模型训练过/可观测的 student-concept pair；
- 没有 train/history evidence 的 pair。

否则“UNKNOWN prior”可能被误解释成“真实 mastery 相似”。

## 3. 决策

### 3.1 C_FINE_EXERCISE

`C_FINE_EXERCISE` 不再作为下一轮图认知诊断默认 Concept 层。

原因不是“624维太多”，而是：

1. fresh holdout 上没有证明比 Exercise-rate baseline 更好；
2. 一 Exercise 一 Concept 缺少 repeated measurement；
3. 大量 student-concept pair 对具体学生不可观测；
4. 继续在此基础上堆 RCD / ORCDF / GEAR-CD 无法回答根因。

### 3.2 C40_TOPIC

C40_TOPIC 暂不批准生产集成，也不被当前 full-vector collapse 指标直接淘汰。

它保留为唯一需要进一步诊断的模型路线，因为：

- MODEL-0R 和 MODEL-1 两个不同学生样本均显示其 response prediction 稳定高于 Exercise-rate baseline；
- 但尚未证明该增量来自 student-specific information；
- 也尚未证明“有 train/history evidence 的 Topic mastery”具有足够区分度和未来行为解释力。

### 3.3 UNKNOWN 语义

任何未来 `ModelMasteryProvider` 必须显式携带 observability/confidence。

对于无足够训练/校准 evidence 的 student-concept：

```text
UNKNOWN
```

不得因为模型 embedding 数值约为 0.5 就向业务层输出 `mastery=0.5`。

### 3.4 不再直接执行复杂图模型

在完成 MODEL-2 Personalization Signal Gate 前，禁止继续：

- RCD training；
- ORCDF fine-concept rerun；
- GEAR-CD full training；
- 其它通过堆 GCN/GAT/Transformer 试图“救指标”的实验。

下一阶段只回答：

> 当前数据是否存在能够稳定超过纯 Exercise 难度统计的 student-specific signal，以及 C40 observed mastery 是否真的能解释未来表现？

## 4. MODEL-2 的三个核心诊断

### A. Observability-aware mastery

按 student-topic 的 train/history exposure count 建 mask：

```text
k >= 1
k >= 3
k >= 5
```

只在满足 exposure 的 pair 上评估：

- mastery distribution；
- per-Topic student std；
- per-student observed-Topic std/range；
- observed future-response agreement；
- NCDM mastery vs V0.4 Beta rule mastery。

完整矩阵指标继续保留，但不得再成为唯一解释。

### B. Student-identity ablation

对同一个已训练 C40 NCDM，不改 item/difficulty/MLP 参数，比较：

1. ORIGINAL student embedding；
2. POPULATION_MEAN student embedding；
3. ZERO student embedding；
4. deterministic PERMUTED student embedding。

如果 ORIGINAL 与这些对照几乎无差别，则 NCDM 的预测提升主要不是来自个体学生表示，不能称为个性化诊断增益。

### C. 简单可解释 student signal

用不依赖 Q-matrix 的简单模型测试 student-specific signal：

- Exercise historical-rate baseline；
- smoothed Student global ability；
- Rasch / IRT-1PL（student ability + Exercise difficulty）；
- V0.4 Topic Beta mastery 作为未来同 Topic 行为信号。

只有简单模型也能稳定证明 student signal，才值得继续研究更复杂个性化模型。

## 5. 统计单位

MODEL-0R / MODEL-1 的部分 paired bootstrap 以 interaction 为抽样单位。

由于同一学生存在大量重复交互，MODEL-2 的主要增量置信区间改为：

```text
student-cluster paired bootstrap
```

即以 student 为重采样簇，保留其内部全部 evaluation rows。

interaction-level bootstrap 可作为历史兼容结果保留，但不能作为新的主要置信依据。

## 6. Final holdout

MODEL-1 final test 已经被观察，并直接促成 ADR-0008，因此不得继续承担下一轮最终无偏模型选择。

MODEL-2 开始时必须先从：

```text
全量 Junyi
- original Medium 10k
- MODEL-1 external holdout 5k
```

之外，按 label-independent 规则再冻结一个 `JUNYI_FINAL_HOLDOUT_V1`。

该成员集在 MODEL-2 只冻结，不用于诊断实验；只有未来某一路线通过 MODEL-2 后，才允许单独批准一次最终验证。

## 7. Stop policy

MODEL-2 是认知诊断研究线的强制停止 Gate。

若没有可靠 student-specific signal：

```text
STOP_ML_DIAGNOSIS_RULE_ONLY
```

则项目产品正式保留：

- `RULE_BETA_1_1_V1` Mastery；
- Published Knowledge Graph；
- `REC_RULE_V1` recommendation / learning path；
- 已归档的模型研究作为负实验与技术选型证据。

不得继续为了“必须有AI模型”无限增加模型。

如果仅有全局 student ability，而 Topic mastery 不成立，则允许进入简单 hierarchical / IRT 研究，但不再称为图认知诊断。

## 8. 业务架构不变

本 ADR 不修改：

- Course / KnowledgePoint / ExerciseUnit / Question；
- V0.4 RuleBasedMasteryProvider；
- Recommendation；
- Published Graph；
- Neo4j；
- Java API。

任何模型最终进入系统，仍必须经过 `MasteryProvider` Adapter 和独立生产 Gate。
