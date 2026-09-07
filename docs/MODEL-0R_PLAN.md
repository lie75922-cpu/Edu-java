# MODEL-0R：认知诊断重评实验计划

**阶段性质：** 第一轮 MODEL-0 评测协议审查后的受控重评。  
**前置 ADR：** `ADR-0004-cognitive-diagnosis-evaluation-protocol.md`。  
**目标：** 分别回答“已有行为历史时能否有效诊断”和“新学生少量校准后能否快速诊断”，而不是把两类问题混成一个指标。

---

## 0. 第一轮 MODEL-0 结果处理

Codex 在开始 MODEL-0R 前必须先把已经完成、当前仍只存在本地 worktree 的 MODEL-0A 产物原样提交和推送到：

```text
codex/model0-junyi-20260907
```

不得为了符合本计划重新计算或改写第一轮结果。

第一轮必须以如下口径归档：

```text
Experiment: MODEL-0A / COLD_ZERO_HISTORY
Gate: NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION
```

它是有效的 negative experiment，但不是模型路线终止证据。

必须保留：

- Preflight；
- NCDM原结果；
- ORCDF原结果；
- RCD NOT_COMPARABLE；
- GEAR-CD smoke；
- configs / seeds；
- runtime/resource logs；
- bad cases；
- adapter说明；
- tests；
- final gate。

原始数据和大checkpoint继续忽略，不上传Git。

---

# 1. 数据基线

不得重新选择 Medium 学生。

固定：

```text
Medium members = DATA-0冻结的10,000学生
Exercise→Topic semantics = DATA-0 / ADR-0002冻结的40 Topic初始概念层
exact duplicate policy = 与 MODEL-0A preflight 一致
unknown Exercise policy = 与 MODEL-0A preflight 一致
```

已有 DATA-0 student-level split 不删除、不覆盖。

MODEL-0R 新增两个独立 Manifest：

```text
JUNYI_MID_TRANS_V1
JUNYI_MID_COLD_V1
```

---

# 2. Protocol A：Warm / Transductive Diagnosis

## 2.1 目的

模拟平台中的常见状态：

> 学生已经在系统里做过一部分练习，系统根据这些历史行为形成学生表示，并预测/解释其后续表现。

这才是NCDM、ORCDF等传统CDM的主要公平使用场景。

## 2.2 Split

对每个 Medium 学生：

1. 按 `occurred_at` 升序；
2. 同时间戳时使用冻结的稳定原始顺序作第二排序键；
3. 前60% → train；
4. 接下来20% → validation；
5. 最后20% → test。

采用确定性 floor/remaining 规则，必须保证至少存在1条test记录；对于短序列必须在报告中给出具体分配规则和数量，不能静默删除学生。

禁止随机打散学生时间序列。

## 2.3 模型

必须：

1. Majority / global-rate baseline；
2. Exercise-rate baseline；
3. Topic-rate baseline；
4. NCDM；
5. ORCDF。

条件：

- RCD：只有共同可审计Concept Graph成立才加入公平主表；
- GEAR-CD：继续保持Small/smoke，未经批准不完整训练。

## 2.4 结果

统一输出：

```text
Model
AUC
ACC
RMSE
LogLoss(optional)
DOA(if valid)
Train Time
Test Inference Time
Peak RAM
Peak GPU
Checkpoint Size
```

另输出：

- mastery score分布；
- student-level mastery variance；
- Topic-level mastery coverage；
- 随机抽样至少20名学生的 mastery vector sanity check（匿名ID）；
- 是否存在明显掌握度塌缩/oversmoothing。

## 2.5 模型选择前提

至少满足：

- AUC显著高于0.5和非个性化baseline；
- ACC不能只靠高正确率类别获得；
- mastery不是所有学生趋于同一向量；
- 输出能稳定映射到40 Topic；
- 资源成本可接受。

---

# 3. Protocol B：New-Student Cold Start

## 3.1 数据

继续使用 DATA-0 原始 student-level split：

```text
train students = 7,000
valid students = 1,000
test students = 2,000
```

学生成员绝对不能修改。

## 3.2 校准定义

对每个valid/test学生的时间序列，固定报告：

```text
k = 0, 3, 5, 10
```

含义：

- 前k条 = calibration；
- k之后的记录 = evaluation；
- calibration记录不能再进入evaluation；
- 对不足以提供k+1条记录的学生，不替换、不删除其冻结身份，只在该k实验中标记`INELIGIBLE_FOR_K`；
- 每个k必须报告 eligible students、coverage%、evaluation interactions。

## 3.3 基线

必须：

- zero-history global rate；
- Exercise historical rate；
- Topic historical rate。

## 3.4 NCDM / ORCDF校准

若对NCDM/ORCDF做cold-start：

- 训练完成后冻结Exercise、Concept、interaction network等全局参数；
- 为新学生创建新的学生表示；
- 只能使用其calibration记录优化/估计该学生表示；
- evaluation记录不可参与适配；
- 报告平均/中位/P95每学生适配时间；
- 明确使用多少gradient steps、learning rate和early stopping规则。

如果现有兼容适配器无法保证只更新新学生表示，应标记：

`NOT_SUPPORTED_BY_CURRENT_ADAPTER`

不能再用零向量代替校准并把结果称为正式NCDM/ORCDF冷启动结果。

## 3.5 ICDM

将 ICDM 作为冷启动优先候选。

参考：

```text
ECNU-ILOG/ICDM
Inductive Cognitive Diagnosis for Fast Student Learning in Web-Based Online Intelligent Education Systems, WWW 2024
```

执行前必须：

- 检查官方仓库/实现许可证；
- 记录代码来源和commit/release；
- 确认输入Q-matrix语义与当前40 Topic一致；
- 不能为了跑通而使用另一个数据集的ID映射。

若无法建立兼容输入，保留真实失败原因。

## 3.6 输出

按k分别报告：

```text
Model
k
Eligible Students
Coverage
Evaluation Interactions
AUC
ACC
RMSE
Calibration Mean/P50/P95
Inference Latency
Peak RAM/GPU
```

重点回答：

> 学生需要做多少题，系统才能开始输出比简单统计baseline更有价值的个性化Mastery？

---

# 4. 第一轮已报告指标的处理

Codex已经返回的第一轮数字只放入 `MODEL-0A / COLD_ZERO_HISTORY` 表：

```text
NCDM: AUC 0.756775 / ACC 0.820678 / RMSE 0.361951
ORCDF: AUC 0.638144 / ACC 0.813219 / RMSE 0.472493
```

在对应原始报告未提交Git前，主分支文档不得把这些数值升级成正式冻结结果。

即使原报告审查通过，也只能注明：

> zero-history unseen-student compatibility result; not standard diagnostic model selection.

不能写：

> NCDM最终优于ORCDF。

---

# 5. 公平性和泄漏控制

所有实验必须满足：

1. DATA-0 Medium学生集合不变；
2. 测试记录绝不参与调参；
3. chronological protocol中未来记录不进入历史特征；
4. cold-start calibration与evaluation严格隔离；
5. baseline只能用其允许的训练/校准数据统计；
6. 所有seed和config版本化；
7. Bad Case不删除；
8. 不因结果差更换Topic/Q-matrix定义。

---

# 6. Gate

MODEL-0R最后只允许：

### `GO_MODEL_INTEGRATION`

至少一个Warm模型产生明确个性化诊断信号且工程成本可接受，并且cold-start有可执行的规则/校准或归纳模型策略。

### `CONDITIONAL_GO`

Warm诊断可用，但cold-start只能先用统计/规则fallback；或模型效果可用但资源/实现仍需治理。

### `NO_GO`

在正确协议下模型仍不能稳定超过非个性化baseline，或Mastery没有合理个体区分度。

完成后停止，不自动修改Java生产AI Gateway。

---

# 7. 必须交付的Git文件

```text
model-service/experiments/model0r/
├── README.md
├── manifests/
│   ├── junyi_mid_trans_v1.json
│   └── junyi_mid_cold_v1.json
├── configs/
├── reports/
│   ├── warm_preflight.md
│   ├── warm_results.md
│   ├── cold_preflight.md
│   ├── cold_results.md
│   ├── mastery_sanity.md
│   ├── bad_cases.md
│   ├── cost.md
│   └── final_gate.md
└── tests/
```

大型派生数据和checkpoints继续Git ignore。
