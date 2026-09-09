# R 阶段交接：推荐/路径离线评测

## 范围与边界

分支：`codex/recommendation-eval-v1`

本阶段只完成 Research Data 上的离线推荐 proxy 评测。它没有创建 Platform User、AnswerRecord、Question 或 Published Graph，也没有改动 `Edu-java-submit`。Rasch 依 R1 规则保持 `DEFERRED`。

Foundation export 由 Foundation 分支中已提交的 exporter 在工作树外临时目录重新生成；本次评测只从中读取 Foundation 冻结的 `ELIGIBLE_FOR_IMPORT` Exercise→Topic 映射与 review-only candidate graph，再清理该临时 export。没有提交 raw metadata、raw interaction 或任何 raw-producing artifact。

## 冻结输入与方法

* ProblemLog：`D:\Code\java\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv`，本次流式扫描 25,925,992 条 Research Interaction。
* Foundation 目录投影：803 个可导入 Exercise、39 个数学 Topic。图候选仍是 78 条未发布 relation、39 节点、78 边、0 self-loop、4 个 cyclic SCC；它不是 Published Graph。
* 固定 cohort：只取数值 `user_id % 257 == 17`。membership 只由 `user_id`、可映射 Exercise 和 `time_done` 决定，不读取 `correct` 决定成员。
* 顺序：每个 Research Student 按 `time_done` 升序，原始 ProblemLog 行号作为相同时间的确定性 tie-breaker；至少 20 条映射交互，前 3/5 是 history，后 2/5 是 future。
* M0：截至各 case cutoff 的其他 Research Student Topic frequency；不借用目标 student 自己的 history。
* M1：目标 student history 中每个 Topic 的 Beta(1,1) posterior error rate（RuleBeta-style weakness）。它不是 Rasch theta，也不是平台 KnowledgePoint mastery。
* M2、M3：`INCONCLUSIVE_GRAPH_SEMANTICS`。没有使用、发布、断开或替换 Foundation candidate graph，因而不会把图本身或同一图关系当 relevance gold。

future Topic hit、future error Topic hit、coverage 和 popularity-adjusted future recall 都是 offline proxy；future correctness 只用于评测目标，未进入任何推荐分数，不能称为 learning improvement。

## 实际结果

| 项目 | 实际值 |
| --- | ---: |
| 固定 bucket 中可映射交互 | 99,874 |
| 固定 bucket Research Student | 961 |
| 满足长度并进入评测的脱敏 case | 394 |
| future error 可评 case | 363 |
| method-by-case 记录 | 3,152 |
| 脱敏 bad case 记录 | 25 |

| 方法 | 状态 | future Topic hit@3 | future error Topic hit@3 | coverage@3 | popularity-adjusted future recall@3 |
| --- | --- | ---: | ---: | ---: | ---: |
| M0 Popularity | `EVALUATED` | 0.690355 | 0.617080 | 0.256410 | 0.376121 |
| M1 Mastery-only | `EVALUATED` | 0.269036 | 0.234160 | 0.923077 | 0.088521 |
| M2 Graph-only | `INCONCLUSIVE_GRAPH_SEMANTICS` | N/A | N/A | N/A | N/A |
| M3 Mastery + Graph | `INCONCLUSIVE_GRAPH_SEMANTICS` | N/A | N/A | N/A | N/A |

在这一个冻结 cohort 和这些 proxy 上，M0 的点估计高于 M1；这只是已确认的离线比较事实，不是“频率推荐在学习效果上更好”的结论。M1 的更高 coverage 同样不是用户收益证据。

## Gate

* R Gate：`EVAL_INCONCLUSIVE`
* 研究结论：`INCONCLUSIVE`

M0/M1 的比较可复现且按 history/future 隔离，但“图是否有增益”与“组合是否有额外增益”不能在 Foundation 的未发布循环 candidate graph 上诚实回答。因此不能升级为 `COMBINED_ADDS_VALUE`、`GRAPH_ADDS_VALUE`、`MASTERY_ADDS_VALUE` 或 `NO_PROVEN_INCREMENT`。

## 可交接文件与验证

输出均位于 `model-service/experiments/recommendation_eval_v1/`：

* `summary.json`
* `method_by_case.csv`
* `metrics.csv`
* `bad_cases.jsonl`
* `report.md`

验证完成：专用单元测试 3 项通过；实际 Foundation export schema 解析通过；实际全量 ProblemLog 运行完成；required-output、case/row cardinality、M2/M3 无 recommendation、future-leakage flag 和脱敏边界一致性检查通过。

后续若要评价图方法，前提是独立治理流程产生有明确语义、经过审核且可合法使用的图版本；不得从本 R 结果反推或自动发布 Foundation candidate graph。
