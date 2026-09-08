# MODEL-2 Personalization Signal Gate

## 1. 目的

MODEL-2 不再回答“哪个深度模型更强”。

它只回答三个问题：

1. MODEL-0R / MODEL-1 的 full-vector mastery collapse 有多少来自未观测 Concept 被当作约 0.5 的数值参与统计？
2. C40_TOPIC NCDM 在两个独立样本上稳定高于 Exercise-rate baseline 的预测增益，是否真的来自 student-specific representation？
3. 如果存在学生个体信号，最简单的可解释 student ability / Topic mastery 是否已经足够？

MODEL-2 是认知诊断研究线的强制停止 Gate。

---

## 2. 已冻结的历史证据

不得改写：

### MODEL-0A

`COLD_ZERO_HISTORY`

- NCDM AUC 0.756775；
- ORCDF AUC 0.638144；
- 仅解释为 zero-history unseen-student compatibility 负实验。

### MODEL-0R

原 Medium 10k，per-student chronological split：

- Exercise-rate baseline AUC 0.748487；
- C40 NCDM AUC 0.765630；
- bootstrap lower bound +0.014948；
- full-vector mastery FLAGGED。

### MODEL-1

fresh external holdout 5k，与 Medium 10k overlap=0：

- Exercise-rate baseline AUC 0.708358；
- C40 NCDM AUC 0.719500；
- lower bound +0.009695；
- fine Exercise-as-Concept NCDM AUC 0.708524；
- fine lower bound -0.001122；
- fine route `NO_GO_CONCEPT_LAYER`。

MODEL-2 不允许重写以上数字。

---

# 3. 工作顺序

必须严格执行：

```text
M2.0 source/checkpoint audit
  -> M2.1 freeze FINAL holdout membership
  -> M2.2 observability audit
  -> M2.3 student-identity ablation
  -> M2.4 simple personalization signal baselines
  -> M2.5 decision
  -> STOP
```

不要跳到新的 graph model。

---

# 4. M2.0：实验资产审计

优先复用已经完成的 MODEL-1 local artifacts：

- `JUNYI_EXTERNAL_HOLDOUT_V1` train/valid/test derivatives；
- selected C40 NCDM checkpoint；
- MODEL-1 manifests/configs/results。

如果 checkpoint/derived data 在当前真实环境不存在：

- 可以按照已冻结 seed/config 只在 MODEL-1 train/validation 上重建同等 C40 checkpoint；
- 不得重新选择 hyperparameter；
- 不得更新 MODEL-1 历史 test result；
- 报告必须标注 `DIAGNOSTIC_REPRODUCTION`，不能冒充原 MODEL-1 run。

先输出：

`model-service/experiments/model2/reports/asset_audit.md`

包括：

- 哪些原资产存在；
- 哪些需要重建；
- 使用的代码/config版本；
- 是否读取历史 final-test；
- 本轮每个数据集的角色是 diagnostic 还是 future final holdout。

---

# 5. M2.1：先冻结新的最终 holdout

在运行新的诊断模型或观察新的诊断结果之前，先冻结：

`JUNYI_FINAL_HOLDOUT_V1`

来源必须排除：

- original Medium 10k；
- MODEL-1 fresh external holdout 5k。

目标：

- 5,000 students；
- 每学生 >=20 eligible chronological interactions；
- 当前 624 Q-eligible Exercise scope；
- selection 不读取 `correct`；
- sequence-length stratified；
- seed `2026090802`；
- overlap with previous research cohorts = 0。

冻结阶段只允许使用：

- anonymous user ID；
- exercise；
- timestamp；
- eligible scope。

成员冻结之后生成：

`manifests/junyi_final_holdout_v1.json`

该 holdout 在 MODEL-2 **禁止用于任何指标、模型选择或调参**。

如果本阶段无法可靠冻结独立 holdout，MODEL-2 最终最多 `CONDITIONAL_GO`。

---

# 6. M2.2：Observability-aware Mastery Audit

使用 MODEL-1 C40 checkpoint 与其 train/history exposure。

## 6.1 exposure matrix

构建：

```text
exposure_count[student, Topic]
correct_count[student, Topic]
```

来源只能是 train/history。

定义：

- OBSERVED_1: exposure >=1；
- OBSERVED_3: exposure >=3；
- OBSERVED_5: exposure >=5；
- UNKNOWN: exposure=0。

必须报告：

- 每学生 observed Topic 数量分布；
- 每 Topic observed student 数量分布；
- student-topic pair coverage；
- k=1/3/5 coverage。

## 6.2 full vs observed-only

保留历史 full-vector：

- median concept std；
- pairwise cosine / MND。

新增 observed-only：

### Concept-side

对于每个 Topic，只在满足 exposure>=k 的学生上计算：

- mean mastery；
- std；
- P05/P50/P95；
- sample size。

只对 sample size >=50 的 Topic 进入 aggregate statistics。

### Student-side

对每个学生，只在其 exposure>=k 的 Topic 上计算：

- observed topic count；
- within-student mastery std；
- range；
- IQR。

### Pairwise difference

不要把 UNKNOWN=0.5 参与学生向量 cosine。

至少提供：

- centered observed MND-RMSE；
- pairwise common-observed Topic 数量；
- 仅在 common observed topics >=3 的 student pairs 上统计差异。

完整矩阵 ORCDF-style MND 可以保留兼容，但必须与 observed-only 结果分表展示。

---

# 7. M2.2B：Mastery 对未来表现是否有意义

对 validation/test 中每条 response，使用 train/history 中固定的 mastery，不允许在线更新。

按照其对应 Topic 的历史 exposure 分层：

- >=1；
- >=3；
- >=5。

比较以下 score：

### NCDM C40 mastery

对应 Topic 的 `sigmoid(student_embedding)`。

### Rule Beta mastery

与 V0.4 同语义：

```text
(correct_count + 1) / (attempt_count + 2)
```

只使用 train/history。

### Student global Beta

```text
(total_correct + 1) / (total_attempt + 2)
```

### Topic population rate

train/history Topic rate。

必须评估：

- future response AUC；
- Log Loss / Brier；
- calibration bins；
- within-Topic rank agreement；
- student-cluster bootstrap confidence interval。

注意：mastery score 不包含 Exercise difficulty，因此这一表用于判断 student/Topic signal，不与完整 response predictor 混为一谈。

另报告：

- NCDM mastery 与 Rule Beta 的 Spearman correlation；
- 两者在未来同 Topic accuracy 上的 calibration 差异。

---

# 8. M2.3：Student Identity Ablation

对**同一个已训练 C40 NCDM checkpoint**做 inference-only ablation。

禁止重新训练 item/difficulty/discrimination/MLP。

比较：

## A. ORIGINAL

真实训练后的 student embedding。

## B. POP_MEAN

所有学生使用 train students 的 population-mean student embedding。

## C. ZERO

所有学生使用 zero logits，即 sigmoid=0.5。

## D. PERMUTED

固定 seed `2026090803`，将 student embedding row 在学生间确定性 permutation；每个学生收到另一名学生的 embedding。

其余 Exercise difficulty/discrimination/MLP/Q 完全不变。

报告：

- AUC / ACC / RMSE / Log Loss；
- ORIGINAL - POP_MEAN AUC delta；
- ORIGINAL - ZERO delta；
- ORIGINAL - PERMUTED delta；
- student-cluster paired bootstrap 95% CI。

主要判断：

如果 ORIGINAL 相比 POP_MEAN / PERMUTED 没有稳定优势，则现有 NCDM prediction gain 不能归因于 student-specific diagnosis。

---

# 9. M2.4：简单可解释个体化信号 Baseline

本阶段不是追求最优模型，而是寻找“学生个体信号是否存在”的最小证据。

## Baseline 0：ExerciseRate

MODEL-1已有统计基线。

## Baseline 1：StudentGlobalBeta

train/history 的 smoothed student global correctness。

## Baseline 2：Rasch / IRT-1PL

只建模：

```text
logit P(correct) = student_ability - exercise_difficulty
```

约束：

- 仅 train/history 拟合；
- validation 只用于固定正则/停止；
- 本轮是 diagnostic，不允许访问 `JUNYI_FINAL_HOLDOUT_V1` labels；
- 输出 student ability 分布与 item difficulty 分布；
- CPU即可。

## Baseline 3：TopicBeta

使用 V0.4 Beta mastery，根据每条 future response 的 Topic 取 student-topic score。

## 可选组合（仅作 signal decomposition）

可以增加一个无复杂网络的固定形式：

```text
logit P = b0
        + b1 * logit(ExerciseRate)
        + b2 * logit(StudentGlobalBeta)
```

如果执行，系数只能用 train/validation 确定，不得增加大量 feature engineering。

---

# 10. 统计方法

主要置信区间统一使用：

`student-cluster paired bootstrap`

流程：

1. 以 student 为抽样单位；
2. 每次有放回重采样学生；
3. 保留抽中学生全部 evaluation rows；
4. 计算 paired metric delta；
5. 至少 500 resamples，建议 1000；
6. seed 固定并记录。

interaction-level bootstrap 只能作为历史兼容附表。

---

# 11. Gate

MODEL-2 只允许三个结果。

## `GO_C40_FINAL_VALIDATION`

必须同时满足：

1. C40 ORIGINAL 相对 POP_MEAN 或 PERMUTED 的 response AUC student-cluster bootstrap lower bound >0；
2. 在 exposure>=3 的 observed Topic 上，C40 mastery 对 future outcome 有稳定 student-specific signal；
3. observed-only mastery 不表现为大规模常数/数值塌缩；
4. C40 mastery 相比 RuleBeta 至少存在经验证的增量或互补价值，而不是只重复相同信息；
5. `JUNYI_FINAL_HOLDOUT_V1` 已冻结且未使用。

该 Gate只允许设计一次 final external validation，不允许直接生产集成。

## `GO_SIMPLE_HIERARCHICAL_ROUTE`

适用：

- Rasch / StudentGlobal 等明确证明 student-specific signal；
- 但 C40 mastery 不满足上面的解释/增量要求。

此时停止 NCDM/RCD/ORCDF/GEAR-CD 路线，下一步只允许设计简单 hierarchical ability + Topic deviation 模型。

不得再称为“图认知诊断主模型”。

## `STOP_ML_DIAGNOSIS_RULE_ONLY`

适用：

- Student-identity ablation 无稳定差异；且
- 简单 Rasch/StudentGlobal 也不能稳定超过 Exercise-only 信息；或
- observed mastery 对未来行为没有可靠解释力。

此时认知诊断研究线正式停止。

产品继续采用：

- `RULE_BETA_1_1_V1`；
- `REC_RULE_V1`；
- Published Knowledge Graph learning path。

---

# 12. 输出目录

```text
model-service/experiments/model2/
├── README.md
├── configs/
├── manifests/
│   └── junyi_final_holdout_v1.json
├── reports/
│   ├── asset_audit.md
│   ├── final_holdout_freeze.md
│   ├── observability.md
│   ├── observed_mastery_future_signal.md
│   ├── student_identity_ablation.md
│   ├── simple_signal_baselines.md
│   ├── statistical_report.md
│   ├── bad_cases.md
│   ├── cost.md
│   └── final_gate.md
├── scripts/
└── tests/
```

Large data/checkpoints/runtime remain ignored。

---

# 13. 禁止事项

- 不修改 MODEL-0A / MODEL-0R / MODEL-1 历史文件；
- 不使用 `JUNYI_FINAL_HOLDOUT_V1` 做本阶段诊断；
- 不训练 RCD / ORCDF / GEAR-CD；
- 不修改 Java MasteryProvider；
- 不修改 V0.4 recommendation；
- 不修改 Published Graph；
- 不因为结果不好更换学生/Exercise scope；
- 不把无历史 embedding≈0.5 写成真实 mastery；
- 不为了简历强行制造一个“AI生产模型”。

---

# 14. Definition of Done

- [ ] Existing MODEL-1 assets audited
- [ ] New final holdout frozen before diagnostics
- [ ] Previous cohorts overlap = 0
- [ ] Exposure matrix k=1/3/5 statistics complete
- [ ] Full vs observed-only mastery comparison complete
- [ ] NCDM vs RuleBeta future-signal comparison complete
- [ ] ORIGINAL / POP_MEAN / ZERO / PERMUTED ablation complete
- [ ] Student-cluster bootstrap complete
- [ ] StudentGlobalBeta complete
- [ ] Rasch/IRT-1PL complete
- [ ] Bad cases retained
- [ ] Cost/runtime recorded
- [ ] Tests and repository CI green
- [ ] One of three MODEL-2 Gate decisions returned
- [ ] STOP after Gate
