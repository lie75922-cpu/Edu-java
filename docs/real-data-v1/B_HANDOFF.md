# B：后端真实目录与图谱治理交接

## 范围与结论

工作分支为 `codex/backend-real-data-v1`，从本地 Foundation 分支建立的隔离 worktree 是
`D:\Code\java\Edu-java-backend-real-data-v1`。本阶段只改动 `backend/**` 和本文件；没有修改
`frontend/**`、`data-pipeline/**`、`model-service/**`、`e2e/**`，也没有操作
`D:\Code\java\Edu-java-submit`。

当前 Gate：`CONDITIONAL_BACKEND`。

后端实现、导出契约 fixture、单元和 Web 层测试均通过；本机 Docker 不可用，因此 MySQL/Neo4j
Testcontainers 集成测试被跳过，未对真实 Foundation export 执行 Java 数据库 apply。不能把下面的
预期导入规模表述为已在数据库写入的实际 import result。

## Foundation 输入核对

使用已提交的 Foundation exporter 在 worktree 外的临时目录读取既有本地 metadata，获得并核对：

| artifact | 记录数 | 后端处理方式 |
| --- | ---: | --- |
| Area | 8 | `KnowledgeArea` + raw/display/provenance 审计记录 |
| Topic | 40 | `KnowledgePoint` 的 V1 可解释投影，不声称为最小教育学知识点 |
| Exercise metadata | 837 | 803 条 `ELIGIBLE_FOR_IMPORT` 可进入 `ExerciseUnit`；不创建 Question |
| quarantine metadata | 34 | 4 duplicate identity、18 缺分类、12 biology；只计入 ImportRun quarantine，不创建业务 Exercise |
| raw prerequisite evidence | 988 | 不可变 Evidence，保持未验证状态与完整 raw payload |
| Topic candidate | 78 | 显式 draft candidate 输入，保留 policy、candidate status、published status 与 evidence IDs |

真实 Topic candidate 图是 39 节点、78 边、0 self-loop、4 个 cyclic SCC。它的
`graphPublicationStatus` 必为 `BLOCKED_GRAPH_PUBLICATION`；实现没有断边、反向、删除或自动发布策略。

临时 export 的 raw artifacts 已在验证后删除，且不在提交中。

## 导入设计

没有创建第二套 catalog importer，而是扩展既有 `SeedImportService`、`SeedImportRun`、
`SeedImportConflict` 与 Seed Import API。

### raw/display/provenance 方案

选择 B：独立 `catalog_source_record` 审计映射表，而不是向每个业务实体重复增加 raw 与来源列。

| 方案 | 迁移影响 | 查询与前端 | 审计性 | 结论 |
| --- | --- | --- | --- | --- |
| 业务实体加 raw/display/provenance 字段 | 每张目录表都要迁移 | 目录查询变宽 | 重复且难覆盖 quarantine | 未采用 |
| 独立 source record | 一张专用表 | 业务目录继续读既有 display name | raw 值 insert-only，来源独立可审计 | 采用 |

业务实体的现有 name 字段保存 display value，供产品目录使用；`catalog_source_record` 保存 raw value、
中文 display、display mapping status、business mapping status、metadata 行号和 provenance JSON。相同
external identity 的 raw value 变化会产生 `RAW_VALUE_CONFLICT`，不会覆盖；raw identity 不变但 display
变化时只更新 display 与审计派生字段，不创建新 identity。

`SeedImportRun` 额外记录 export format version、输入路径、输入大小、编码、列清单、源记录数和
quarantine count。它使用这些显式字段做可审计边界，不使用内容摘要。

`FoundationBusinessExportReader` 只读取冻结的 `business_export` 文件结构并生成既有 API 的 request
objects；它不是 HTTP 任意路径读取接口。调用顺序是：

1. `POST /api/v1/admin/seed-imports/dry-run` 或 `/apply` 导入目录投影；
2. 向一个人工创建的 draft graph version 调用既有 raw evidence import；
3. 再显式调用 candidate relation import。

dry-run 只持久化 catalog ImportRun 与 conflict 审计，绝不写 Course、KnowledgeArea、KnowledgePoint、
ExerciseUnit、Exercise-Topic mapping 或 Published Graph。

## Evidence、candidate 与 Published Graph

`EvidenceImportService` 的 raw evidence 路径已改为只写入/复用 `KnowledgeRelationEvidence`：不再根据
已解析 endpoint 隐式创建 `KnowledgeRelation`。candidate 必须经新 API 输入，且每一条都要求：

* `candidate_status = REVIEW_REQUIRED_NOT_PUBLISHED`；
* `published_graph_status = NOT_PUBLISHED`；
* policy version、candidate ID、Topic external IDs 和已有 raw evidence IDs；
* raw evidence 已解析到完全相同的 Topic pair。

candidate relation 保存为 draft 的 `RelationReviewStatus.CANDIDATE` 和 `relation_source = DERIVED_POLICY`。
任何 candidate self-loop 或 cycle 都返回 `BLOCKED_GRAPH_PUBLICATION`，但仍保留 draft 供人工审查。没有
任何本阶段代码写 Published Graph 或 Neo4j。

## 迁移与 API

新增迁移：

* `V007__real_data_catalog_provenance_and_import_audit.sql`：catalog provenance mapping 与 SeedImportRun
  审计字段；
* `V008__candidate_input_policy_governance.sql`：candidate ID、policy、candidate status 与 published status。

新增或扩展的管理员接口：

* `GET /api/v1/admin/data-governance/overview`
* `GET /api/v1/admin/data-governance/import-runs`
* `POST /api/v1/admin/graph-versions/{graphVersionId}/candidate-relation-imports/dry-run`
* `POST /api/v1/admin/graph-versions/{graphVersionId}/candidate-relation-imports/apply`

前两个接口只允许 `SYSTEM_ADMIN`，读真实 `SeedImportRun`、conflict、Evidence、candidate 与 Published Graph
计数；没有固定 Foundation 数字。candidate 接口沿用图谱教学访问校验，仍需通过所属课程授权。

## 验证

执行：

```powershell
cd D:\Code\java\Edu-java-backend-real-data-v1\backend
D:\Code\java\tools\apache-maven-3.9.16\bin\mvn.cmd --batch-mode test
```

结果：88 tests passed，30 skipped，0 failures，0 errors。新增覆盖包括：

* Foundation business_export fixture contract reader；
* catalog dry-run 无业务写、first apply、display-only rerun、duplicate quarantine 与 research user/interaction 边界；
* raw Evidence 不会隐式生成 candidate；
* candidate cycle/self-loop `BLOCKED_GRAPH_PUBLICATION`；
* migration schema；
* data-governance 和 candidate import 的管理员权限。

测试 fixture 是合成记录，只覆盖 export schema 与导入语义；不含真实 Foundation raw source value。

30 个跳过的是现有 MySQL/Neo4j Testcontainers 集成测试；Docker daemon 在本机不可用，故没有伪报其通过。

## 未完成的运行时证明与下一步

* 未执行真实 Java database dry-run/apply/second apply，因此 **actual imported counts 尚无**。Foundation
  输入给出的预期目录写入上限是 8 Area、40 Topic、803 Exercise；34 条 quarantine、988 Evidence、78 candidate
  必须由有 MySQL 的环境实际生成 ImportRun 后再报告 create/reuse/update/conflict 计数。
* 当前 candidate 图有 4 个 cyclic SCC，因此即使 Evidence 与 candidate 已导入，也必须保持
  `BLOCKED_GRAPH_PUBLICATION`；不得发布或投影 Neo4j，直到人工审查改变 draft 后重新验证。
* Research Student、25M Research Interaction、Junyi Question content 仍未、且不得进入平台业务域。
