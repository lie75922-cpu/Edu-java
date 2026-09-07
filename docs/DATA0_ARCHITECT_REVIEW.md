# DATA-0 独立架构审查结论

**审查日期：2026-09-07**  
**审查对象：`DATA-0` 分支**  
**原执行结论：`CONDITIONAL_GO`**  
**架构审查结论：`ACCEPTED_FOR_MODEL0_AND_V0.2_DESIGN`**

## 1. 结论

DATA-0 已经完成“是否存在足够真实数据支撑后续研究”的核心验证，可以作为后续 MODEL-0 和 V0.2 详细设计的证据基线。

当前结果不等于：

- 数据拥有一级官方来源完整可重复性；
- Junyi prerequisite 已经成为正式知识图；
- 模型已经验证有效；
- 平台业务数据库已经可以直接导入研究数据。

因此本审查接受 DATA-0，但保留来源追溯、模型输入净化和图关系治理条件。

## 2. 已确认的数据事实

### 全量审计

- ProblemLog：25,925,992 条交互；
- 匿名学生：247,606；
- ProblemLog 实际引用 Exercise：722；
- Exercise 元数据：837 行；
- 不同 external ID：835；
- Topic：40 个非缺失值；
- Area：8 个非缺失值；
- 完全重复交互：1,439 行；
- 未知 Exercise 引用：11 行；
- 全量学生序列中位数：10；P75：50；P95：485；P99：约1671；
- 已审计全量正确率：约0.827874。

### Medium候选

- 学生：10,000；
- 交互：284,245；
- 实际发生交互的 Exercise：624；
- Topic：40；
- Area：8；
- 学生级划分：7,000 / 1,000 / 2,000；
- train/valid/test 学生交叉：0；
- 学生成员划分在任何模型结果产生前冻结。

## 3. 对采样统计口径的修正解释

DATA-0 的 Small / Medium / Large 报告中：

- `Exercises` 是各候选集中**实际产生交互的 Exercise 数**；
- `Relation edges=979` 和 `largest_component_ratio≈0.9092` 使用的是固定的 **graph scope**，不是各候选交互子集重新投影后的图指标；
- 当前代码的 graph scope 为 815 个可进入范围分析的 Exercise，并将同一份 scope relationships 写入三档候选。

因此三档数据图边数/连通率相同是既有实现设计，不是三档交互网络完全相同。

在 MODEL-0 前必须额外生成：

1. Medium observed-exercise induced graph；
2. 该图真实节点数、边数、连通分量、cycle/self-loop；
3. 与固定 scope graph 的差异。

在这些指标完成前，`979 edges` 不能被描述为“Medium 624个Exercise之间有979条关系”。

## 4. 领域模型决策

根据 DATA-0，接受 ADR-0002：

```text
KnowledgeArea (Area)
    └─ KnowledgePoint (Topic, first platform knowledge layer)
          └─ ExerciseUnit (Junyi Exercise / capability unit)
                └─ Question (platform-owned business item)
```

并明确：

- Exercise 不等于 Question；
- Exercise 不直接等于 KnowledgePoint；
- Topic 是第一版可解释 KnowledgePoint 来源，但保留 `source_type=JUNYI_TOPIC`；
- ModelConcept 属于算法 Adapter，不反向决定业务主键；
- 缺失 Topic 的 Exercise 保持 UNMAPPED，不伪造概念。

## 5. 图关系决策

根据 DATA-0，接受 ADR-0003：

- 原始 prerequisite、annotation、RCD关系都先作为 Evidence；
- 2个自环保留证据、禁止发布；
- 1个三节点循环保留证据、禁止直接进入学习路径；
- GraphValidator 对正式 Published Graph 强制 DAG；
- RCD numeric concept graph 在无法追溯 ID mapping 前只作为研究参考。

这解决 DATA-0 Final Gate 中“关系不能直接生产化”的条件，但不意味着原始关系已经完成治理。

## 6. Medium Dataset 的接受与派生规则

`JUNYI_MID` 的10,000名学生成员和70/10/20学生级split接受为 MODEL-0冻结基线。

**不得重新抽学生、不得根据模型结果更换test成员。**

但 DATA-0 全量质量审计发现：

- 1,439条完全重复交互；
- 11条未知Exercise引用。

DATA-0的候选物化逻辑没有全局去重，因此 MODEL-0 前必须在固定成员集内执行一次 Preflight：

1. 统计 Medium 内实际包含多少 exact duplicate；
2. 统计 Medium 内实际包含多少 unknown-exercise interaction；
3. exact duplicates 从模型输入派生版本中去重，但保留审计计数；
4. unknown-exercise interaction 不进入需要Q-matrix/元数据映射的模型输入；
5. 不修改学生split成员；
6. 生成 `JUNYI_MID_MODEL_V1` 派生Manifest，引用原 `JUNYI_MID`，不得覆盖原DATA-0产物。

## 7. 仍未解除的条件

### C1 — Primary provenance

官方 PSLC DataShop 目标已定位，但DATA-0未直接取得一级文件；当前实际输入来自第三方镜像且镜像URL未记录。

影响：

- 不阻塞个人研究/求职Demo继续开发；
- 阻塞“完全从官方原始文件可重复构建”的声明；
- 对公开交付必须继续标注 `THIRD_PARTY_PROCESSED / provenance-limited`。

Codex后续应尝试恢复镜像实际URL/获取来源；若Owner具备DataShop登录权限，可另行取得一级文件做文件级对照，但不得因此改变冻结test成员或已产生模型结果。

### C2 — Raw content integrity digest

当前执行约束没有留下raw source digest。该问题降低文件级可重复性，但已有文件大小、schema、行数、样例和来源分类作为次级证据。

在Owner没有解除相关约束前，不要求Codex违规生成digest；保持为已知限制。

### C3 — Peak RAM

Peak RAM未取得可靠全程测量。约1.23GiB只能作为瞬时观测，不能冒充峰值。

该指标不阻塞DATA-0通过；在MODEL-0运行时使用统一资源监控重新记录。

## 8. 下一阶段允许的工作

DATA-0之后允许两条线并行：

### 研究线：MODEL-0

- Medium派生模型输入预检；
- NCDM baseline；
- ORCDF graph-response candidate；
- RCD traceability investigation；
- GEAR-CD仅环境/最小smoke，暂不全量训练。

### 工程线：V0.2

- Course / KnowledgeArea / KnowledgePoint / ExerciseUnit / Question 领域与数据库；
- 平台题库与答题记录闭环；
- Research Catalog 只作为可选seed/import来源；
- 不导入Junyi user为平台用户；
- 不在V0.2把raw prerequisite直接发布到Neo4j。

## 9. DATA-0最终状态

从软件工程Gate角度：

```text
DATA-0 = PASSED WITH CONDITIONS
```

条件已经被分成：

- 已由架构决策解决：领域分层、图发布规则；
- MODEL-0前必须完成：Medium派生模型输入预检、候选特定图指标；
- 长期来源限制：一级数据直接获取、raw digest；
- 非阻塞资源指标：Peak RAM。

因此可以结束DATA-0，并进入受控的MODEL-0与V0.2阶段。
