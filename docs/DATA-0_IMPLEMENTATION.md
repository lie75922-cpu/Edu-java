# DATA-0 实施文档

## 1. 阶段目标

DATA-0 的目的不是“把 Junyi 数据导进系统”，而是在正式冻结数据库、图谱与模型接口之前，用真实数据验证以下关键假设：

1. 是否能获得足够规模且可合法用于研究/求职 Demo 的学生-练习交互数据；
2. Exercise 是否能稳定映射到 Topic/Area/知识关系；
3. Prerequisite 图是否具有足够覆盖率、连通性与可用性；
4. 是否能够构造 NCDM/RCD/GEAR-CD/ORCDF 所需的统一训练输入；
5. 在 20 万～50 万条交互规模下，CPU 数据处理与单卡训练成本是否可控；
6. 研究数据域与平台业务域如何映射，哪些字段可以复用，哪些必须由平台自己产生。

只有 DATA-0 Gate 通过后，才允许冻结 Dataset V1 和正式模型输入 Schema。

---

## 2. 数据源与许可边界

### 2.1 主数据候选：Junyi Academy

公开说明可获得：

- `junyi_Exercise_table.csv`
  - `name`
  - `live`
  - `prerequisite`
  - `h_position`
  - `v_position`
  - `creation_date`
  - `seconds_per_fast_problem`
  - `pretty_display_name`
  - `short_display_name`
  - `topic`
  - `area`
- `relationship_annotation_training.csv`
- `relationship_annotation_testing.csv`
  - `Exercise_A`
  - `Exercise_B`
  - `Similarity_avg`
  - `Difficulty_avg`
  - `Prequesite_avg`
  - raw worker scores
- `junyi_ProblemLog_original.zip`
  - `user_id`
  - `exercise`
  - `problem_type`
  - `problem_number`
  - `topic_mode`
  - `suggested`
  - `review_mode`
  - `time_done`
  - `time_taken`
  - `time_taken_attempts`
  - `correct`
  - `count_attempts`
  - `hint_used`
  - `count_hints`
  - `hint_time_taken_list`
  - `earned_proficiency`
  - `points_earned`

### 2.2 许可

Junyi 数据公开说明明确：**Any form of commercial usage is not allowed**。

本仓库用途限定为：

- 学术/技术研究；
- 个人学习；
- 非商业求职 Demo；
- 模型复现实验。

不得将该数据直接用于商业收费产品。任何未来商业化部署必须切换到自有/重新授权的数据源。

### 2.3 辅助数据

可以使用已公开的 RCD/InsCD/GEAR-CD 预处理数据帮助：

- 快速验证字段结构；
- 对照处理逻辑；
- 运行 baseline；
- 检查图关系。

但必须记录二次数据来源，不将其冒充为自己生成的数据。

---

## 3. 数据规模目标

DATA-0 不追求全量 Junyi。目标是得到一个足够“像真实项目”但成本可控的中等规模子集：

| 对象 | 目标规模 |
|---|---:|
| 学生 | 8,000～12,000 |
| Exercise | 700～1,000 |
| Topic/知识节点 | 100～300+ |
| 有效交互 | 200,000～500,000 |
| 有向先修边 | 以真实审计为准，目标至少数百条有效关系 |

这些是工程目标区间，不是预先声称的数据事实。最终数值由 `data/reports/data0_summary.json` 冻结。

---

## 4. 不采用的做法

### 4.1 不直接随机抽 1 万学生

随机抽学生可能导致：

- 大量 Exercise 彼此不连通；
- 先修图覆盖率下降；
- 学生序列过短；
- 热门题过度集中；
- 模型和图谱无法形成统一业务闭环。

### 4.2 不先决定模型再强行改数据

DATA-0 先产生统一 Canonical Dataset，再由模型 Adapter 转换到各模型格式。

### 4.3 不把离线用户导入平台用户表

Junyi `user_id` 永远属于研究数据域；不得生成虚假姓名、邮箱、手机号或登录账户。

### 4.4 不把 Exercise 当完整 Question 题库

Junyi 的 Exercise/ProblemLog 适合作为能力单元和认知诊断输入，但并不保证提供平台展示所需的稳定题干、选项和解析。

平台 Question 必须由业务题库独立管理。

---

## 5. Canonical 数据模型

外部数据首先统一成内部科研 Schema，不允许模型代码直接依赖 Junyi 原始列名。

### 5.1 Student

```json
{
  "student_external_id": "..."
}
```

### 5.2 Exercise

```json
{
  "exercise_external_id": "...",
  "name": "...",
  "display_name": "...",
  "topic": "...",
  "area": "...",
  "prerequisite_external_id": "...",
  "seconds_per_fast_problem": 30
}
```

### 5.3 Interaction

```json
{
  "student_external_id": "...",
  "exercise_external_id": "...",
  "correct": true,
  "attempts": 1,
  "duration_seconds": 20.5,
  "hint_used": false,
  "count_hints": 0,
  "earned_proficiency": false,
  "occurred_at": "2015-01-01T00:00:00Z"
}
```

### 5.4 KnowledgeRelation

```json
{
  "source_external_id": "exercise-A",
  "target_external_id": "exercise-B",
  "relation_type": "PREREQUISITE",
  "confidence": 1.0,
  "verified": true,
  "source": "JUNYI_EXPERT_OR_ANNOTATION"
}
```

### 5.5 DatasetManifest

```json
{
  "dataset_name": "JUNYI_MID",
  "version": "1.0.0",
  "source": "Junyi Academy",
  "license_note": "non-commercial",
  "student_count": 0,
  "exercise_count": 0,
  "interaction_count": 0,
  "relation_count": 0,
  "split_strategy": {},
  "sha256": ""
}
```

---

## 6. DATA-0 处理流水线

```text
Raw Junyi
   ↓
Schema Inspector
   ↓
Field Normalizer
   ↓
Data Quality Audit
   ↓
Knowledge Graph Audit
   ↓
Exercise Scope Selection
   ↓
Student Sequence Filtering
   ↓
Stratified Sampling
   ↓
Canonical Dataset
   ↓
Train / Valid / Test
   ↓
Dataset Manifest + EDA Report
```

### 6.1 Step A：原始结构检查

输出：

- 文件清单；
- 文件大小；
- 列名；
- 数据类型；
- NULL率；
- 唯一值数量；
- 异常时间字段；
- 重复行。

### 6.2 Step B：Exercise 质量检查

检查：

- `exercise` 唯一性；
- topic/area 缺失率；
- prerequisite 是否引用存在 Exercise；
- live 状态；
- Exercise 是否有实际交互。

### 6.3 Step C：行为日志清洗

规则初始值：

- 删除 user/exercise/correct 缺失；
- 时间转 UTC；
- 对同一学生按 `time_done` 稳定排序；
- 保留原始 attempts/hints 字段；
- 极端 duration 只标记异常，不静默改值；
- 不把 `earned_proficiency` 作为模型标签，除非单独设计实验。

### 6.4 Step D：图结构审计

统计：

- 节点数；
- 有向边数；
- 自环；
- 重复边；
- 环；
- 入度/出度分布；
- 弱连通分量；
- 孤立节点；
- 有交互但无图关系 Exercise；
- 有图关系但无交互 Exercise。

注意：外部 prerequisite 数据不假定天然满足 DAG。需要在报告中列出环并决定：删除、人工审查或保留为非正式关系。

### 6.5 Step E：选择中等规模 Exercise Scope

优先保留：

1. 位于主要连通分量；
2. 有足够学生交互；
3. topic/area信息完整；
4. 先修关系覆盖较好；
5. 不被少量超热门题完全主导。

### 6.6 Step F：学生序列过滤

开发初始规则：

- 最少有效交互：15；
- 最大序列不直接删除，采用训练窗口切分；
- 最终学生数量控制在 8k～12k；
- 对序列长度做分层采样，避免只保留重度用户。

阈值必须在 EDA 后确认，而不是永久硬编码。

### 6.7 Step G：数据划分

必须避免未来信息泄漏。

优先策略：**按学生划分 train/valid/test**。

开发候选比例：

- Train 70%
- Validation 10%
- Test 20%

如果模型论文需要特定划分方式，额外建立 Experiment Split，但必须在 Manifest 中单独记录，不覆盖主划分。

随机种子固定并写入 Manifest。

---

## 7. DATA-0 EDA 必须输出

### 7.1 基础统计

- student_count
- exercise_count
- interaction_count
- topic_count
- area_count
- prerequisite_edge_count

### 7.2 行为分布

- 每学生交互数 P25/P50/P75/P90/P99；
- 每 Exercise 交互数；
- correct rate；
- attempts 分布；
- duration 分布；
- hint usage；
- proficiency比例。

### 7.3 图统计

- 节点/边；
- 连通分量；
- 最大连通分量占比；
- DAG检查；
- 入度/出度；
- 图覆盖率。

### 7.4 数据联结率

至少输出：

```text
interaction → exercise 成功率
exercise → topic 成功率
exercise → area 成功率
exercise → prerequisite覆盖率
selected exercise → usable interaction覆盖率
```

---

## 8. DATA-0 质量 Gate

### 必须满足

1. 目标学生规模不低于 5,000；
2. 有效交互不低于 200,000，若低于该值需重新评估“中等规模项目”目标；
3. Exercise 与交互联结率接近 100%；
4. 选中 Exercise 的 topic/area 映射率满足系统展示需要；
5. 至少存在可用于路径/图模型实验的有效知识关系子图；
6. 至少 3 种模型能从 Canonical Schema 构建输入，而不需要回读原始文件；
7. Test split 在冻结后不因结果好坏重新划分。

### 触发重新选数据方案的条件

- 大多数有效 Exercise 没有可用关系；
- 关系图严重碎片化，无法形成有意义路径；
- 交互和图关系无法对齐；
- 模型输入必须依赖大量无法获得字段；
- 数据许可与项目公开方式发生冲突。

备选顺序：ASSISTments → Eedi/NeurIPS20 → MOOCCubeX小型CS子集。

---

## 9. DATA-0 代码目录

```text
data-pipeline/
├── README.md
├── pyproject.toml
├── src/edu_data/
│   ├── adapters/
│   │   └── junyi.py
│   ├── canonical/
│   │   └── schema.py
│   ├── audit/
│   │   ├── quality.py
│   │   └── graph.py
│   ├── sampling/
│   │   └── junyi_mid.py
│   ├── split/
│   │   └── student_split.py
│   └── manifest.py
├── tests/
└── reports/
```

原始数据目录统一加入 `.gitignore`，禁止向 GitHub 提交大体积原始数据。

---

## 10. DATA-0 产物

冻结时必须提交：

```text
reports/data0_summary.json
reports/data0_quality.md
reports/data0_graph.md
manifests/junyi_mid_v1.json
schemas/canonical-v1.json
```

数据本体若受许可或体积限制，不上传GitHub；只上传可复现脚本、Manifest、Schema和统计报告。

---

## 11. 成本预算

### 本地处理

目标数据规模使用 pandas/polars/duckdb 即可，不引入 Spark/Flink。

现金成本：0元。

### GPU

DATA-0本身不需要GPU。

### 存储

建议本地预留 15～30GB，包括原始压缩包、解压文件、中间Parquet和实验副本。

### 人力

预计 3～6 有效人日：

- 数据获取/校验：0.5～1日
- Schema/清洗：1～1.5日
- 图审计：0.5～1日
- 采样/划分：0.5～1日
- EDA/Gate报告：0.5～1.5日

---

## 12. DATA-0 完成定义（DoD）

DATA-0 只有同时满足以下条件才标记完成：

- [ ] 原始数据来源和许可已记录
- [ ] Canonical Schema 已冻结
- [ ] Junyi 中等规模采样实际完成
- [ ] 真实学生/Exercise/交互数已统计
- [ ] 先修图质量已审计
- [ ] train/valid/test 已冻结
- [ ] Manifest 含 SHA256
- [ ] NCDM/RCD/主模型候选均可从同一 Canonical Dataset 构造输入
- [ ] 未将研究用户导入平台注册用户
- [ ] 所有统计可通过脚本重复生成

未完成上述清单前，不允许在README或简历中声称最终数据规模和模型效果。
