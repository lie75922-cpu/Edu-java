# ADR-0006：Mastery Provider 与规则兜底策略

- 状态：Accepted
- 决策日期：2026-09-08
- 适用阶段：V0.4 及后续模型接入

## 背景

V0.2 已经完成平台答题闭环，并在每次成功答题事务中写入 `MASTERY_UPDATE_REQUEST` Outbox；V0.3 已经完成版本化 Published Knowledge Graph。

MODEL-0A 仅证明“零历史新学生冷启动协议”不适合作为 NCDM / ORCDF 的最终模型选择依据。MODEL-0R 仍在独立实验线上进行，因此平台不能把 V0.4 阻塞在某个尚未被选定的模型上。

V0.4 需要先完成一个可运行、可解释、可替换的掌握度与推荐闭环，使平台能够在无模型、模型失败或模型不适用时继续工作。

## 决策

### 1. MasteryProvider 是唯一业务抽象

Java 业务层只依赖：

```text
MasteryProvider
  ├─ RuleBasedMasteryProvider   (V0.4默认)
  └─ ModelMasteryProvider       (后续MODEL-0R通过后接入)
```

推荐、学习路径、教师分析不得直接依赖 NCDM / ORCDF / ICDM 等具体模型类。

### 2. RuleBasedMasteryProvider 是正式兜底，不冒充AI

V0.4 使用透明规则估计 KnowledgePoint 掌握度。

每个学生×知识点维护至少：

- `attempt_count`
- `correct_count`
- `last_answered_at`
- `mastery_score`
- `algorithm_version`
- `source_type=RULE`
- `graph_version_id`（生成推荐/路径时绑定）

初始规则采用 Beta-Bernoulli 平滑正确率：

```text
mastery = (correct_count + alpha) / (attempt_count + alpha + beta)
```

V0.4 固定 `alpha=1, beta=1`，版本标识 `RULE_BETA_1_1_V1`。

该规则是工程可解释基线，不声称为认知诊断模型。

### 3. 一个答题事件可影响多个 KnowledgePoint

`AnswerRecord.exercise_unit_id` 通过 `exercise_knowledge` 映射到一个或多个 KnowledgePoint。

每个映射到的 KnowledgePoint 都独立更新统计；不得把 ExerciseUnit 本身当成 KnowledgePoint。

若 ExerciseUnit 无有效 KnowledgePoint 映射：

- AnswerRecord 保留；
- Outbox 事件标记业务处理失败或不可处理；
- 不生成虚假 Mastery。

### 4. Mastery 更新必须幂等

`MASTERY_UPDATE_REQUEST` 消费必须以 `answerRecordId` 为幂等键。

同一个 AnswerRecord 无论 worker 重试多少次，只能使学生知识统计增加一次。

### 5. 快照与当前态分离

MySQL 是 Mastery 事实权威源。

至少区分：

- `student_knowledge_mastery`：当前态；
- `student_knowledge_mastery_history`：可选/必要的变化历史，用于趋势和审计；
- `mastery_processed_answer` 或等价幂等记录：保证事件只处理一次。

不得只把掌握度放Redis而没有数据库事实。

### 6. 推荐依赖“Mastery + Published Graph + 可用ExerciseUnit”

推荐引擎分三步：

```text
Candidate generation
  -> Safety/business filter
  -> Deterministic ranking + explanation
```

候选来源：

1. 当前掌握度低于阈值的 KnowledgePoint；
2. Published Graph 中这些薄弱点的未掌握 prerequisite；
3. 对应 ACTIVE 且有平台 Question 的 ExerciseUnit。

V0.4 默认弱点阈值：`0.70`，作为版本化规则配置，不硬编码散落在业务代码中。

### 7. 推荐优先级

优先级从高到低：

1. 当前目标知识点的未掌握前置知识；
2. 已发生错误且 mastery 较低的知识点；
3. mastery 较低但近期练习不足的知识点；
4. 已掌握知识点的巩固练习。

V0.4 不实现协同过滤、LLM排序或复杂学习排序模型。

### 8. 学习路径是约束排序，不是生成式文本

给定目标 KnowledgePoint：

1. 从当前 Published Graph 获取 prerequisite ancestors；
2. 去掉 mastery >= threshold 的节点；
3. 在 DAG 中做拓扑排序；
4. 输出未掌握节点的有序路径；
5. 每个节点关联可练习 ExerciseUnit。

若图版本不存在、路径不存在或节点不在当前 Published Graph，返回明确业务状态，不伪造路径。

### 9. 所有推荐与路径必须可解释

每个 Recommendation 至少记录：

- `reason_code`
- `knowledge_point_id`
- `mastery_score`
- `graph_version_id`
- `rule_version`
- `evidence`（例如最近错误数、是否 prerequisite）

前端解释示例：

> “一次方程当前掌握度 0.46，且它是函数的前置知识，因此优先推荐复习。”

不需要LLM生成解释。

### 10. 模型以后如何接入

MODEL-0R通过后，新模型只能通过 `ModelMasteryProvider` 接入。

必须：

- 绑定 ModelVersion / DatasetVersion / Knowledge Schema；
- 通过 ModelScopeValidator；
- 输出映射到平台 KnowledgePoint；
- 不适用或服务失败时回退 RuleBasedMasteryProvider；
- 推荐与路径层不感知具体模型实现。

## 结果

V0.4 可以独立完成完整个性化闭环：

```text
Answer
 -> Outbox
 -> Rule Mastery
 -> Weak Knowledge
 -> Published Graph
 -> Recommendation / Learning Path
 -> Explanation
```

同时保留后续模型替换能力，而不把尚未通过MODEL-0R的算法写死进业务系统。