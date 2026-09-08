# ADR-0010：MODEL-2 后的简单层级能力建模路线

- 状态：Accepted
- 决策日期：2026-09-08
- 适用阶段：MODEL-3

## 1. 背景

MODEL-2 已完成个体化信号终止 Gate，并得到 `GO_SIMPLE_HIERARCHICAL_ROUTE`：

1. 固定 MODEL-1 C40 NCDM checkpoint 后，仅替换 student embedding，ORIGINAL 相对 POP_MEAN、ZERO、PERMUTED 均保留稳定 AUC 优势，说明学生个体表示确实承载可利用信息；
2. 但在 `OBSERVED` 且 Topic train/history exposure >= 3 的未来行为上，C40 mastery AUC 低于 V0.4 RuleBeta，不能证明 40 维 NCDM mastery 对平台掌握度有增量价值；
3. 简单 Rasch / IRT-1PL：`logit P(correct)=student_ability-exercise_difficulty` 在同一诊断数据上显著优于 ExerciseRate；
4. `JUNYI_FINAL_HOLDOUT_V1` 已在 MODEL-2 任何诊断指标产生前冻结，5000 名学生与 Medium10k、MODEL-1 5k 均零重叠，且标签/指标仍未读取。

因此，研究问题已从“选择更复杂认知诊断模型”转为：

> 在一个已证实存在的全局学生能力信号之上，加入**受收缩的学生-Topic偏差**，是否能在保持可解释性的同时，获得超出 Rasch 与 RuleBeta 的稳定增量？

本阶段不再训练 RCD、ORCDF、GEAR-CD、NCDM 或其它图认知诊断模型。

## 2. 学术定位

Rasch / IRT 可解释为学生潜在能力与题目难度的差异模型；多层/层级 IRT 进一步允许在教育测量的层级或内容结构中引入随机效应。MODEL-3 采用这一成熟统计思想，不把模型命名包装为新的学术算法。

本项目只验证一个工程/研究上必要的最小扩展：

```text
global student ability
+ exercise difficulty
+ shrinked student-topic deviation
```

它是**受控简单模型比较**，而不是创新点先行。

## 3. 模型定义

### 3.1 B0 — ExerciseRate

非个性化基线：只使用开发数据历史 Exercise 正确率，未见 Exercise 使用预注册 fallback。

### 3.2 B1 — Rasch / IRT-1PL

```text
logit P(y_ui = 1) = μ + θ_u - β_i
```

- `θ_u`：student ability；
- `β_i`：exercise difficulty；
- `μ`：全局截距，可固定/吸收到参数中心化中；
- 不含隐藏层、图传播、Topic mastery vector。

### 3.3 B2 — Hierarchical Rasch + Topic Deviation

```text
logit P(y_ui = 1) = μ + θ_u - β_i + δ_{u,t(i)}
```

其中：

- `t(i)` 为 Exercise 的 frozen Topic；
- `δ_{u,t}` 是 student 对该 Topic 的偏差；
- `δ` 必须使用零均值收缩/正则先验，防止低样本 Topic 被过拟合；
- 无 Topic 历史时 `δ=0` / UNKNOWN，不允许从随机初始化产生伪个性化；
- 不能把 `δ` 直接解释为百分制 mastery；它首先是 logit 空间的局部能力偏差。

该模型在实现中可采用 MAP / L2 regularized logistic mixed-effect 等价形式，但公式、参数语义和收缩方式必须固定并审计。

## 4. 为什么不直接把 B2 当产品 mastery

MODEL-2 已证明“预测概率”和“可解释 mastery”不是同一件事。

因此：

- `θ_u` = 全局能力 latent score；
- `δ_{u,t}` = Topic deviation latent score；
- 若未来需要映射到平台 `KnowledgePoint mastery`，必须另做 calibration / monotonic mapping；
- 在 MODEL-3 Gate 前，V0.4 `RULE_BETA_1_1_V1` 继续是唯一生产 MasteryProvider；
- 推荐、Learning Path、教师端均不切换到新模型。

## 5. 数据边界

### 5.1 Development cohort

MODEL-1 的 5000 名 external holdout 已经被用于 MODEL-1 / MODEL-2 诊断，因此从 MODEL-3 起将其明确降级为**开发/方法选择 cohort**。

允许使用：

- MODEL-1 train/history；
- MODEL-1 validation；
- 已被观察过的 MODEL-1 diagnostic test，只作为开发证据，不再称为无偏最终测试。

不得再次用它作为“最终泛化性能”。

### 5.2 Final holdout

`JUNYI_FINAL_HOLDOUT_V1` 是唯一新的最终验证 cohort。

在 MODEL-3 设计、模型形式、正则/超参数、fallback、指标、Gate 全部冻结之前：

- 不读取其 `correct`；
- 不物化最终标签表；
- 不产生任何性能指标。

最终验证只允许执行一次。

## 6. 开发阶段选择原则

只比较 B0/B1/B2，不扩大搜索空间。

### 6.1 B2 正则

允许一个小而预注册的 Topic-deviation shrinkage 候选集，例如：

```text
lambda_delta ∈ {0.03, 0.10, 0.30, 1.00}
```

具体值可由 MODEL-3 config 冻结；选择只看 development validation。

Rasch 的学习率、epoch、中心化方式优先复用 MODEL-2 已验证配置；若必须调整，只能依据 development train/validation，并完整记录。

禁止：

- 根据 final holdout 改 lambda；
- 搜大量神经网络结构；
- 引入 Graph / Attention / Transformer；
- 根据单个学生/Exercise ID 硬编码。

## 7. Final holdout 协议

MODEL-3 最终验证模拟平台“已有一段学生历史后再预测后续表现”的场景。

在 global 参数由 development cohort 冻结后，对 final holdout 每名学生：

1. 按时间排序 eligible interactions；
2. 前 60% 为 calibration/history；
3. 后 40% 为 final evaluation；
4. global item / model parameters保持冻结；
5. 只允许使用 calibration/history 估计该学生 `θ_u` 与 `δ_{u,t}`；
6. evaluation 响应不得反向更新参数；
7. 对 calibration 未见 Topic，`δ=0/UNKNOWN`；
8. 对 development 未充分估计的 Exercise 必须使用预注册 fallback，并单独报告 coverage / 指标，不静默删除。

最终 test 只读取一次。

## 8. 指标

### 8.1 Response prediction

- AUC
- ACC
- RMSE
- Log Loss
- Brier
- calibration bins / ECE（若实现定义固定）

### 8.2 个体化增量

主要比较：

```text
B1 Rasch - B0 ExerciseRate
B2 Hierarchical - B1 Rasch
```

使用 student-cluster paired bootstrap，至少 1000 resamples（资源不足最低 500并说明）。

### 8.3 Topic deviation 可解释性

至少：

- observed Topic coverage；
- `δ` 分布；
- exposure >= 3 / 5 分层；
- future-response association；
- 与 RuleBeta 的相关性与互补性；
- 20 个匿名学生案例；
- UNKNOWN 不作为数值 mastery。

## 9. Final Gate

只允许：

### `GO_HIERARCHICAL_MODEL_INTEGRATION`

必须同时满足：

- B2 final AUC > B1；
- B2-B1 student-cluster bootstrap 95% CI lower bound > 0；
- B2 Log Loss / Brier 不出现明显恶化；
- observed Topic deviation 在 exposure>=3 上有稳定 future-value；
- calibration 和 bad-case 检查无系统性异常。

该 Gate 只授权后续单独设计 `ModelMasteryProvider` / scope validation，不自动接入。

### `GO_RASCH_ONLY_INTEGRATION`

- B1 Rasch 在 final holdout 稳定优于 ExerciseRate；
- B2 没有稳定增量或 Topic deviation 不具备可解释价值。

后续只允许集成全局 ability / response-risk 辅助信号；RuleBeta 仍作为 Topic mastery。

### `STOP_ML_INTEGRATION_RULE_ONLY`

- Rasch 在真正 final holdout 上也不能稳定优于 ExerciseRate，或存在严重校准/覆盖问题。

则停止模型生产集成，平台最终以 RuleBeta + Published KG + REC_RULE_V1 为准。

## 10. 生产边界

MODEL-3 不修改：

- Java `MasteryProvider`；
- `student_knowledge_mastery`；
- Recommendation；
- Learning Path；
- Published Graph；
- Teacher Analytics。

任何模型接入必须另开 Gate。

## 11. 参考研究方向

本路线以经典 Rasch / IRT 与 multilevel item-response / mixed-effects 思路为理论背景。参考方向包括：

- Adams, Wilson & Wu (1997), Multilevel Item Response Models；
- Doran, Bates, Bliese & Dowling (2007), Estimating the Multilevel Rasch Model；
- Sulis & Toland (2017), Introduction to Multilevel Item Response Theory Analysis。

这些文献用于说明层级 IRT / 随机效应建模的成熟基础；本项目不声称简单的 `θ - β + δ_topic` 形式本身构成新的理论算法。
