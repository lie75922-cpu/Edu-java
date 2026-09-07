# DATA Pipeline

该目录属于 **Research Data Domain**，用于 Junyi 等公开教育数据的离线审计、清洗、采样和模型输入生成。

## 原则

- 原始数据放在 `data/` 或 `.data/` 下，并由 `.gitignore` 排除；
- 模型代码只依赖 Canonical Schema，不直接依赖原始列名；
- DATA-0 冻结前不声称最终学生数、Exercise数、知识节点数或交互数；
- Junyi 数据仅用于非商业研究/学习/求职 Demo，并保留来源、许可与 SHA256；
- 镜像、作者预处理数据与原始数据必须区分来源级别，不能统一写成“Junyi官方数据”。

## 当前阶段

V0.1 已有 Canonical Schema 与 Junyi Adapter 边界。本分支额外保留一个 **Research Exercise Catalog PoC**，用于验证真实 Exercise 元数据能否被 Java/Vue 稳定读取；它不是 DATA-0 完成标志，也不等同于平台 `Question`、`KnowledgePoint` 或在线用户数据。

## Junyi Research Exercise Catalog

`scripts/export_junyi_catalog.py` 将经审计的 Exercise 元数据 CSV 投影为只读研究目录。Schema v2 强制保存来源分类和原文件 SHA256，并同时保存：

- `prerequisiteRaw`：源 CSV 单元格原值；
- `prerequisites`：仅用于目录查询/显示的逗号分隔可逆视图。

在 DATA-0 完成 `prerequisite` 语义审计前，`prerequisites` **不得被当作最终知识图边**。

示例：

```bash
python -B scripts/export_junyi_catalog.py \
  --input data/interim/junyi_metadata/junyi_Exercise_table.csv \
  --output data/processed/junyi_catalog_v2.json \
  --source-type THIRD_PARTY_PROCESSED \
  --source-label "Junyi metadata mirror under provenance review" \
  --source-uri "<exact URL/repository file URL when verified>" \
  --acquired-at "2026-09-07T00:00:00Z"
```

如果不能证明当前文件来自原始发布方，`--source-type` 必须使用 `THIRD_PARTY_PROCESSED` 或其它真实分类，禁止为了方便写成 `PRIMARY`。

输出契约：

```json
{
  "schemaVersion": 2,
  "provenance": {
    "sourceType": "THIRD_PARTY_PROCESSED",
    "sourceLabel": "Junyi metadata mirror under provenance review",
    "sourceUri": null,
    "sourceFileName": "junyi_Exercise_table.csv",
    "sourceSha256": "<64 hex chars>",
    "acquiredAt": null,
    "transformation": "Research catalog projection only; ..."
  },
  "items": [
    {
      "recordNumber": 1,
      "externalId": "exercise name",
      "displayName": "pretty_display_name",
      "topic": "raw topic text",
      "area": "raw area text",
      "live": true,
      "prerequisiteRaw": "raw,source,value",
      "prerequisites": ["raw", "source", "value"],
      "duplicateExternalId": false
    }
  ]
}
```

导出器会：

1. 校验CSV表头和行宽；
2. 计算源文件SHA256；
3. 保留重复external ID而不擅自合并；
4. 保留原始prerequisite字符串；
5. 原子写入输出文件；
6. 失败时不覆盖既有成功产物。

真正的 DATA-0 仍需按照 `docs/DATA-0_IMPLEMENTATION.md` 和后续交接任务书完成行为日志、图关系、采样、模型准备度和最终 Gate。
