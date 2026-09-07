# ADR-0005：MySQL权威关系生命周期与版本化Neo4j投影

- 状态：Accepted
- 决策日期：2026-09-07
- 前置：ADR-0002、ADR-0003、V0.2业务闭环

## 1. 背景

V0.2 已经冻结平台语义：

```text
Course
  → KnowledgeArea
    → KnowledgePoint（V0.2初始来源可为Junyi Topic）
      → ExerciseUnit
        → Question
```

DATA-0同时证明 Junyi 原始 prerequisite 位于 Exercise 粒度，并包含重复身份、自环和有向循环。因此不能把 raw Exercise prerequisite 直接写成平台 `KnowledgePoint-[:PREREQUISITE]->KnowledgePoint` 后交给Neo4j作为事实。

本项目还要求：

- 关系可审计、可拒绝、可回滚；
- 发布失败不能破坏当前在线图；
- Neo4j故障不能改变MySQL业务事实；
- 图版本可以与后续推荐/MasterySnapshot一起追溯。

因此需要明确“证据、候选关系、发布快照、Neo4j投影”四层职责。

## 2. 决策

### 2.1 MySQL是关系权威数据源

MySQL保存：

1. 关系证据 Evidence；
2. Draft Graph Version；
3. KnowledgePoint关系候选；
4. 校验/人工处理状态；
5. Published Graph Version；
6. 当前Course active graph version指针；
7. 发布操作与失败原因。

Neo4j只保存 **PUBLISHED Graph Version 的查询投影**。

禁止将仅存在于Neo4j、没有MySQL来源/版本记录的边视为正式业务关系。

### 2.2 正式学习图固定为KnowledgePoint层

V0.3正式发布图只包含：

```text
(:KnowledgePoint)-[:PREREQUISITE]->(:KnowledgePoint)
```

不在正式学习图中直接发布：

- ExerciseUnit→ExerciseUnit prerequisite；
- raw annotation score；
- RCD numeric graph；
- unresolved external-ID relation。

Exercise粒度关系继续作为 Evidence 保存。

原因：学习路径/学生Mastery/推荐最终都面向用户可解释的 KnowledgePoint；ExerciseUnit是练习载体而不是正式知识概念。

### 2.3 Exercise prerequisite到KnowledgePoint候选的转换

一条原始证据：

```text
Exercise A prerequisite of Exercise B
```

只有在：

- A唯一解析为一个内部ExerciseUnit；
- B唯一解析为一个内部ExerciseUnit；
- A存在至少一个受控Exercise→KnowledgePoint映射；
- B存在至少一个受控Exercise→KnowledgePoint映射；

时，才允许产生 `KnowledgePoint source → target` 候选。

当前Junyi初始数据通常为单Topic映射，因此第一版可得到一个Topic→Topic候选。

若：

- 任一Exercise身份冲突；
- 任一端没有Knowledge映射；
- 映射存在歧义；

则证据保留，候选状态为 `UNRESOLVED`，不得发布。

如果source和target映射到同一KnowledgePoint，则：

```text
REJECTED_SELF_LOOP
```

原始Evidence仍然保留。

多个Exercise证据投影到同一个KnowledgePoint pair时，不重复生成正式边；候选关系聚合 `evidence_count`，并可追踪全部Evidence ID。

### 2.4 证据不自动等于“已确认先修”

从 DATA-0 prerequisite 推导得到的Topic关系初始状态只能为：

```text
CANDIDATE
```

它可以被：

- 规则校验；
- 管理员人工批准/拒绝；
- 后续研究实验提供置信度；

但任何单次导入都不能自动把所有候选改为 `APPROVED`。

V0.3允许为了演示/工程测试建立**小型人工fixture图**并正式发布；真实Junyi批量关系是否批准属于后续关系治理，不得伪装成专家认证知识图。

### 2.5 Graph Version是不可变发布快照

`graph_version`生命周期：

```text
DRAFT
  → VALIDATING
  → READY
  → PUBLISHING
  → PUBLISHED
  → ARCHIVED
```

失败状态：

```text
VALIDATION_FAILED
PROJECTION_FAILED
```

一旦版本进入 `PUBLISHED`，其边集合不可原地修改。

修改关系必须：

1. 基于当前版本建立新的DRAFT；
2. 修改DRAFT关系；
3. 重新验证；
4. 发布新版本。

这样保证历史推荐、路径和后续Mastery结果能够绑定旧graphVersion进行追溯。

### 2.6 发布验证

GraphValidator至少检查：

- source/target均存在且属于同一Course；
- source != target；
- 无重复 `(source,target,relationType)`；
- KnowledgePoint状态允许发布；
- `PREREQUISITE`候选集合构成DAG；
- 对存在Exercise覆盖要求的节点给出warning/report，而不是静默忽略；
- relation必须具有来源（MANUAL或可追溯Evidence）。

存在任何自环或有向循环：

```text
Graph Version不得进入READY
```

### 2.7 发布采用异步版本投影，不删除当前在线图

发布请求流程：

```text
Admin publish
  → MySQL事务再次校验READY版本
  → version=PUBLISHING
  → 写 GRAPH_REBUILD_REQUEST 到 outbox_event
  → COMMIT
```

`GraphProjectionWorker`只消费 `GRAPH_REBUILD_REQUEST`，不得消费 `MASTERY_UPDATE_REQUEST`。

Worker：

```text
读取目标GraphVersion
  → 在Neo4j建立该version的新节点/边
  → 查询验证节点/边数量与关键约束
  → 成功：MySQL事务更新 course.active_graph_version_id
           新version=PUBLISHED
           旧version=ARCHIVED
  → 失败：新version=PROJECTION_FAILED
           当前active version不变
```

禁止：

```text
DELETE当前图 → 再慢慢写新图
```

### 2.8 Neo4j版本键

Neo4j KnowledgePoint投影至少包含：

```text
businessId
courseId
graphVersionId
knowledgeCode
name
projectionKey
```

`projectionKey`建议：

```text
{courseId}:{graphVersionId}:{businessId}
```

并建立唯一约束。

关系包含：

```text
relationId
graphVersionId
sourceType
```

业务层绝不依赖Neo4j内部node ID。

### 2.9 查询必须显式绑定active version

学生/教师端图查询：

1. 从MySQL Course取得 `active_graph_version_id`；
2. 以该version查询Neo4j；
3. 返回响应时携带 `graphVersionId`。

至少支持：

- 当前Published Graph子图；
- 某KnowledgePoint前驱；
- 某KnowledgePoint后继；
- 两KnowledgePoint之间的先修路径；
- 目标KnowledgePoint祖先子图。

## 3. 一致性边界

MySQL是强一致业务权威；Neo4j是异步读模型。

因此短时间内允许：

```text
DRAFT/READY存在于MySQL
但Neo4j仍服务旧Published版本
```

不允许：

```text
MySQL active_version已经切换
但Neo4j对应version尚未验证完成
```

active pointer只能在Neo4j投影成功后切换。

## 4. 恢复

Neo4j整个数据库可根据：

```text
MySQL PUBLISHED GraphVersion + KnowledgeRelation snapshot
```

重新构建。

因此备份优先级：

1. MySQL；
2. relation evidence/version数据；
3. Neo4j可重建投影。

## 5. 对后续版本的影响

V0.3只完成图治理和Published Graph。

不在V0.3实现：

- Student Mastery；
- Cognitive Diagnosis生产接入；
- Personalized Recommendation；
- 基于Mastery过滤的学习路径；
- LLM/Agent。

V0.3完成后，后续推荐与学习路径只能读取 `PUBLISHED` 图，不得绕过GraphVersion直接读取raw evidence。
