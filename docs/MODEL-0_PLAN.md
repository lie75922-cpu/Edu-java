# MODEL-0 实验计划

**阶段目标：** 在不改变 DATA-0 冻结学生成员和 test split 的前提下，验证认知诊断模型能否稳定运行、效果是否值得集成、资源成本是否可接受。

## 1. 输入基线

基线数据：`JUNYI_MID`（10,000学生，70/10/20 student-level split）。

不得重新抽学生，不得根据模型结果修改 test 成员。

MODEL-0开始前先生成派生版本：`JUNYI_MID_MODEL_V1`。

派生只允许：

- exact duplicate去重；
- unknown-exercise interaction从需要Q-matrix的模型输入中过滤；
- Exercise→Topic映射生成；
- candidate-specific graph projection；
- 模型格式转换。

不得覆盖原DATA-0数据和Manifest。

## 2. Preflight必须输出

- Medium exact duplicate count；
- Medium unknown-exercise count；
- Medium mapped/unmapped Exercise count；
- 过滤前后 interaction count；
- train/valid/test各自student/interactions/correct-rate；
- observed Exercise→Topic coverage；
- 40 Topic Q-matrix统计；
- Medium observed-exercise induced graph：nodes / edges / components / self-loops / cycles；
- 派生Manifest及转换规则。

## 3. 第一优先级：NCDM

定位：非图 baseline。

统一输入：

- student；
- ExerciseUnit；
- binary response；
- Exercise→Topic Q-matrix。

优先通过 InsCD 或明确可审计的独立实现运行。

必须记录：

- AUC；
- ACC；
- RMSE；
- DOA（框架支持且定义一致时）；
- train wall time；
- inference latency；
- peak RAM；
- peak GPU memory；
- checkpoint size；
- seed / hyperparameters。

调参只允许使用 train+valid，test只用于最终一次冻结评估。

## 4. 第二优先级：ORCDF

定位：图响应认知诊断主候选。

使用与NCDM相同的：

- Medium派生数据；
- student split；
- Exercise→Topic Q-matrix；
- 评价指标。

新增记录：

- response graph构造方式；
- graph nodes / edges；
- graph construction time；
- 相对NCDM的收益和额外成本。

如果ORCDF效果没有稳定提升，也如实保留结果，不允许通过更换test或删Bad Case制造优势。

## 5. RCD

定位：关系图baseline，但当前为条件路线。

DATA-0已确认原RCD numeric concept graph无法直接追溯到当前raw external IDs。

只有满足以下之一才进入同数据公平对比：

1. 重建可追溯 concept mapping；或
2. 在统一Topic层定义经过审核的concept graph。

否则RCD只做reference reproduction，不与NCDM/ORCDF的同数据结果混表宣称公平优劣。

## 6. GEAR-CD

定位：高复杂度候选，不是默认最终模型。

本阶段只做：

- 环境可安装性；
- checkpoint/code路径核对；
- input adapter审计；
- Tiny/Small smoke test；
- 显存和运行时间估计。

未经Owner/架构审查批准，不进行完整Medium全量训练。

## 7. 模型选择原则

最终模型不按单一AUC决定。

综合考虑：

1. AUC/ACC/RMSE；
2. 稳定性；
3. 训练成本；
4. 在线推理成本；
5. 模型输出是否能投影到平台KnowledgePoint；
6. 工程部署复杂度；
7. 可解释性；
8. 许可证与代码可维护性。

## 8. 推荐输出表

| Model | AUC | ACC | RMSE | Train Time | Inference | Peak RAM | Peak GPU | Size | Integration Risk |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| NCDM | | | | | | | | | |
| ORCDF | | | | | | | | | |
| RCD | | | | | | | | | |
| GEAR-CD | | | | | | | | | |

## 9. MODEL-0 Gate

只允许：

- `GO_MODEL_INTEGRATION`
- `CONDITIONAL_GO`
- `NO_GO`

MODEL-0结束以后停止，不自动修改Java AI Gateway生产接口；先由ChatGPT/Owner审查模型选择。
