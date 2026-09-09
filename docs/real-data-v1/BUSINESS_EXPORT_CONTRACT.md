# F：Business Export 契约

## 生成入口与范围

从 `data-pipeline` 运行：

```powershell
$env:PYTHONPATH = 'D:\Code\java\Edu-java-foundation-real-data-v1\data-pipeline\src'
C:\Users\26675\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m edu_data.business_export `
  --metadata D:\Code\java\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv `
  --output D:\Code\java\Edu-java-foundation-real-data-v1\data-pipeline\business_export
```

输出格式版本为 `1.0.0`。生成器只读取 Exercise metadata；不会读取 ProblemLog，不会生成 Platform User、AnswerRecord 或 Question。输出是本机生成的业务桥接 artifact，不是可直接加入源代码提交的原始数据替代物。

生成顺序固定：Area 按 raw Area、Topic 按 raw Topic、Exercise 按 source metadata 行号、raw prerequisite evidence 按行号和 token 位置、Topic candidate 按 prerequisite Topic 和 dependent Topic。没有生成时间字段，重复运行同一输入和输出路径会覆盖本契约列出的同名 artifact 并得到相同内容结构。

## 文件与记录数

| 文件 | 记录数 | 契约 |
| --- | ---: | --- |
| `manifest.json` | 1 | 格式版本、输入路径/大小/编码/列、输出记录数和边界声明。 |
| `areas.jsonl` | 8 | `area_external_id`、`raw_area`、中文 display、mapping status、provenance。 |
| `topics.jsonl` | 40 | `topic_external_id`、唯一 `area_external_id` 父项、raw Topic、中文 display、mapping status、provenance。 |
| `exercises.jsonl` | 837 | 每个 metadata 行；完整 `raw_source_fields`、row number、raw Area/Topic、中文 display 状态、业务映射状态及 `NOT_PRESENT_METADATA_ONLY` Question 状态。 |
| `exercise_topic_mapping.jsonl` | 837 | 每个 metadata 行的 Exercise→Topic 投影及 fail-closed 状态；不是无条件可导入关系。 |
| `prerequisite_raw_evidence.jsonl` | 988 | 原始 prerequisite token、原始 cell、两端 Exercise ID、source row、identity 解析状态；关系类型固定为 `PREREQUISITE_RAW_UNVERIFIED`。 |
| `prerequisite_topic_candidates.jsonl` | 78 | 由 raw evidence 推导的 Topic pair、evidence IDs、policy version；固定为审核候选，绝非 Published Graph。 |
| `display_mapping.jsonl` | 885 | Area、Topic、Exercise 的 raw 值与中文 display 并列保存。 |
| `quality_report.json` | 1 | 实际计数、解析状态、原始图与 candidate Topic graph 统计，以及 F Gate。 |

`manifest.json`、`quality_report.json` 使用 UTF-8 JSON；其余文件使用 UTF-8 JSONL，每行一个对象。JSON key 按字典序写出；消费者不得依赖对象 key 的显示顺序。

## 身份、raw 与 display

* `junyi-area:{raw_area}` 和 `junyi-topic:{raw_topic}` 是稳定的 V1 external identity。原始值始终另存为 `raw_area` 或 `raw_topic`。
* Exercise identity 是源 `name`；`source_metadata_row_number` 是 metadata 行的审计定位，不是对原始 Exercise ID 的替代。
* Exercise 的 11 个源列完整位于 `raw_source_fields`；`display_name_zh` 只承载展示，不覆盖 raw 值。
* `display_mapping_status` 只允许 `REVIEWED`、`UNREVIEWED`、`AI_DRAFT`、`QUARANTINED`。本次 Area/Topic 使用 `REVIEWED`，可导入 Exercise 使用源提供的中文名并标为 `UNREVIEWED`，quarantine Exercise 标为 `QUARANTINED`；没有 `AI_DRAFT` 记录。

业务映射状态的有效值为：

| 值 | 后续导入行为 |
| --- | --- |
| `ELIGIBLE_FOR_IMPORT` | 可进入业务导入的候选集，仍需由 ImportRun 记录实际 create/reuse/update/conflict。 |
| `QUARANTINED_DUPLICATE_EXTERNAL_ID` | 不创建业务 Exercise identity。 |
| `QUARANTINED_MISSING_AREA_OR_TOPIC` | 不创建业务关系，保留 raw metadata。 |
| `QUARANTINED_NON_MATH_AREA` | 不纳入数学平台课程目录，保留 raw metadata 和中文 display。 |

## prerequisite 治理契约

`prerequisite_raw_evidence.jsonl` 和 `prerequisite_topic_candidates.jsonl` 必须分开消费。

1. raw evidence 可进入 Evidence 审计域，永远保持 `verified: false`，不可直接成为 Published Graph relation。
2. Topic candidate 只作为 review/draft 输入，必须保留 `derivation_policy_version: junyi-topic-projection-v1`、evidence IDs、`REVIEW_REQUIRED_NOT_PUBLISHED` 与 `NOT_PUBLISHED`。
3. candidate graph 的任何 self-loop 或 cyclic SCC 都禁止发布。F 的实际 candidate graph 有 4 个 cyclic SCC；本契约没有自动断边、反向或删除策略。
4. `COLLAPSED_SAME_TOPIC`、`UNRESOLVED_*`、`QUARANTINED_*` 是保留在 raw evidence 的投影结果，不能被消费者当作已审核关系。

## Fail-closed 条件

生成器在以下情况抛出 `DataConsistencyError`，不得产生一个伪完整的 export：metadata 缺失或为空、列模式变更、Exercise ID 为空、出现未映射的非空 Area/Topic、或任一 Topic 有多个 Area 父项。因缺分类、重复 identity 或跨学科导致的单行问题不会被隐去，而是以 quarantine status 写入输出。
