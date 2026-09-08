# Codex 执行任务：真实数学数据 → Java 业务系统 → Published Graph → 个性化学习闭环

## 0. 执行角色与硬约束

你是本仓库下一阶段的执行工程师。不要重新设计一个新项目，也不要推翻已经通过验证的 V0.7 工程底座。

仓库：`lie75922-cpu/Edu-java`

起始分支：优先从 PR #34 的最新 head `chatgpt/final-status-sync-20260908` 开始；执行前先 `git fetch` 并核对最新 SHA 和 CI，不允许从旧 main 直接重新做一套。

最新产品基线必须先阅读：

- `docs/V1_MATH_PLATFORM_BASELINE.md`
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

---

## 1. 本地真实输入

当前 provenance 已登记的本地输入包括：

```text
D:\Code\java\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv
D:\Code\java\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv
```

执行前必须：

- 检查文件是否真实存在；
- 输出大小、列名、编码、10 条脱敏样例；
- 与 DATA-0 报告记录比对；
- 如果路径已变化，自动定位仓库父目录/已登记数据目录，不要重新从网络下载一份不明来源数据；
- 如果真实输入不存在，停止“业务导入”步骤并明确 `BLOCKED_MISSING_LOCAL_DATA`，但可以继续完成不依赖原始数据的代码、测试、文档工作。

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
5. self-loop 标记冲突；
6. cycle 由 Validator 阻断；
7. 关系必须 review/approve 后才有资格进入 Published Graph；
8. 发布后执行 Neo4j projection verification。

### 输出真实计数

最终报告必须给：

```text
raw evidence rows = ?
resolved = ?
unresolved = ?
self-loop conflicts = ?
duplicate/ambiguous conflicts = ?
candidate relations = ?
approved = ?
rejected = ?
validator cycle errors = ?
published nodes = ?
published edges = ?
Neo4j verification = PASS/FAIL
```

Published Graph 的硬 Gate：

- self-loop = 0
- directed cycle = 0
- endpoint outside course = 0
- relation with missing provenance = 0

如果真实关系无法安全形成足够质量的 Topic-level prerequisite graph，不得硬发；可以保留为 Evidence + 部分 reviewed graph，并说明覆盖率。

---

## 5. 第四任务：大规模知识图谱前端

当前 V1 已经取消 `DM-LOGIC/DM-GRAPH` 前端推断，课程页和图谱页使用后端 Area/Point 数据。继续在此基础上做，不要退回硬编码。

当节点超过 100 后，禁止默认把 800 个节点全部一次性画在一张静态 SVG 上。

至少实现：

- 按 KnowledgeArea 筛选；
- 搜索 Topic/KnowledgePoint；
- focus selected node；
- predecessor/successor 局部子图；
- prerequisite subgraph；
- 学习路径高亮；
- 图例中文；
- 节点详情显示 raw provenance / 中文名 / 关联 ExerciseUnit 数；
- 学生视角叠加 mastery state：已掌握、薄弱、已有记录、暂无数据；
- 不把 UNKNOWN 渲染为 0.5 掌握。

如需引入图可视化库，优先选择成熟、轻量、维护正常的依赖，说明为什么选；不要为了“高级”引入重型框架。

---

## 6. 第五任务：把个性化推荐证据链展示清楚

当前 Java 推荐必须保持：

- RuleBeta Mastery（生产默认）；
- weak threshold 0.70；
- Published Graph prerequisite；
- recent errors；
- review interval；
- deterministic rank；
- recommendation snapshot + graph/rule version。

前端每条推荐至少展示可解释证据：

```text
推荐知识：xxx
当前状态：OBSERVED / 暂无数据
掌握度：xx%（若有）
近期错误：x 次（若 API 当前未返回，扩展 DTO）
图谱原因：是目标 xxx 的前置知识 / 当前知识本身薄弱
图谱版本：v?
规则版本：REC_RULE_V1
推荐动作：练习 / 复习 / 补前置
```

学习路径必须能够在图谱页高亮同一条路径，让用户肉眼看到：

```text
答题 -> 薄弱点 -> 未满足先修 -> 推荐 -> 路径
```

不要只显示一句“根据学习记录推荐”。

---

## 7. 第六任务：数据治理管理端

当前 `frontend/src/v1/researchSnapshot.js` 是冻结审计快照，只用于把已有研究工作显性化。它不是最终数据治理后端。

新增正式后端数据治理能力，至少提供：

- DatasetSource / DatasetVersion / ImportRun（可以先做只读运行记录，不要过度设计）；
- source provenance；
- latest import status；
- entity counts；
- missing/duplicate/conflict counts；
- graph evidence counts；
- mapping review counts；
- processing timestamp；
- research data vs platform business data 明确标签。

管理端“数据与算法”改为从 API 读取最新 run，同时保留冻结研究报告作为历史/实验记录入口。

---

## 8. 第七任务：算法接入边界与消融

### 当前冻结结论

MODEL-3：

- ExerciseRate AUC 0.707210
- Rasch AUC 0.724675
- Rasch - ExerciseRate = +0.017465
- 95% CI [0.012394, 0.022665]
- Hierarchical Rasch+Topic AUC 0.710015
- Gate = GO_RASCH_ONLY_INTEGRATION

### Java 集成规则

如果本轮做 Rasch 集成：

- 新建独立 provider / auxiliary signal；
- feature flag 默认关闭，除非有完整集成验收；
- θ 只能称“全局能力/答题风险辅助信号”，不得称 Topic mastery；
- RuleBeta 仍是 KnowledgePoint mastery；
- recommendation snapshot 必须记录使用了哪种 signal/version；
- 未通过对照实验不得声称推荐质量提升。

### 推荐/路径消融（在真实数据允许的范围内）

至少比较：

1. Popular/Random（无学生状态、无图）；
2. Mastery-only；
3. Graph-only；
4. Mastery + Graph（当前核心）；
5. 可选 Rasch + Graph。

不能用“生成推荐本身使用的同一条规则”作为唯一正确答案进行自我评分。

可用指标包括：

- prerequisite violation rate；
- valid path rate；
- coverage；
- redundancy / path length；
- held-out response relevance（明确只是 proxy）；
- 如有独立专家标注，再报告 expert agreement。

如果没有真实教学试验，不得写“学习效果提升 xx%”。

---

## 9. 角色与产品结构

保持四类后端权限：

- STUDENT
- TEACHER
- TEACH_ADMIN
- SYSTEM_ADMIN

产品至少保持学生/教师/管理三套信息架构分离。

不要把所有教师功能或管理 CRUD 再堆回一页。

下一步可以拆：

学生：课程学习 / 知识图谱 / 个性化学习 / 练习 / 学习报告。

教师：教学概览 / 学情分析 / 课程内容 / 图谱与资源。

管理：用户权限 / 课程体系 / 数据治理 / 图谱治理 / 系统运行。

教学资源中心只有在正式资源实体、来源、授权、关联模型完成后再上线；禁止前端虚构视频、课件、教材。

---

## 10. 必跑测试与发布验收

每次重要阶段必须执行并记录：

```bash
cd backend && mvn --batch-mode test
cd ../model-service && python -m pytest -q
cd ../data-pipeline && python -m pytest -q
cd ../frontend && npm install --no-package-lock --no-audit --no-fund && npm run build
cd .. && docker compose config
```

完整 release：

```bash
scripts/generate-local-release-env.sh .env.release

docker compose --env-file .env.release -f docker-compose.full.yml up --build -d
scripts/wait-for-ready.sh http://localhost:8080

docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run smoke
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run e2e
```

另外必须新增：

- real-data export tests；
- import idempotency test；
- no-research-student-to-user test；
- published graph self-loop=0 test；
- published graph cycle=0 test；
- cross-role/cross-course authorization test；
- >100-node graph UI smoke（至少使用生成的非生产测试 fixture 验证性能/交互）；
- recommendation evidence DTO/API test。

### CI Gate

`backend / python / frontend / compose-config / full-stack-release` 必须全部成功。

任何一个失败都不得写 `GO_RELEASE`。

---

## 11. 最终交付格式

完成后必须一次性向 Owner 返回以下内容，禁止只说“已完成”：

### A. 代码

- 分支名；
- 最终 commit SHA；
- PR 地址/编号；
- changed files 分类表。

### B. 真实数据结果

- 实际读取源文件路径；
- 实际读取记录数；
- export 实体数；
- import create/update/skip/conflict 数；
- KnowledgeArea/KnowledgePoint/ExerciseUnit 最终业务数量；
- Evidence/Published Graph 实际数量；
- 所有异常与未解决冲突。

### C. 测试

逐项列：

```text
backend: PASS/FAIL, tests=?
model-service: PASS/FAIL, tests=?
data-pipeline: PASS/FAIL, tests=?
frontend build: PASS/FAIL
compose-config: PASS/FAIL
clean-volume startup: PASS/FAIL
API smoke: PASS/FAIL
Playwright: PASS/FAIL
CI 5 jobs: ?/5
```

附失败根因，不得隐藏失败。

### D. 产品截图

至少：

- 学生首页；
- 真实数学课程页；
- >100 节点情况下的知识图谱筛选/局部图；
- 推荐解释；
- 学习路径高亮；
- 教师学情；
- 管理端数据治理；
- 管理端图谱治理。

### E. 尚未完成

单独列 `BLOCKED / DEFERRED / NOT_IMPLEMENTED`，说明为什么没有做、需要什么条件。

---

## 12. Final Gate

只有同时满足下列条件才返回 `GO_REAL_DATA_V1_RC`：

1. 真实本地数据已完成确定性 export；
2. Java 业务导入可重复、幂等、可审计；
3. 用户侧展示中文，但 raw English label/provenance 保留；
4. 课程结构不在 Vue 中按知识编码硬推断；
5. 原始 prerequisite 走完整图谱治理；
6. Published Graph 无 self-loop / cycle；
7. 科研匿名学生没有转成业务用户；
8. 没有伪造 Question/教学资源；
9. 个性化推荐能展示学生状态 + 图谱证据 + 推荐理由；
10. backend/python/frontend/compose/full-stack-release 全绿；
11. 真实截图人工检查没有明显英文业务术语、混乱 CRUD 或大规模图不可用问题；
12. 所有数据规模和算法结果均有可复核证据。

任一 P0 条件不满足，返回 `NO_GO` 或 `CONDITIONAL_GO`，不要降低标准。
