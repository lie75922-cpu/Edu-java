# Edu-java 面试口径与系统说明（当前真实实现）

> 本文是当前项目的面试、简历和项目汇报统一口径。若旧文档与本文冲突，以本文和 `docs/V1_MATH_PLATFORM_BASELINE.md` 为准。
>
> 最重要的原则：只讲已经实现或已经验证的事实；计划中的 V1.5 “历史学习用户接入”必须在实现完成后才能表述为已上线。

## 1. 项目一句话定位

这是一个**基于真实数学学习数据的中文智能学习平台**：前端提供学生学习、教师学情、题库和管理员治理；后端使用 Spring Boot + MySQL + Neo4j + Redis；数据侧使用 Python 对真实历史学习数据做清洗、领域建模和离线评测；推荐采用可解释的常规规则算法，安全知识图谱存在时再作为增强信号。

## 2. 真实数据规模

当前已审计的数据包括：

- 247,606 名匿名历史学习者；
- 25,925,992 条历史学习行为；
- 837 行 Exercise metadata；
- 835 个不同 Exercise external ID；
- 8 个原始 Area，其中 biology 为跨学科数据并隔离；
- 40 个原始 Topic；
- 803 个 Exercise 通过 Foundation 业务候选审计；
- 实际 Java 业务导入形成 1 门数学课程、7 个数学领域、39 个 Topic 级学习单元、802 个 ExerciseUnit；32 条隔离、3 条冲突保留并失败关闭。

### 用户口径

项目中应区分两个层级：

1. **账号（Account）**：能够登录、注册、获得 JWT 的 `sys_user`；
2. **学习者（Learner）**：拥有学习行为、知识状态和推荐上下文的学习主体。

当前 Java 生产系统的在线 Learner 与 `sys_user` 一一对应；247,606 个数据集 learner 目前仍主要用于离线分析和模型/推荐评测。

计划中的 V1.5 将把匿名 learner ID 以**历史导入学习者（IMPORTED_HISTORICAL）**方式纳入平台学习者层，用于学情统计、画像和推荐初始化，但不会为这些匿名 learner 创建可登录凭据。完成该实现后，可以在面试中说“平台同时支持注册用户和历史导入学习者”；在实现完成前，不应说“247,606 个用户已经作为在线账号导入系统”。

## 3. 为什么没有把 25,925,992 条历史记录直接写进 `answer_record`

原因不是数据不能用，而是**在线答题事件和历史离线日志语义不同**。

`answer_record` 是平台在线事件：

- 由具体登录用户产生；
- 绑定平台 Question；
- 有幂等 `clientRequestId`；
- 进入在线 Mastery 更新、Outbox、推荐链。

历史数据中的交互是匿名数据集事件：

- 题目对象主要是 Exercise metadata；
- 没有平台 Question 语义；
- 没有平台登录账号；
- 不应伪造成在线用户主动提交的 AnswerRecord。

因此正确做法是：

```text
Historical ProblemLog
        ↓
Python batch aggregation
        ↓
Historical Learner / Learner-Topic Stats
        ↓
统一 Learner Profile
        ↓
教师分析 / 冷启动先验 / 离线评测
```

而不是：

```text
25.9M logs → 假装成 25.9M 在线 AnswerRecord
```

## 4. 课程与领域模型怎么建立

Junyi metadata 自带结构化字段：`area`、`topic`、`name`、`prerequisites` 等。

V1 业务投影是：

```text
Area        → KnowledgeArea
Topic       → KnowledgePoint（业务学习单元）
Exercise    → ExerciseUnit
Question    → 仅平台自有或有权使用的具体题目
```

这里的 `Topic → KnowledgePoint` 只是**V1 业务层可解释投影**，不声称 Topic 一定等于教育学上的最小知识点。

真实原始英文值不覆盖，中文名称通过 display mapping 提供；raw/display/provenance 分离，以便追溯。

## 5. 知识图谱怎么建立

真实图不是用 LLM 从教材文本中抽出来的，而是基于**结构化 prerequisite 字段**构建。

流程：

```text
Junyi Exercise metadata
        ↓
读取 prerequisites 字段
        ↓
raw prerequisite token
        ↓
Exercise external ID 唯一解析
        ↓
Exercise A → Exercise B
        ↓
映射两个 Exercise 所属 Topic
        ↓
Topic A → Topic B
        ↓
去掉同 Topic 投影 + Topic pair 去重
        ↓
Candidate Relation
        ↓
Evidence → Candidate → GraphValidator
        ↓
只有校验通过才能 Published → Neo4j
```

### 真实图规模

原始 prerequisite 关系：

- 988 个 raw prerequisite token；
- 980 个可唯一解析 Exercise 关系；
- 979 条不同 Exercise 边；
- 833 个原始 Exercise 图节点；
- 2 个 self-loop；
- 3 个 cyclic SCC。

投影到 Topic 层后：

- 39 个 Topic candidate 节点；
- 78 条 candidate 边；
- 0 个 self-loop；
- 4 个 cyclic SCC。

因此**39 节点 / 78 边是真实 candidate graph，不是已发布知识图谱**。

## 6. 真实知识图谱能不能展示

可以展示两类内容：

1. **真实课程目录结构**：39 个 Topic 及其 Area/Exercise 层级可以正常展示；
2. **正式先修知识图**：当前真实 Topic candidate graph 因 4 个 cyclic SCC 被 `GraphValidator` 阻断，因此没有作为 Published Graph 投影到 Neo4j，也不应在学生端冒充正式图谱。

系统已有完整的图展示能力；合成 Demo Published Graph 可以证明 Neo4j 查询、前端可视化、路径查询的工程链可用。但它不能替代真实 Junyi 图语义证据。

## 7. 怎么判断“抽取是否准确”

项目没有把“能解析到 ID”冒充“关系语义准确率”。

### 第一层：身份解析质量

988 个 raw prerequisite token 中，980 个可以唯一解析到 Exercise 起点和终点：

```text
980 / 988 ≈ 99.19%
```

这叫**identity resolution coverage**，不是知识关系 Precision。

### 第二层：结构有效性

发布前 GraphValidator 会检查：

- missing source/target；
- cross-course edge；
- inactive node；
- self-loop；
- duplicate edge；
- directed cycle；
- no evidence warning；
- no exercise coverage warning。

真实 Topic candidate graph 就是在这一层被发现有 4 个 cyclic SCC，因此 fail-closed。

### 第三层：语义有效性

V2 对 4 个 SCC 逐个回溯 Exercise 支撑边，结论为：

- 3 个 SCC 主要是 `PROJECTION_ARTIFACT`；
- 1 个是 `COARSE_TOPIC_SEMANTICS`。

即 Exercise 层的合理关系投影到较粗 Topic 后产生双向依赖。结论是：**Topic 适合做学习状态/导航层，但不适合直接承担严格 prerequisite DAG。**

目前没有人工专家 gold set，因此不能写“关系准确率 xx%”。如以后要正式给出知识关系 Precision/Recall，需要独立专家标注样本。

## 8. 为什么还需要知识图谱

知识图谱不是为了“技术看起来高级”，而是解决推荐里的**结构约束和解释问题**。

普通推荐能回答：

> 学生现在更需要复习什么？

知识图谱希望进一步回答：

> 这个知识点为什么学不会？是否因为某个前置知识没有掌握？应该先补什么？

因此图谱主要承担：

- prerequisite / successor 显式关系；
- 学习路径；
- 推荐解释；
- 发布治理和版本管理。

当前设计把图谱作为**可选增强层**：没有安全 Published Graph 时，常规推荐照常运行；只有安全图存在时才增加 prerequisite 约束。

## 9. 当前生产推荐算法是什么

当前生产并不是协同过滤、GNN、NCDM 或 Rasch 推荐，而是：

> **基于学习状态的 Rule-based / Content-based Ranking**。

Mastery 使用透明 Beta(1,1) 平滑：

```text
mastery = (correct_count + 1) / (attempt_count + 2)
```

算法版本：`RULE_BETA_1_1_V1`。

没有历史的 Topic 保持 `UNKNOWN`，绝不解释为“50%掌握”。

推荐规则版本：`REC_RULE_V1`。

核心参数：

- weak mastery threshold = 0.70；
- recent error window = 30 days；
- review due after = 7 days；
- max recommendations = 5。

排序逻辑：

```text
若有安全图：未满足 prerequisite 优先
        ↓
掌握度更低优先
        ↓
近期错误更多优先
        ↓
距离上次练习更久优先
        ↓
稳定 ID 排序保证可复现
```

没有 Published Graph 时第一项直接取消，其余规则仍正常运行。

## 10. 为什么选常规规则推荐

当前阶段选择规则推荐而不是更复杂模型，原因有四点：

1. **可解释**：每条推荐能说明是低掌握、近期错误、到期复习还是安全先修关系；
2. **冷启动友好**：不依赖大规模在线用户-物品矩阵；
3. **数据语义匹配**：在线系统当前真实交互量还不适合直接做协同过滤；
4. **研究结果支持**：复杂 CDM 虽有预测能力，但 Topic mastery 表示出现塌缩；Rasch 更像全局能力信号，不适合冒充 Topic mastery。

## 11. 冷启动怎么做

### 新注册用户完全没有历史

不伪造 mastery，不使用 0.5 先验；按课程稳定顺序选前 5 个学习单元作为入门建议。

### 有少量学习历史

优先使用该用户**已经观察过**的 Topic；如果没有明显低于 0.70 的弱项，则从已学内容中按掌握度最低和复习间隔排序。

### V1.5 历史学习者接入后的冷启动增强

计划增加两层先验，但必须保持可解释：

- course/topic popularity prior：来自历史 247,606 learners 的统计；
- learner-segment prior：仅用于排序 tie-break 或新用户初始建议，不覆盖个人真实 AnswerRecord。

一旦新用户产生平台答题记录，个人行为信号优先级高于群体先验。

## 12. 推荐离线评测目前怎么解释

原 M1 有 UNKNOWN 语义 bug：未观察 Topic 被 Beta(1,1) 先验当成 0.5 weakness。V2 修复后只对 OBSERVED Topic 排序。

394 个固定时间切分离线 case：

| offline proxy | Popular | Corrected Mastery |
| --- | ---: | ---: |
| Future Topic Hit@3 | 0.690355 | 0.878173 |
| Future Error Topic Hit@3 | 0.617080 | 0.760331 |
| Popularity-adjusted Recall | 0.376121 | 0.500041 |

这些只能说明个体历史状态对未来行为/未来错误有较强离线关联，**不能写成学习效果提升**。

## 13. 面试时如何解释“25 万用户为什么没有全部登录账号”

推荐回答：

> 数据集里的 24.7 万用户是真实历史学习者，但它们只有匿名 learner ID，没有平台账号信息。我把“账号”和“学习者”两个概念拆开：注册用户有账号、JWT 和实时 AnswerRecord；历史学习者不创建可登录凭据，而是作为 imported learner profile 进入分析和推荐层。这样既能利用真实用户行为，又不会伪造账号语义。后续新注册用户和历史导入用户在 Learner Profile 层统一，个人实时行为始终优先于群体历史先验。

## 14. 面试高频追问与回答要点

### Q1：为什么用 Neo4j？MySQL 不行吗？

MySQL 负责 Evidence、Candidate、GraphVersion、审核和业务权威数据；Neo4j只保存通过校验的 Published Graph，用于 predecessor/successor/path 等关系查询。不是为了把所有数据都换成图数据库。

### Q2：为什么真实图没发布？

因为 39 节点、78 边的 Topic candidate graph 有 4 个循环 SCC。回溯后发现主要是粒度投影伪影，所以系统选择阻断发布，而不是为让页面好看强删边。

### Q3：那知识图谱是不是没用？

不是。工程治理链、版本管理、Neo4j 投影和查询能力都已经跑通；真实数据验证反而证明了“不是任何关系都能直接当知识图”。当前推荐不依赖图，安全图未来可以作为增强层。

### Q4：为什么不直接上协同过滤？

因为当前线上注册用户交互还不足，而 25.9M 历史日志是匿名 learner 数据。先用可解释规则算法保证在线可用；历史 learner 接入后可以作为 popularity/segment prior，但不能把匿名日志硬伪造成在线用户行为。

### Q5：为什么不用 NCDM？

NCDM 在响应预测上有一定提升，但 Topic mastery 表示出现明显塌缩，学生间 mastery vector 高度相似。预测准不等于诊断可信，所以没有为了模型复杂度强行上线。

## 15. 当前简历可以安全写什么

可以写：

- 对 247,606 名匿名历史学习者、25,925,992 条学习行为和 837 条 Exercise metadata 完成数据审计；
- 建立真实数学目录 Area→Topic→Exercise，并导入 7 个数学领域、39 个学习单元和 802 个 ExerciseUnit；
- 构建 Evidence→Candidate→Validator→Published Graph 图谱治理链，真实 Topic candidate graph 39 节点/78 边，检测 4 个 cyclic SCC 后阻断发布；
- 实现 RuleBeta + 近期错误 + 复习间隔的常规个性化推荐和冷启动；
- 构建学生/教师/管理员多角色平台、题库、学习报告、数据治理、Docker/CI/E2E。

当前不能写：

- 247,606 个用户已经全部作为可登录平台账号；
- 真实 39 节点/78 边图已经发布到 Neo4j；
- 知识图谱抽取准确率 99%；
- 推荐让学习成绩提升 xx%；
- Rasch/NCDM 已用于生产推荐。
