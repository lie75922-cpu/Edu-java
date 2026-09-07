# ADR-0004：认知诊断评测必须区分常规诊断与新学生冷启动

**Status:** Accepted  
**Date:** 2026-09-07  
**Context:** DATA-0 / MODEL-0 architect review

## 1. 背景

DATA-0 为防止学生身份泄漏，冻结了一个 10,000 学生的 student-level 70/10/20 划分。第一轮 MODEL-0 直接把这一划分用于 NCDM / ORCDF，并对 validation/test 中训练阶段从未见过的学生使用中性零历史表示。

这一协议没有泄漏，但它改变了原本要回答的问题：

- 平台主要需要根据**已经产生若干答题历史的学生**估计知识掌握度；
- student-level 完全隔离 + 零校准历史测试的是**未见学生、零历史冷启动预测**；
- NCDM、ORCDF 等经典/图认知诊断方法使用学生专属表示，本质上主要面向 transductive（已知学生、有历史响应）场景；
- 对新学生的快速诊断是另一类 inductive cognitive diagnosis 问题。

WWW 2024 的 ICDM 工作专门指出，多数已有 CDM 使用 transductive student-specific embeddings，面对训练阶段未见学生时需要重新适配/训练，因此将新学生诊断单独定义为 inductive cognitive diagnosis。

参考：
- NCDM: https://ojs.aaai.org/index.php/AAAI/article/view/6080
- ORCDF: https://arxiv.org/abs/2407.17476
- ICDM: https://arxiv.org/abs/2404.11290

因此，第一轮 MODEL-0 的零历史结果必须保留，但不能被解释为“认知诊断路线失败”或“NCDM/ORCDF 在正常使用场景下的公平优劣”。

## 2. 决策

后续认知诊断实验固定拆为两条互相独立的协议。

### Protocol A — Warm/Transductive Diagnosis

目标：评估学生已经具有历史行为时，模型能否预测其后续答题并形成有区分度的知识掌握表示。

数据原则：

- 仍使用 DATA-0 冻结的同一批 10,000 名 Medium 学生，不重新挑学生；
- 从去重后的 Medium 交互派生独立版本 `JUNYI_MID_TRANS_V1`；
- 每个学生内部严格按时间排序；
- 使用按学生序列的 chronological 60/20/20：前 60% train、接着 20% valid、最后 20% test；
- 同一学生可以出现在三个 interaction split 中，因为学生历史正是该任务的输入，但任何未来交互不得进入早期预测特征；
- split manifest 必须独立版本化，不能覆盖 DATA-0 student-level split。

主要模型：

1. NCDM baseline；
2. ORCDF graph/response candidate；
3. RCD 仅在共同可审计 concept graph 成立时加入公平比较；
4. GEAR-CD 仍保持 smoke/条件路线，未经批准不进行高成本完整训练。

### Protocol B — New-Student Cold Start

目标：评估平台注册新学生在只有少量校准答题时，系统多快能够形成可用掌握画像。

数据原则：

- 保留 DATA-0 原 student-level 7,000 / 1,000 / 2,000 学生划分；
- train 学生用于学习全局 Exercise/Concept/interaction 参数；
- valid/test 学生训练阶段完全不可见；
- 对未见学生仅允许使用其时间序列开头的校准记录；校准记录与评测记录严格分开。

固定报告 `k = 0, 3, 5, 10`：

- `k=0`：零历史基线，只用于说明纯冷启动难度，不用于选择传统 CDM；
- `k=3/5/10`：以前 k 条真实响应用于校准，预测其后续记录；
- 每个 k 必须报告 eligible student 数、覆盖率和评测 interaction 数，不能为了凑 k 删除或替换冻结 test 学生；
- NCDM/ORCDF 若参与 cold-start，只允许冻结全局参数并适配新学生表示，必须报告每学生校准时间；
- 若实现不可安全支持这一过程，应标记 `NOT_SUPPORTED`，不得用零向量替代后宣称模型失败；
- ICDM 作为专门面向 unseen-student 的候选模型进入 cold-start 比较。

## 3. 必须增加的简单基线

所有协议必须同时报告：

- majority / always-correct baseline；
- global correct-rate baseline；
- Exercise historical-rate baseline（仅使用允许的训练数据统计）；
- Topic historical-rate baseline（仅使用允许的训练数据统计）。

原因：DATA-0 中整体正确率较高，仅看 ACC 容易把类别不平衡误当成个性化能力。

只有模型显著超过非个性化 baseline，才有资格声称产生了有效的个体诊断信号。

## 4. 指标解释

### Response prediction

- AUC
- ACC
- RMSE
- log loss（实现方便时）

### Diagnosis quality

- DOA：只有定义、输入和学生历史满足方法要求时报告；否则 N/A；
- mastery dispersion / student differentiation；
- 相同或近似响应历史下的表示稳定性（可选但推荐）。

### Engineering

- train wall time
- inference latency
- calibration latency（cold-start）
- peak process RAM
- peak GPU memory
- checkpoint size
- graph construction time

## 5. 第一轮 MODEL-0 的解释

第一轮 student-level + zero-history 结果统一命名为：

`MODEL-0A / COLD_ZERO_HISTORY`

它回答的是：

> 在不给未见学生任何校准历史、并使用中性学生表示的条件下，兼容适配器能否仅依靠全局 Exercise/Concept 参数预测答题？

它不能回答：

- NCDM 正常认知诊断效果；
- ORCDF 正常图认知诊断效果；
- 学生 mastery 是否准确；
- ORCDF 是否在公平的标准场景下弱于 NCDM。

因此其 `NO_GO` 解释为：

`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`

不是：

`NO_GO_FOR_COGNITIVE_DIAGNOSIS`。

## 6. 对产品架构的影响

V0.2 已经通过 Outbox 为 mastery 更新留出边界，因此模型尚未选定不会阻塞网站开发。

后续平台应明确支持两种状态：

1. **COLD / INSUFFICIENT_EVIDENCE**：新学生历史不足，使用规则/统计先验并提示低置信度；
2. **DIAGNOSED**：积累足够响应后使用正式认知诊断模型更新 MasterySnapshot。

这比强迫一个传统模型在零历史新学生上输出“个性化掌握度”更符合真实业务。

## 7. Consequences

正面：

- 避免把数据防泄漏规则误当成所有模型的评测协议；
- 区分“已有学习历史的诊断”与“新学生冷启动”；
- 保留第一轮失败实验的研究价值；
- 为平台上线后的新用户场景提供明确降级策略。

代价：

- 需要新增一个 chronological transductive split；
- cold-start 需要校准实验或 ICDM 等归纳模型；
- MODEL-0 不能在第一轮 `NO_GO` 后直接结束模型研究。
