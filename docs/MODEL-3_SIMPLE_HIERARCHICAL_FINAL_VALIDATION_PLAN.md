# MODEL-3 Simple Hierarchical Ability — Final Validation Plan

## 1. Objective

MODEL-2 已确认：

- C40 student embedding 含真实个体信号；
- C40 Topic mastery 不优于 RuleBeta，不能进入最终验证；
- Rasch/IRT-1PL 在开发诊断数据上稳定优于 ExerciseRate；
- `JUNYI_FINAL_HOLDOUT_V1` 已冻结且从未产生标签指标。

MODEL-3 的唯一问题是：

> 简单的全局学生能力模型是否能在真正未使用的 final holdout 上复现；以及加入受收缩 student-topic deviation 后是否有额外、可解释的增量？

本阶段不做图认知诊断，不训练 NCDM/RCD/ORCDF/GEAR-CD。

---

## 2. Work packages

### WP-M3.0 — Integrity audit

先确认：

- MODEL-2 reports/configs 已归档且只读；
- `JUNYI_FINAL_HOLDOUT_V1` manifest status = `FROZEN_UNEVALUATED`；
- 5000 student membership 与 Medium10k、MODEL-1 5k overlap 都为0；
- 本阶段开始前没有 final holdout label/metric artifact；
- development Exercise/Topic identity与 final eligible scope一致性。

任何不一致 => STOP，不打开 final labels。

### WP-M3.1 — Development-only model freeze

使用 MODEL-1 cohort 作为 development data。

预注册候选：

1. `EXERCISE_RATE_DEV`
2. `RASCH_1PL_V1`
3. `HIER_RASCH_TOPIC_V1`

`HIER_RASCH_TOPIC_V1`：

```text
logit P(correct) = μ + θ_student - β_exercise + δ_student_topic
```

约束：

- `δ_student_topic` 使用明确 L2/MAP shrinkage；
- no-history Topic deviation = 0 / UNKNOWN；
- 无隐藏层；
- 无知识图传播；
- 无身份硬编码。

模型选择只使用 development train/validation。

允许的 `lambda_delta` 候选必须在 config 中预先冻结；建议不超过4个。

选择依据：validation AUC优先，其次Log Loss/Brier；不得看 final holdout。

### WP-M3.2 — Development refit

模型形式和超参数冻结后，可使用整个历史 development cohort 对**global parameters**做一次最终 refit：

- global intercept；
- Exercise difficulty；
- 其它全局固定参数。

这个refit允许使用已被历史研究观察过的 MODEL-1 cohort，因为它从 MODEL-3 起明确属于 development domain。

不得使用 `JUNYI_FINAL_HOLDOUT_V1`。

产出：

- frozen config；
- global parameter coverage；
- unseen Exercise fallback policy；
- final-evaluation ledger初始状态。

### WP-M3.3 — One-time final holdout materialization

仅在 WP-M3.1/M3.2 完全冻结后才允许读取 final holdout labels。

对每个 frozen final student：

```text
chronological interactions
first 60% -> calibration/history
last 40%  -> final evaluation
```

规则：

- membership不能替换；
- calibration/evaluation不能重叠；
- global model parameters frozen；
- 只能用calibration拟合该学生的 `θ` / `δ`；
- evaluation不得在线更新；
- no-history Topic deviation = 0/UNKNOWN；
- 未见Exercise不能删除，使用预注册fallback并单独报告。

Final evaluation ledger必须拒绝第二次运行。

### WP-M3.4 — Final metrics

对 B0/B1/B2 同时报告：

- AUC
- ACC
- RMSE
- Log Loss
- Brier
- calibration table
- known-item / fallback-item coverage
- per-student calibration latency
- total fit/inference time
- peak RAM/GPU

Primary paired comparisons：

- `RASCH_1PL_V1 - EXERCISE_RATE_DEV`
- `HIER_RASCH_TOPIC_V1 - RASCH_1PL_V1`

使用 student-cluster paired bootstrap：

- target 1000 resamples；
- 最低500，若降低必须写明资源原因；
- 95% CI。

### WP-M3.5 — Topic-deviation audit

仅对 B2：

- exposure>=1/3/5 coverage；
- `δ` P05/P50/P95/std；
- future outcome association；
- 与 RuleBeta correlation；
- 在 Rasch residual 上是否仍有增量；
- 20个匿名学生案例；
- UNKNOWN不填0.5或随机值。

如果 B2 只提升response AUC但 Topic deviation无法解释，则不能进入 `GO_HIERARCHICAL_MODEL_INTEGRATION`。

---

## 3. Required artifact structure

```text
model-service/experiments/model3/
├── README.md
├── configs/
│   ├── development.json
│   ├── final_validation.json
│   └── final_gate.json
├── manifests/
│   └── junyi_final_holdout_v1_consumption.json
├── reports/
│   ├── integrity_audit.md
│   ├── development_model_selection.md
│   ├── development_refit.md
│   ├── final_holdout_protocol.md
│   ├── final_results.md
│   ├── topic_deviation_audit.md
│   ├── statistical_report.md
│   ├── calibration.md
│   ├── bad_cases.md
│   ├── cost.md
│   └── final_gate.md
├── scripts/
└── tests/
```

Large rows/checkpoints/runtime ledgers ignored from Git.

---

## 4. Tests

At minimum：

- final holdout membership overlap guard；
- no label read before config freeze；
- chronological split deterministic；
- calibration/evaluation no overlap；
- global parameters frozen during final student calibration；
- no-history topic deviation equals neutral 0 and status UNKNOWN；
- evaluation rows never update θ/δ；
- final-test ledger refuses second evaluation；
- cluster bootstrap preserves student cluster；
- fallback Exercise rows retained/reported；
- historical MODEL-0/0R/1/2 files unchanged。

Repository CI must remain green.

---

## 5. Gate

Only：

### GO_HIERARCHICAL_MODEL_INTEGRATION

B2 clearly and robustly improves on Rasch and has interpretable Topic-deviation future value.

### GO_RASCH_ONLY_INTEGRATION

Rasch robustly beats ExerciseRate on final holdout, but B2 adds no stable/meaningful Topic increment.

### STOP_ML_INTEGRATION_RULE_ONLY

Rasch does not replicate on final holdout or has unacceptable calibration/coverage failures.

After Gate STOP. Do not modify Java `MasteryProvider` or production recommendation automatically.
