# Codex 执行任务：真实数学数据 → Java 业务系统 → Published Graph → 个性化学习闭环

## 0. 执行角色与硬约束

你是本仓库下一阶段的执行工程师。不要重新设计一个新项目，也不要推翻已经通过验证的 V0.7/V1 工程底座。

仓库：`lie75922-cpu/Edu-java`

起始分支：优先从 PR #34 的最新 head `chatgpt/final-status-sync-20260908` 开始；执行前先 `git fetch` 并核对最新 SHA 和 CI，不允许从旧 main 直接重新做一套。

本地仓库根目录不要假定唯一固定路径。优先检查：

```text
D:\Code\Edu-java
D:\Code\java
```

以实际包含 `.git`、`backend/`、`frontend/`、`data-pipeline/` 的目录作为 `REPO_ROOT`。如果两者都不是，自动在 `D:\Code` 下查找仓库，不要复制出第三套项目。

最新产品基线必须先阅读：

- `docs/V1_MATH_PLATFORM_BASELINE.md`
- `docs/V1_SELF_IMPLEMENTATION.md`
- `docs/DATA0_ARCHITECT_REVIEW.md`
- `data-pipeline/reports/data_sources.md`
- `data-pipeline/reports/exercise_eda.md`
- `data-pipeline/reports/interaction_eda.md`
- `data-pipeline/reports/data0_graph.md`
- `data-pipeline/reports/domain_mapping_options.md`
- MODEL-2 / MODEL-3 最终 Gate 与 ADR。

### 禁止事项

1. 不得把平台重新锁死成“离散数学平台”。真实数据是什么学科就保留什么学科；当前 Junyi 主体是数学。
2. 不得把英文原始数据直接改写覆盖；必须保留 raw label，并建立中文 display mapping。
3. 不得为了“课程数量好看”人工编造课程。
4. `Area != Topic != Exercise != Question`，不得偷换语义。
5. 不得把 Junyi Exercise 元数据伪造成具体 Question 的题干、选项、答案或解析。
6. 不得把科研匿名 student ID 自动映射成平台业务 User。
7. 原始 prerequisite 只能作为 Evidence，禁止直接写入 Published Graph/Neo4j 当成真值。
8. 不得为了通过测试对某学生、某 ID、某 fixture 写专用分支。
9. 不得把 Rasch 当前研究结果写成线上已使用，也不得把 Rasch θ 叫“知识点掌握度”。
10. 不得提交原始 Junyi CSV/zip、凭据、`.env`、日志、依赖目录、模型大文件。
11. 任何数量、性能、模型效果必须来自本次真实运行日志/报告，不得估算后写成事实。
12. 不允许只改文档或只做假页面后宣称完成；每个业务能力必须有可执行代码、测试和验收证据。

---

## 1. 本地真实输入

不要假定原始数据一定只有一套路径。按以下顺序检查并记录最终实际使用路径：

```text
%REPO_ROOT%\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv
%REPO_ROOT%\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv
D:\Code\junyi_Exercise_table.csv
D:\Code\junyi_ProblemLog_original.csv
```

如果历史机器上存在其它已登记路径，也只能在确认文件来源、大小、schema 与 DATA-0 provenance 一致后使用。

执行前必须：

- 检查文件是否真实存在；
- 输出大小、列名、编码、10 条脱敏样例；
- 与 DATA-0 报告记录比对；
- 如果路径已变化，自动定位仓库父目录/已登记数据目录，不要重新从网络下载一份不明来源数据；
- 如果真实输入不存在，停止“业务导入”步骤并明确 `BLOCKED_MISSING_LOCAL_DATA`，但必须继续完成所有不依赖原始数据的代码、测试、文档工作。

已知审计基线仅用于一致性核查：

- students = 247,606
- interactions = 25,925,992
- metadata rows = 837
- distinct exercise IDs = 835
- Topics = 40
- Areas = 8
- raw prerequisite rows = 980

如果本次读取结果与上述不一致，禁止强行改数据凑数；必须报告差异并解释原因。

---

## 2. 第一任务：做一个正式、可重复的数据导出层

不要让 Java 后端直接读取 2.6GB 原始 CSV。

在 `data-pipeline` 内新增确定性的业务导出 pipeline，至少产出：

```text
business_export/
  dataset_manifest.json
  areas.csv/jsonl
  topics.csv/jsonl
  exercises.csv/jsonl
  exercise_topic_mapping.csv/jsonl
  prerequisite_evidence.csv/jsonl
  zh_display_mapping.csv/jsonl
  quality_report.json
```

### 每个实体必须保留

- source dataset/version
- source type
- original external id
- raw English label
- Chinese display label（若已审核）
- mapping status
- source/provenance field
- deterministic stable business key

### 中文映射

建立版本化、可审计映射，例如：

```text
raw_name,display_name_zh,mapping_status,mapping_source,review_note
algebra,代数,REVIEWED,...,...
geometry,几何,REVIEWED,...,...
```

要求：

- 不覆盖 raw_name；
- 未可靠翻译的值标记 `UNREVIEWED`，不能悄悄猜；
- biology/跨学科 Area 进入 quarantine/review 清单，不默认进数学课程；
- 导出结果顺序稳定，同一数据重复执行 byte-level ordering 稳定（不要求 hash，但内容和记录顺序必须可复现）。

### 测试

为 data-pipeline 增加：

- schema test
- duplicate external-id handling test
- missing Area/Topic test
- deterministic export test
- cross-discipline quarantine test
- mapping raw label preservation test

---

## 3. 第二任务：Java 端正式业务导入

实现一个**幂等、可审计、支持 dry-run** 的 import 机制。形式可选 CLI/ApplicationRunner/Admin API，但必须清晰隔离普通线上请求。

推荐业务映射第一版：

```text
Math / 合理课程容器
  KnowledgeArea <- Junyi Area
    KnowledgePoint <- Junyi Topic
      ExerciseUnit <- Junyi Exercise
        Question <- 仅平台自有/有权使用的具体题目
```

不要把 8 Area 简单粗暴变成 8 门课程；先根据真实数据和已有领域模型说明你采用“一门数学课程+多个领域”还是其它组织方式及原因，并写 ADR。

### 导入要求

- sourceType + externalId 构成可审计外部身份；
- 支持 dry-run：输出 create/update/skip/conflict 数量；
- 重跑不重复插入；
- 支持部分失败报告；
- ImportRun 有唯一运行 ID、输入版本、状态、起止时间、计数和错误摘要；
- 业务表不要保存 250k 科研匿名学生为 User；
- 不导入 Question 假数据；没有 Question 的 ExerciseUnit 在学生界面必须显示“暂无可用练习”。

### 必须新增测试

- first import
- second identical import is idempotent
- changed Chinese display mapping updates display only，不改 raw identity
- duplicate external id enters conflict path
- missing Topic/Area behavior deterministic
- rollback/failed import不会留下半套错误关系

---

## 4. 第三任务：真实 prerequisite 进入图谱治理链

已有工程链：

```text
Evidence -> Draft Relation -> GraphValidator -> READY -> Publish -> Neo4j projection
```

必须复用，不能另写一个“CSV -> Neo4j”捷径。

### 真实关系导入步骤

1. raw prerequisite 导出为 Evidence；
2. Resolve Exercise endpoint；
3. Resolve 到业务 KnowledgePoint 候选；
4. 无法唯一解析的进入 conflict；
5. 同点自环进入 reject；
6. 构建 draft graph version；
7. 跑 cycle / endpoint / duplicate / cross-course validator；
8. 只有通过并经明确 review 的版本才能 publish；
9. publish 后再投影到 Neo4j；
10. 输出 before/after 关系数量和拒绝原因统计。

严禁为了得到大图把全部 raw edge 自动设为 READY。

---

## 5. 第四任务：让大规模知识图谱真正可用

当前 16 节点 fixture 只能证明链路，不足以证明产品能力。真实数据导入后，学生图谱页面必须支持大图使用，而不是一次性把 100+ 节点全画满。

至少实现：

- Area / Topic 筛选；
- 文本搜索；
- 当前节点 1-hop / 2-hop 局部图；
- 前置链/后继链高亮；
- 目标学习路径高亮；
- 学生 mastery 状态叠加：已掌握 / 薄弱 / 未观察；
- 图例；
- 节点详情：raw label、中文 display label、来源、关联 ExerciseUnit 数；
- 关系详情：evidence source、graph version、review status；
- 节点数过大时默认局部视图，不冻结浏览器。

禁止重新通过 knowledgeCode 猜节点位置或章节。

### 性能/可用性验收

至少在真实 Published Graph 上记录：

- node/edge count；
- 首屏渲染时间；
- 搜索响应；
- 1-hop/2-hop 切换；
- 路径高亮；
- 浏览器无明显卡死；
- Playwright 覆盖搜索/选择节点/路径高亮。

---

## 6. 第五任务：把推荐证据链做成可解释产品

当前生产链必须保留：

```text
AnswerRecord
 -> RuleBeta mastery / UNKNOWN
 -> weak KnowledgePoint
 -> Published Graph prerequisite
 -> Recommendation
 -> Learning Path
```

推荐页增加可解释证据：

- 当前 mastery / status；
- attempt / correct count；
- 最近 30 天错误次数；
- 距离上次练习时间；
- 是否是目标知识直接/间接 prerequisite；
- 来源 Published Graph version；
- 为什么排在当前 rank；
- 若无足够观测，明确 UNKNOWN，不补数字。

目标学习路径展示：

```text
学生当前状态
 -> 未掌握前置节点
 -> 目标知识
```

并在图谱中同步高亮同一条路径。

### 推荐消融

如果数据条件允许，至少实现离线对照接口/脚本，不需要虚构线上 A/B：

- Popular/Random（无学生状态、无图谱）
- Mastery only
- Graph only
- Mastery + Graph（当前主方法）

评价指标必须先定义，再运行。没有真实可评价 ground truth 时，不得编造“准确率”。可以报告结构有效性、prerequisite validity、coverage、冗余、路径长度以及人工/专家审核结果。

---

## 7. 第六任务：正式数据治理后台

当前“数据与算法”页只是冻结科研快照，不能继续冒充实时治理。

实现最小正式数据治理模型/API：

- DatasetSource
- DatasetVersion
- ImportRun
- ImportConflict（或等价结构）

后台至少展示：

- 数据源名称/来源/许可或 provenance 说明；
- 数据版本；
- 输入文件 checksum；
- 导入时间；
- create/update/skip/conflict；
- 数据质量结果；
- 当前业务数据量；
- 当前 Published Graph version；
- 最近一次导入状态。

敏感本地路径只在开发日志中出现，Web 页面不要显示用户机器绝对路径。

---

## 8. 第七任务：教学资源中心

不允许为了页面丰富随便伪造教材/视频。

建立最小资源领域模型，例如：

- LearningResource
- ResourceType
- ResourceSource / license
- KnowledgePointResourceLink

支持：

- 文档/视频/外链/例题等类型；
- 标题、来源、授权/许可状态；
- 与 KnowledgePoint 关联；
- 教师新增/编辑/下架；
- 学生节点详情查看关联资源；
- 没有合法资源时明确显示“暂无资源”。

若当前本地没有合法资源素材，只实现模型/API/UI 空状态/测试，不得批量生成假的真实课程资源。

---

## 9. 第八任务：课程层级与角色产品补齐

课程层级不能靠前端硬编码。真实数据导入后，根据数据与业务需求决定是否增加 Chapter/Section/KnowledgeCollection 等实体，并写 ADR。

至少检查并补齐：

### 学生

- 首页
- 课程学习
- 知识图谱
- 个性化学习
- 练习/错题入口
- 学习报告

### 教师

不要所有功能继续堆在一个大页面。拆分或通过清晰二级导航组织：

- 教学概览
- 课程/内容管理
- 学情分析
- 图谱/资源
- 教学评价

### 管理员

- 用户与权限
- 课程体系
- 数据治理
- 知识图谱治理
- 系统运行/审计

现有 RBAC 与 teacher course authorization 必须保留且增加负向权限测试。

---

## 10. 第九任务：Rasch 生产集成只能作为可选增强

不要因为 MODEL-3 Gate 是 `GO_RASCH_ONLY_INTEGRATION` 就把现有 RuleBeta mastery 替掉。

如果做 Rasch integration：

- 必须 feature flag；
- 标注为 student global ability / response-risk signal；
- 不写成 KnowledgePoint mastery；
- 保留 RuleBeta 作为解释性知识点状态；
- 增加 OFF/ON 回归测试；
- 没有明确推荐增益证据时默认 OFF；
- 页面清楚区分“知识点掌握度”和“总体能力/答题风险”。

---

## 11. 第十任务：测试与 Final Gate

所有新增工作完成后必须执行仓库现有测试，再增加真实数据专项测试。

最低命令（按仓库实际脚本调整）：

```powershell
cd $REPO_ROOT

# Python / data pipeline
python -m pytest data-pipeline/tests model-service/tests

# Backend
cd backend
./mvnw test
cd ..

# Frontend
cd frontend
npm ci
npm run build
cd ..

# Compose definition
docker compose config

# Full release stack / browser E2E
# 使用仓库现有 release workflow / scripts；不得用 mock 页面替代。
```

### 必须新增的 E2E 行为

真实数据接入后，Playwright 不得断言固定课程名、固定 `DM-*` 编码、固定 16 节点等 fixture 细节；必须验证通用业务行为：

- 学生可看到导入后的中文数学课程/领域；
- 课程页结构来自后端；
- 无合法 Question 的 ExerciseUnit 显示“暂无可用练习”；
- 选择一个确有平台 Question 的练习可真实答题；
- 推荐页展示可解释证据；
- 学习路径与 Published Graph 一致；
- 知识图谱可搜索/局部展开/路径高亮；
- 教师只看有权限课程；
- 管理员能看到真实 ImportRun / GraphVersion；
- 403 负向权限仍通过。

### Final Gate

只有同时满足以下条件才允许返回：

```text
GO_REAL_DATA_INTEGRATION
```

- raw data provenance 核对完成；
- deterministic business export PASS；
- Java dry-run PASS；
- first import PASS；
- second identical import idempotent PASS；
- GraphValidator PASS；
- Published Graph projection PASS；
- backend tests PASS；
- python/data tests PASS；
- frontend build PASS；
- compose config PASS；
- full-stack API smoke PASS；
- real browser Playwright PASS；
- 未提交 raw dataset/secrets；
- 没有离散数学前端硬编码回流；
- 没有虚假 Question/Resource/算法归因。

如果有任意阻塞，返回：

```text
BLOCKED_REAL_DATA_INTEGRATION
```

并逐项列出：

1. 已完成；
2. 未完成；
3. 阻塞文件/命令/错误；
4. 当前 branch/HEAD；
5. 测试结果表；
6. 数据实际数量；
7. 导入 create/update/skip/conflict；
8. Published Graph node/edge/rejected counts；
9. 下一条最小修复动作。

**不要只写“已完成”或“测试通过”。必须给可复核证据。**

---

## 12. Codex 最终汇报模板

执行结束必须按下表回答，不得省略：

| 项目 | 结果 | 证据 |
|---|---|---|
| REPO_ROOT | | |
| branch / HEAD | | |
| 原始数据实际路径 | | |
| Exercise metadata 行数 | | |
| Interaction 行数 | | |
| Area / Topic / Exercise | | |
| business export | PASS/FAIL | |
| 中文 mapping | reviewed/unreviewed/quarantine | |
| Java dry-run | PASS/FAIL | create/update/skip/conflict |
| Java real import | PASS/FAIL | |
| Graph evidence import | PASS/FAIL | |
| Graph validation | PASS/FAIL | |
| Published Graph | node/edge/rejected | |
| RuleBeta recommendation | PASS/FAIL | |
| graph-based path | PASS/FAIL | |
| data governance UI | PASS/FAIL | |
| resource center | PASS/FAIL/BLOCKED_NO_LEGAL_RESOURCE | |
| backend tests | PASS/FAIL | |
| python tests | PASS/FAIL | |
| frontend build | PASS/FAIL | |
| compose config | PASS/FAIL | |
| API smoke | PASS/FAIL | |
| Playwright E2E | PASS/FAIL | |
| raw data/secrets committed | MUST BE 0 | |
| Final Gate | GO_REAL_DATA_INTEGRATION / BLOCKED_REAL_DATA_INTEGRATION | |

最后附：

- Git diff summary；
- migration 清单；
- 新 API 清单；
- 新/改测试清单；
- 真实截图路径；
- 任何未解决风险。
