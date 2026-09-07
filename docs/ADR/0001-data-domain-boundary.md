# ADR-0001：科研数据域与平台业务域分离

- 状态：Accepted
- 日期：2026-09-07

## 背景

Junyi 等教育数据集非常适合训练认知诊断模型和验证知识关系，但其匿名学生、Exercise日志和研究字段并不等同于一个真实在线学习产品的数据模型。

如果将科研数据直接塞入平台用户、题库、答题表，会产生以下问题：

1. 匿名数据被误解为真实平台用户；
2. Exercise被错误当成完整Question；
3. 训练字段污染在线业务Schema；
4. 模型对特定Exercise集合的适用范围被隐藏；
5. 未来替换数据集时业务库被迫重构；
6. 非商业数据许可边界难以控制。

## 决策

系统永久区分两类数据域。

### Research Data Domain

保存于 `data-pipeline/` 与模型实验目录。

负责：

- 原始数据；
- Canonical Dataset；
- train/valid/test；
- EDA；
- 模型训练；
- checkpoint；
- Dataset Manifest。

研究用户ID不进入 `sys_user`。

### Platform Business Domain

保存于MySQL/Neo4j/Redis。

负责：

- 注册用户；
- 教师/教学班；
- Course；
- ExerciseUnit；
- Question；
- 平台答题事实；
- Mastery Snapshot；
- Recommendation。

## 映射规则

允许的映射：

```text
Research Exercise
      ↓ explicit mapping
Platform ExerciseUnit
```

禁止的隐式映射：

```text
Research Student → Platform User
```

平台Question必须是独立业务实体；不能因为研究数据中存在Exercise就假设已经拥有完整题干、选项和解析。

## 图数据主权

MySQL保存知识关系的版本、审核、状态和审计，是业务权威数据源。

Neo4j只保存PUBLISHED图版本的查询投影，用于邻域、多跳、路径和可视化。

## 模型范围

Model Version必须绑定：

- Dataset Version；
- Knowledge/Graph Schema Version；
- 支持的Exercise/Knowledge scope。

新Exercise不在模型scope时必须使用fallback，不允许伪装泛化。

## 后果

### 正面

- 数据集可替换；
- 业务库稳定；
- 模型作用范围可追溯；
- 许可边界清晰；
- 避免研究Demo与产品数据混淆。

### 代价

- 需要额外的Adapter和映射表；
- Offline/Online各维护一套ID空间；
- 模型部署需要scope校验。

这些成本是可接受的，且明显低于后期整体重构成本。
