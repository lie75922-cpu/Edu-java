# R：推荐/路径离线评测

## Gate 与结论

- Gate：`EVAL_INCONCLUSIVE`
- 结论：`INCONCLUSIVE`
- Rasch：`DEFERRED`

本评测的 M0/M1 是有效的离线 proxy 比较，但不是学习增益或线上产品证据。M2/M3 均为 `INCONCLUSIVE_GRAPH_SEMANTICS`：Foundation 图是未发布、待审核的 Topic candidate，且 Foundation 已报告 cyclic SCC；没有将其称为 Published Graph、没有删边或换图。因此本轮不能对“图是否带来增益”作有效结论。

## 冻结方法

- Research Student 仅按 `user_id % 257 == 17` 选择；选择与 outcome 无关。
- 仅保留 Foundation `ELIGIBLE_FOR_IMPORT` Exercise 到 Topic 的映射；每位学生按 `time_done` 升序、原始 ProblemLog 行号打破时间相同的并列。
- 至少 20 条映射交互；前 3/5 为 history，剩余为 future。推荐时未读取该学生 future。
- M0 是截至该 student cutoff 的其他 Research Student Topic frequency；M1 是该 student history 的 Beta(1,1) posterior Topic error rate。二者相同候选集和 `@3`。
- `future_topic_hit`、`future_error_topic_hit`、coverage 和 popularity-adjusted recall 都是离线 proxy。future error 仅用作评测目标，未用于推荐；相关性不是由 candidate graph 标注。

## 实际样本

| 项目 | 数值 |
| --- | ---: |
| 原始 ProblemLog 扫描行 | 25925992 |
| 固定 bucket 映射交互 | 99874 |
| 可评 Research Student case | 394 |
| Topic 候选集 | 39 |
| bad case 记录 | 25 |

## M0/M1 指标

| 方法 | future Topic hit@3 | future error Topic hit@3 | coverage@3 | popularity-adjusted future recall@3 |
| --- | ---: | ---: | ---: | ---: |
| M0 Popularity | 0.690355 | 0.617080 | 0.256410 | 0.376121 |
| M1 Mastery-only | 0.269036 | 0.234160 | 0.923077 | 0.088521 |

完整逐 case 输出在 `method_by_case.csv`，指标在 `metrics.csv`，反例在 `bad_cases.jsonl`。这些文件只用重编号 case，不包含原始 user_id、原始交互或 Platform business records。

## 限制和后续边界

- `INCONCLUSIVE_GRAPH_SEMANTICS` 不等同于“图无价值”；它表示 Foundation candidate 目前不具备可用于图方法的已发布语义。
- `NO_PROVEN_INCREMENT` 未被声明，因为组合方法没有合法图输入可供检验；总体结论必须是 `INCONCLUSIVE`。
- 本轮未做 Rasch，也未做任何平台推荐接入、AnswerRecord 写入或 Question 映射。
