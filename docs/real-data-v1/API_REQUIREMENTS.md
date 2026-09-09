# F→B：真实数据后端输入要求

本文件是 Foundation 对后端阶段的输入约束，不实施后端改动。

## 导入边界

后端只可从本 export 导入 `ELIGIBLE_FOR_IMPORT` 的 Area、Topic、Exercise 与 Exercise→Topic mapping。必须保留 raw 值、中文 display、display mapping status、provenance、dataset format version 和 source metadata 行定位。

不得导入或伪造：

* Research Student 为 `sys_user` 或其他 Platform User；
* Research Interaction 为 `AnswerRecord`；
* Junyi Exercise metadata 为 Question、题干、选项、答案或解析；
* biology quarantine 项为数学课程目录；
* duplicate 或缺分类 Exercise 的新业务 ID。

导入端必须区分 `Area != Topic != Exercise != Question`。`Topic -> KnowledgePoint` 仅实现为 V1 可解释业务投影，不得改写为“真实最小知识点”的声明。

## ImportRun 与更新行为

ImportRun 至少记录：export 格式版本、输入路径/大小/编码/列、导入时间、dry-run/apply、created/reused/updated/conflict 数、quarantine 数和失败原因。相同 external identity 再次导入时，中文 display 的变化只能更新 display，不得创建重复 identity；raw identity 冲突必须产生 conflict 记录而非覆盖。

dry-run 可以记录审计 run 和 conflict，但不得写入 Course、KnowledgeArea、KnowledgePoint、ExerciseUnit 或 Published Graph。apply 必须可安全重跑。

## prerequisite / 图谱治理

后端要分别接收：

| F 输出 | 可进入的位置 | 禁止行为 |
| --- | --- | --- |
| `prerequisite_raw_evidence.jsonl` | 原始 Evidence 审计记录 | 不得直接发布、不得改名为已审核知识关系。 |
| `prerequisite_topic_candidates.jsonl` | candidate/draft review 输入 | 不得自动视为人工批准或 Neo4j Published Graph。 |

候选 Topic graph 的实际规模为 39 节点、78 边、4 个 cyclic SCC、0 个 self-loop。后端必须在任何发布动作前重新校验 self-loop 与 cycle；当前 F 数据的候选关系不能自动发布。原始证据仍可导入并治理，不能因为发布被阻断而丢弃。

## 最小治理 API

后端阶段至少需要提供：

* `GET /api/v1/admin/data-governance/overview`
* `GET /api/v1/admin/data-governance/import-runs`

这些接口必须来自真实 ImportRun / conflict / evidence 状态，而不是 F 的固定数字。普通学生与教师接口只展示可用的中文业务目录；管理员审计界面可展示 raw English、external ID 和状态，但同时需要中文解释。
