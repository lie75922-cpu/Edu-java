# ADR-0011：注册用户与历史学习者统一到 Learner Profile 层

## 状态

`PROPOSED_FOR_V1_5`

## 背景

Junyi 数据包含 247,606 个匿名 learner 和 25,925,992 条历史学习行为。当前 Java 在线链以 `sys_user` 作为登录账号，并将 `answer_record` 作为平台实时答题事件。

如果完全把历史 learner 隔离在研究目录，平台无法充分利用真实用户规模做学情统计和冷启动；如果直接把匿名 learner 伪造成可登录账号并把历史 ProblemLog 写成 AnswerRecord，又会破坏账号、Question、幂等事件和数据来源语义。

## 决策

引入“账号”和“学习者”分层：

```text
PlatformAccount (sys_user)
        │ optional 1:1
        ▼
LearnerProfile
        ▲
        │
HistoricalImportedLearner
```

LearnerProfile 是统一的学习主体。来源至少包含：

- `REGISTERED_REALTIME`：平台注册/演示账号；
- `IMPORTED_HISTORICAL`：数据集匿名 learner。

历史 learner 不创建可登录密码/JWT；若业务上确实需要落到 `sys_user`，必须是不可登录状态并保留 `user_origin=IMPORTED_HISTORICAL`，但首选方案是不让账号表承担历史画像职责。

## 历史行为存储

不把 25.9M ProblemLog 逐条伪造成 `answer_record`。Python 侧先按 learner/topic/exercise 聚合并导出：

- learner summary；
- learner-topic attempts/correct/correct-rate/last-observed；
- 可选 learner-exercise summary；
- course/topic popularity；
- 数据版本与 provenance。

Java 侧建议表：

- `learner_profile`；
- `historical_learner_topic_stat`；
- `historical_learner_activity_summary`；
- `historical_profile_import_run`。

在线 `answer_record` 保持不变。

## 推荐融合

推荐信号优先级：

1. 当前用户实时 AnswerRecord / RuleBeta；
2. 当前用户历史导入画像（若存在映射）；
3. 历史全体 learner 的 Topic popularity / segment prior；
4. 课程稳定顺序冷启动。

群体先验不能覆盖个人真实行为，也不能把 UNKNOWN 伪造为 0.5 mastery。

## 教师分析

教师工作台可以分别展示：

- 在线注册学生；
- 历史导入学习者样本/总体统计；
- 来源标签。

不能把匿名历史 learner 展示成有姓名的真实学生。

## 面试口径

实现完成后可表述为：

> 平台同时支持实时注册用户和约 24.7 万历史导入学习者。历史 learner 以匿名画像进入学习者层，用于群体学情、冷启动和离线推荐；登录账号、实时 AnswerRecord 与历史日志在事件层分离，但统一到 Learner Profile 进行分析。

实现完成前只能说“设计并准备接入”，不能说已经上线。
