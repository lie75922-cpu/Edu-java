# F：真实数据领域决策

## Gate

`GO_FOUNDATION_V1`

该 Gate 只冻结可解释的业务投影及其治理边界。它**不**批准任何 Topic 候选关系发布为知识图谱，也不启动后续阶段。

## 已核实输入

| 输入 | 本地路径 | 大小 | 本阶段处理 |
| --- | --- | ---: | --- |
| Junyi Exercise metadata | `D:\Code\java\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv` | 139,957 bytes | 完整读取 837 行 |
| Junyi ProblemLog | `D:\Code\java\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv` | 2,668,566,436 bytes | 未重新扫描 |

metadata 编码是带可选 BOM 的 UTF-8，列严格为 `name`、`live`、`prerequisites`、`h_position`、`v_position`、`creation_date`、`seconds_per_fast_problem`、`pretty_display_name`、`short_display_name`、`topic`、`area`。ProblemLog 的既有 DATA-0 审计已记录 25,925,992 条 Research Interaction 和 247,606 名匿名 Research Student；F 没有读取它，也没有导出其中任何学生或交互。

## 实际领域结果

| 项目 | 实际值 |
| --- | ---: |
| metadata 行 | 837 |
| 不同 Exercise external ID | 835 |
| 重复 Exercise external ID | 2 个 ID、4 行 |
| 非空 Area | 8 |
| 非空 Topic | 40 |
| 原始 `N/A` Area/Topic 行 | 20 |
| biology 跨学科行 | 12 |
| 可作为业务导入候选的唯一 Exercise | 803 |

Area 的实际 raw 列表为 `algebra`、`analytic-geometry`、`arithmetic`、`biology`、`calculus`、`geometry`、`logics`、`probability-statistics`。40 个 Topic 及其唯一 Area 父项、以及 837 个 Exercise 与其 raw Topic/Area，会由本提交的 export 命令写入本机 `data-pipeline/business_export/` 下的 `topics.jsonl`、`exercises.jsonl` 和 `exercise_topic_mapping.jsonl`；该生成目录不纳入源代码提交。

本 export 的隔离结果为：803 行 `ELIGIBLE_FOR_IMPORT`、4 行 `QUARANTINED_DUPLICATE_EXTERNAL_ID`、18 行 `QUARANTINED_MISSING_AREA_OR_TOPIC`、12 行 `QUARANTINED_NON_MATH_AREA`。20 个原始 `N/A` 行与 18 个缺分类 quarantine 行并不矛盾：其中 2 行同时是重复 external ID，duplicate quarantine 的优先级更高。

## 冻结的 V1 投影

| 原始概念 | V1 业务投影 | 冻结限制 |
| --- | --- | --- |
| Area | `KnowledgeArea` 候选 | Area 仍不是 Topic；biology 保留为 raw 数据但隔离。 |
| Topic | `KnowledgePoint` 候选 | 这是可解释的 V1 业务分类，不宣称 Topic 等于教育学最小知识点。 |
| Exercise metadata | `ExerciseUnit` 候选 | 仅 803 个可导入身份；metadata 不是题干。 |
| Question | 无投影 | 只能来自平台真实且有权使用的题目。 |

Area 与 Topic 的 8+40 条中文显示映射是 F 维护的 V1 curated mapping，导出状态为 `REVIEWED`；这表示已经按此版本校对展示用语，不等同于课程专家对知识颗粒度的批准。Exercise 的 raw English ID 和每个源字段都保留；830 行带源提供的中文 `pretty_display_name`，7 行缺失中文展示，均为 `UNREVIEWED`，不得由导入端臆造题名或中文名。

## prerequisite 投影

原始文本 token、唯一可解析 Exercise 边和 Topic 候选关系是三个不同层次：

| 阶段 | 实际值 |
| --- | ---: |
| 所有原始 prerequisite token（含重复 ID 行） | 988 |
| 可唯一解析的 Exercise→Exercise 原始边行 | 980 |
| 无法唯一解析的 Exercise 边行 | 8 |
| 可解析原始图的不同边 | 979 |
| 可解析原始图 self-loop | 2 |
| 可解析原始图 cyclic SCC | 3 |
| 同 Topic 折叠 | 823 |
| 可映射的跨 Topic 输入边 | 156 |
| 重复 Topic pair 折叠 | 78 |
| Topic 投影未解析 | 9 |
| 不同 Topic candidate pair | 78 |
| candidate Topic graph 节点 / 边 | 39 / 78 |
| candidate Topic graph self-loop | 0 |
| candidate Topic graph cyclic SCC | 4 |

`prerequisite_raw_evidence.jsonl` 保留全部 988 条 raw 文本证据及解析状态。`prerequisite_topic_candidates.jsonl` 只包含 78 条经去重、去同 Topic 的派生候选，全部标记 `REVIEW_REQUIRED_NOT_PUBLISHED` 和 `NOT_PUBLISHED`。因此 candidate graph 的 4 个 cyclic SCC 是后续人工审核和发布校验的阻塞条件，而不是被静默改写或自动发布的图。

## 为什么是 GO 而不是 NO_GO

已经确认的事实支持 Area→Topic→Exercise 的受限业务目录：每个非空 Topic 恰好有一个 Area 父项，8 个 Area 和 40 个 Topic 都有中文展示映射，不能安全导入的 34 行均可按确定规则隔离。循环只出现在**派生且未发布**的 Topic candidate graph；F 的产物没有把它冒充成 Published Graph。因而它不否定目录投影，但严格禁止后续阶段自动发布候选边。

仍需后续治理的风险是：第三方处理数据来源、2 个重复 Exercise identity、20 条缺分类 metadata、12 条跨学科 metadata、7 条缺源中文 Exercise 展示，以及 4 个 Topic candidate cyclic SCC。任何一项都不得通过伪造 ID、补造 Question 或删除 raw 值来“修复”。
