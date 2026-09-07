# Edu-java DATA-0 Codex 工作交接与执行控制书

**文档状态：执行基线**  
**阶段：DATA-0 / 数据事实审计与 Dataset Gate**  
**项目仓库：`lie75922-cpu/Edu-java`**  
**Codex执行基线：远程 `main`，不得从 Research Exercise Catalog 实验分支继续开发**

---

## 1. 交接目的

本文件用于将项目从“架构设计/实验功能评审”正式交接至 Codex 的真实环境执行阶段。

本轮 Codex 的唯一目标不是继续增加产品功能，而是建立可审计、可重复、可追溯的 **DATA-0 数据基线**，为后续 V0.2 领域模型、MySQL Schema、Neo4j Schema 和 MODEL-0 提供证据。

DATA-0 完成以前，下列结论均不得冻结：

- `ExerciseUnit` 与 Junyi `Exercise` 的最终映射；
- `KnowledgePoint` 应由 Exercise、Topic 还是其它层级承担；
- Junyi `prerequisite` 是否可以直接作为 Neo4j `PREREQUISITE`；
- NCDM / RCD / ORCDF / GEAR-CD 中哪一个进入正式模型路线；
- 最终学生数、Exercise数、Interaction数和知识节点数；
- V0.2 Course / ExerciseUnit / Question / KnowledgePoint 正式表结构。

---

## 2. 项目前因与当前决策

项目最终目标是构建一个真实的软件工程项目，而不是单纯科研 Notebook：

```text
学习 / 答题
    ↓
行为记录
    ↓
认知诊断
    ↓
知识掌握状态
    ↓
知识关系图
    ↓
候选生成 / 约束
    ↓
个性化推荐与学习路径
```

早期曾考虑 MOOCCubeX + Knowledge Tracing / HCGKT，但真实检查后发现 MOOCCubeX 全量数据远超当前求职项目所需规模，会显著增加下载、ETL、存储和实验成本。因此当前以 Junyi 作为主数据候选，目标是形成具有一定规模、关系完整且成本可控的中等子集。

当前开发原则固定为：

> **Evidence First → Design Second → Implementation Third**

Codex 本轮不是证明前期架构正确，而是寻找能够支持或推翻前期假设的真实证据。

---

## 3. 角色与决策权

### Owner

用户本人，负责目标、预算和最终决策。

### ChatGPT

角色：**System Architect / Research Planner / Independent Reviewer**。

负责：

- 数据与算法路线；
- 系统架构；
- Gate 设计；
- Codex交付审查；
- DATA-0后重新冻结 V0.2 与 MODEL-0。

### Codex

角色：**Implementation & Experiment Engineer**。

负责：

- 真实数据获取；
- 环境与文件操作；
- 数据解析、EDA、采样和验证；
- 测试与CI；
- 运行日志、SHA和成本记录；
- 将证据提交回仓库。

### Codex无权自行改变

- 主数据集；
- 研究问题；
- 领域实体定义；
- 最终模型；
- 测试集；
- 是否进入V0.2；
- 是否引入LLM、Agent、Kafka、Spark、Flink、微服务等非当前需求技术。

遇到研究或架构歧义时，Codex应输出备选方案与证据，不得代替Owner/ChatGPT冻结结论。

---

## 4. 配置管理与分支策略

### 4.1 当前分支状态

`main` 是 V0.1 工程基线。

现有 `codex/junyi-catalog-20260907` 及其评审分支属于 **Research Exercise Catalog PoC**。该功能可以保留用于后续参考，但它没有完成 DATA-0，因此不得作为本轮 DATA-0 的开发基线。

### 4.2 Codex必须从main创建新分支

```bash
git switch main
git pull --ff-only
git switch -c codex/data0-junyi-20260907
```

如果真实执行日期变化，可更新日期后缀，但必须保持 `codex/data0-junyi-*` 命名。

### 4.3 禁止直接修改main

所有DATA-0工作均在独立分支完成，完成后创建PR或提交完整SHA供审查；在ChatGPT/Owner通过Gate前不得合并。

---

## 5. 已确认的数据与代码来源登记

Codex开始执行前必须建立 `data-pipeline/reports/data_sources.md`，实际下载后补齐URL、下载时间、文件大小和SHA256。

### S1 — Junyi原始/主数据

优先目标：Junyi Academy公开数据（EDM 2015相关数据）。

重点文件：

- `junyi_Exercise_table.csv`
- `relationship_annotation_training.csv`
- `relationship_annotation_testing.csv`
- `junyi_ProblemLog_original.zip`

原始数据公开说明含 Exercise metadata、student problem log、relationship annotation。数据明确限制商业使用。

如果直接原始来源因登录、网络或权限无法获得，必须记录真实失败原因；不得将镜像文件改称原始官方文件。

### S2 — RCD作者处理数据与参考代码

仓库：`bigdata-ustc/RCD`

用途：

- 对照 Junyi 处理结构；
- 核验 `K_Directed.txt` / `K_Undirected.txt`；
- 理解 Student–Exercise / Exercise–Concept 图构造；
- MODEL-0 后续复现参考。

RCD原仓库环境较旧，不应直接作为生产模型服务依赖。

### S3 — InsCD统一实验框架

仓库：`ECNU-ILOG/InsCD`

用途：

- NCDM / RCD / ORCDF 等模型的统一实验接口；
- Junyi734等处理数据作为对照；
- MODEL-0工程候选。

InsCD代码采用MIT许可。DATA-0不能因为InsCD已有Junyi处理版本而跳过主数据审计。

### S4 — GEAR-CD作者补充数据/代码

来源：Figshare条目 **“The preprocessed dataset and code for GEAR-CD”**。

用途：

- 输入Schema对照；
- 依赖审计；
- 成本与适配性评估。

DATA-0阶段禁止完整训练GEAR-CD。只有成本很低时允许tiny smoke test。

### S5 — NCDM原论文实现参考

仓库：`bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD`

用途：

- 非图认知诊断baseline输入定义；
- 过滤和Q-matrix设计的历史实现参考。

不得照搬旧环境进入生产服务。

### S6 — ORCDF作者实现参考

仓库：`ECNU-ILOG/ORCDF`

用途：

- Response Graph输入需求；
- MODEL-0图模型备用方案；
- 评估Junyi对ORCDF的适配程度。

---

## 6. Provenance强制规则

每一个被使用的数据文件必须记录：

```text
source_id
source_type
source_label
source_uri
source_file_name
acquired_at
compressed_size
uncompressed_size
sha256
license_note
citation
local_path
purpose
```

`source_type` 只能使用：

- `PRIMARY`
- `AUTHOR_PREPROCESSED`
- `THIRD_PARTY_PROCESSED`
- `REFERENCE_CODE`

不能证明为原始发布方数据时，不得标记 `PRIMARY`。

当前Catalog PoC曾使用“Junyi via USTC mirror”笼统标签，这不足以满足DATA-0。Codex必须追溯当前镜像具体来自哪个仓库/文件/commit或下载URL，并计算本地源文件SHA256。

---

## 7. Work Package A — 执行环境审计

输出：`data-pipeline/reports/environment.md`

至少记录：

- OS / version
- CPU model
- logical cores
- RAM total
- disk free
- Python
- Java
- Maven
- Git
- Docker
- GPU model（若无则明确写None）
- VRAM / CUDA（若无则N/A）

DATA-0理论上不需要GPU；不得为了方便启动付费GPU实例。

**验收条件：环境报告可从命令输出追溯，不能凭手填估计。**

---

## 8. Work Package B — 原始文件与Schema审计

至少审计：

1. Exercise metadata；
2. ProblemLog；
3. relationship annotation training；
4. relationship annotation testing。

每个文件必须输出：

```text
file name
sha256
row count
column count
column names
data types
missing count/rate
unique count
exact duplicate rows
10 real samples
```

真实样例放在：

```text
data-pipeline/reports/samples/
```

原始个人身份信息如存在，必须保持匿名ID，不额外制造姓名、邮箱、手机号。

---

## 9. Work Package C — Exercise层级与语义审计

至少统计：

- Exercise总行数；
- external ID唯一数；
- 重复ID及具体记录；
- live分布；
- topic总数及缺失率；
- area总数及缺失率；
- prerequisite原始值缺失率；
- 每topic Exercise数；
- 每area Exercise数。

特别要求：

当前已观察到某镜像字段为 `prerequisites`（复数），而公开原始说明曾使用 `prerequisite`（单数）。Codex必须确认：

1. 当前镜像是否做过列名/结构转换；
2. 原始文件真实列名；
3. `prerequisites` 是否由镜像加工而来；
4. 多个prerequisite的分隔和语义；
5. 是否存在重复token、悬空引用或空白token。

输出：`data-pipeline/reports/prerequisite_semantics.md`。

不得静默把逗号split后的结果当正式知识图边。

---

## 10. Work Package D — Student Interaction EDA

必须基于真实 ProblemLog 统计：

- Student count；
- Interaction count；
- referenced Exercise count；
- correct rate；
- attempts分布；
- duration分布；
- hint使用；
- proficiency分布；
- 时间跨度；
- invalid/missing timestamp；
- exact duplicate / business-key duplicate。

学生序列必须：

```text
group by student
→ parse timestamp
→ stable sort
```

不得依赖源文件天然顺序。

### 序列长度必须输出

```text
min
P10
P25
P50
P75
P90
P95
P99
max
mean
```

并统计：

```text
<5
<10
<15
<20
<30
```

交互记录的学生数量。

只有得到该分布后才能决定 `min_interactions`；禁止先把15写死为最终值。

---

## 11. Work Package E — Relation / Graph Audit

必须分别处理，不得混合：

1. Exercise metadata中的prerequisite；
2. relationship annotation中的Prerequisite分数；
3. relationship annotation中的Similarity；
4. RCD `K_Directed`；
5. RCD `K_Undirected`；
6. Topic / Area层级关系。

每一种关系分别输出：

```text
nodes
edges
self loops
duplicate edges
dangling references
components
largest component size/ratio
isolated nodes
in-degree/out-degree distribution
cycle count / representative cycles
```

用于学习路径的先修关系必须额外进行DAG检查。

发现cycle时禁止自动删除；记录具体边和来源交由ChatGPT审查。

---

## 12. Work Package F — 领域抽象比较

DATA-0必须用真实数据比较至少三种方案：

### A. Exercise = KnowledgePoint

### B. Topic = KnowledgePoint，Exercise → Topic

### C. ExerciseUnit + KnowledgePoint + Topic/Area层级

比较维度：

- 与源数据语义一致性；
- 节点/边规模；
- 图连通性；
- Q-matrix可构建性；
- NCDM适配；
- RCD适配；
- ORCDF适配；
- GEAR-CD适配；
- Java在线题库兼容；
- 学习路径解释能力；
- 推荐粒度；
- 后续扩展性。

输出：`data-pipeline/reports/domain_mapping_options.md`。

Codex可以给推荐意见，但不得把某一方案直接写入正式V0.2 Schema。

---

## 13. Work Package G — 可重复中等规模采样

禁止 `random.sample(users, 10000)` 后再补关系。

流程应为：

```text
关系图审计
→ 选择结构和行为都可用的Exercise Scope
→ 拉取对应Interaction
→ 数据质量过滤
→ 学生序列过滤
→ 按序列长度分层抽样
→ 重新验证Graph Coverage
```

至少产生：

### Small
约5k students（若真实数据允许）

### Medium
目标8k–12k students / 20万–50万 interactions

### Large
约15k students（若真实数据允许）

以上均为目标而非必须硬凑的数字。

每档记录：

```text
students
exercises
interactions
topics
areas
relation edges
largest component ratio
sequence statistics
disk size
ETL wall time
peak RAM
```

所有采样固定random seed并可从源数据一键复现。

---

## 14. Work Package H — Canonical Dataset与Split

Canonical数据不能丢失真实语义。

至少形成：

### ExerciseRecord

```text
exercise_external_id
display_name
topic
area
live
prerequisite_raw
```

### InteractionRecord

```text
student_external_id
exercise_external_id
correct
attempts
duration_seconds
hint_used
count_hints
earned_proficiency
occurred_at
```

### RelationshipRecord

```text
source_external_id
target_external_id
relation_type
score
source
verified
```

如果DATA-0尚未证明Topic就是Knowledge，则禁止凭空生成伪Knowledge ID。

### Split

至少比较：

- student-level split；
- temporal split；
- 论文复现需要时的interaction-level split。

主项目评测优先防止同学生信息泄漏。最终split必须记录seed和成员hash；test冻结后不得因为模型结果重新划分。

---

## 15. Work Package I — Model Readiness（不是正式训练）

对以下模型逐一输出：

### NCDM

- 需要哪些实体/矩阵；
- 当前数据是否直接提供；
- Q-matrix如何得到；
- 缺失项与转换成本。

### RCD

- Student–Exercise；
- Exercise–Concept；
- Concept–Concept；
- 与当前Junyi/RCD处理数据的差异。

### ORCDF

- Response Graph能否由当前Interaction构建；
- 是否需要额外Concept定义。

### GEAR-CD

- Knowledge Concept Graph；
- Exercise–Concept Graph；
- Student–Exercise Graph；
- 当前数据是否真实满足；
- 作者代码/补充数据环境和训练成本。

输出：`data-pipeline/reports/data0_model_readiness.md`。

DATA-0禁止完整训练GEAR-CD；其它模型也只允许廉价的输入转换测试或tiny smoke test，不进行正式指标调优。

---

## 16. Work Package J — 成本与可重复性

输出：`data-pipeline/reports/data0_cost.md`

记录真实：

- download GB；
- raw disk GB；
- processed disk GB；
- ETL wall time；
- peak RAM；
- CPU hours（可取得时）；
- GPU hours；
- 实际现金支出。

如果GPU和成本为0，应如实写0。

同时提供一个可重复的入口，例如：

```bash
python -m edu_data.data0 ...
```

或：

```bash
make data0
```

若原始数据必须人工登录下载，则自动化从“数据已放到指定目录”开始，并提供明确的人工下载说明。

---

## 17. 强制交付物

DATA-0分支至少提交：

```text
data-pipeline/reports/environment.md
data-pipeline/reports/data_sources.md
data-pipeline/reports/schema_audit.md
data-pipeline/reports/data0_summary.json
data-pipeline/reports/data0_quality.md
data-pipeline/reports/exercise_eda.md
data-pipeline/reports/interaction_eda.md
data-pipeline/reports/prerequisite_semantics.md
data-pipeline/reports/data0_graph.md
data-pipeline/reports/domain_mapping_options.md
data-pipeline/reports/data0_sampling.md
data-pipeline/reports/data0_model_readiness.md
data-pipeline/reports/data0_cost.md
data-pipeline/reports/bad_cases.md
data-pipeline/reports/data0_final_gate.md

data-pipeline/manifests/junyi_mid_v1.json
data-pipeline/schemas/canonical-v1.json
```

原始大数据、Parquet主体、checkpoint、日志、环境秘密不得提交Git。

---

## 18. 测试要求

新增数据转换逻辑至少覆盖：

- valid record；
- missing required field；
- malformed timestamp；
- duplicate row；
- unknown/dangling Exercise；
- invalid relation；
- empty prerequisite；
- duplicate prerequisite token；
- raw/parsed语义一致性；
- deterministic sampling；
- train/valid/test overlap检查。

现有GitHub Actions中的 Backend / Python / Frontend / Compose 必须保持绿色。

DATA-0 PR未通过CI不得提交“完成”结论。

---

## 19. 禁止事项

Codex不得：

1. 为了满足20万/50万目标硬凑数据；
2. 删除Bad Case而不报告；
3. 把镜像数据声称为官方原始数据；
4. 把split后的`prerequisites`直接当正式知识关系；
5. 将Junyi user导入业务`sys_user`；
6. 将Junyi Exercise声称为完整Question；
7. 为提高模型指标修改Dataset/Test；
8. 在DATA-0期间继续开发页面、Course CRUD、推荐功能或教师看板；
9. 完整训练GEAR-CD；
10. 自动进入V0.2。

---

## 20. Final Gate

最终只能给：

- `GO`
- `CONDITIONAL_GO`
- `NO_GO`

### GO

真实Junyi数据足以支撑中等规模项目、知识结构和至少一个非图+一个图模型路线。

### CONDITIONAL_GO

整体可用，但仍有明确的领域/图/模型风险，必须在V0.2或MODEL-0解决。

### NO_GO

关键行为或知识关系不足，无法支撑当前闭环。

如果NO_GO，Codex不得自行换ASSIST/Eedi/MOOCCubeX；停止并回传证据，由ChatGPT重新做数据集可行性分析。

---

## 21. 最终回传格式

Codex结束时必须返回：

```text
Repository:
Branch:
HEAD SHA:
Commits:

Environment:

Primary data source:
Secondary sources:
Source SHA256:

Students:
Exercises:
Interactions:
Topics:
Areas:
Correct rate:

Sequence min/P10/P25/P50/P75/P90/P95/P99/max/mean:

Prerequisite nodes:
Prerequisite edges:
Components:
Largest component ratio:
Cycles:
Dangling refs:

Exercise→Topic coverage:
Exercise→Area coverage:
Exercise→Interaction coverage:

Small dataset:
Medium dataset:
Large dataset:

NCDM readiness:
RCD readiness:
ORCDF readiness:
GEAR-CD readiness:

ETL wall time:
Peak RAM:
GPU hours:
Cash cost:

Tests:
CI:
Bad cases:

FINAL GATE: GO / CONDITIONAL_GO / NO_GO
```

任何数字都必须能在提交的报告、Manifest或程序输出中定位。

---

## 22. Definition of Done

只有以下关键项全部完成，才能声明DATA-0完成：

- [ ] 主数据来源或无法获取原因已证据化；
- [ ] 每个使用文件来源、许可、SHA完整；
- [ ] Exercise真实Schema审计完成；
- [ ] ProblemLog真实Schema审计完成；
- [ ] relationship annotation审计完成；
- [ ] 真实样例已保存；
- [ ] Interaction EDA完成；
- [ ] Sequence EDA完成；
- [ ] prerequisite语义审计完成；
- [ ] Graph Audit完成；
- [ ] 三种领域映射方案完成对比；
- [ ] Small/Medium/Large完成比较；
- [ ] Medium数据可重复生成；
- [ ] Canonical Schema由真实数据验证；
- [ ] Split冻结并通过泄漏检查；
- [ ] Manifest完成；
- [ ] NCDM/RCD/ORCDF/GEAR-CD readiness完成；
- [ ] 成本报告完成；
- [ ] Bad Cases完成；
- [ ] CI通过或失败已明确解释；
- [ ] HEAD SHA回传；
- [ ] Final Gate完成。

完成后立即停止，不进入V0.2，等待ChatGPT/Owner独立审查。
