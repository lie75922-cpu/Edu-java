# ADR-0009：教师课程授权与学情分析边界

- 状态：Accepted
- 决策日期：2026-09-08
- 适用阶段：V0.6 及后续教师端

## 1. 背景

V0.2 已建立 `course_enrollment`，学生只能访问自己处于 ACTIVE enrollment 的课程；但当前 `CourseAccessService` 对 `SYSTEM_ADMIN`、`TEACHER`、`TEACH_ADMIN` 直接放行。

这在早期单课程开发阶段便于测试，但在 V0.6 开始展示学生答题、掌握度与推荐等个人学习信息后，不再符合最小权限原则：

```text
任意 TEACHER
  -> 任意 Course
  -> 该 Course 下全部学生学情
```

独立代码审查还确认，现有 `AdminCourseController`、`AdminQuestionController`、`AdminExerciseUnitController`、`AdminKnowledgeController`、`AdminGraphController` 等控制器普遍以 `SYSTEM_ADMIN | TEACHER` 角色作为入口，但尚未统一落实 Course 级归属检查。这意味着只新增“教师大屏权限”仍不足以真正解决越权问题。

因此 V0.6 必须先建立显式 Teacher-Course assignment，并统一收紧已有课程内容/图谱治理接口，再开放班级与学生明细。

## 2. 决策

### 2.1 新增 CourseTeacherAssignment

V0.6 新增业务关系：

```text
sys_user(teacher)
   └─< course_teacher_assignment >─ course
```

最低字段：

- `teacher_id`
- `course_id`
- `assignment_role`：`OWNER` / `INSTRUCTOR`
- `status`：`ACTIVE` / `INACTIVE`
- `assigned_by`
- `assigned_at`
- `created_at`
- `updated_at`

唯一约束：`teacher_id + course_id`。

数据库外键只保证用户存在；应用层必须验证被分配用户具有 `TEACHER` 或允许的教学角色，不能把普通 STUDENT 静默升级成教师。

V0.6 中 `OWNER` / `INSTRUCTOR` 先作为可审计教学关系与UI信息；普通教师的实际课程级内容/分析访问均要求 ACTIVE assignment。课程生命周期仍由平台教学管理员控制，不在本阶段人为创造复杂 OWNER 特权矩阵。

### 2.2 权限矩阵

#### SYSTEM_ADMIN

- 可访问所有课程；
- 可创建/修改/停用 Course；
- 可管理 Teacher-Course assignment；
- 可管理全部课程教学内容与图谱；
- 可查看全部学情。

#### TEACH_ADMIN

当前系统没有组织/院系 tenant 边界，因此 V0.6 将其视为平台级教学管理员：

- 可访问所有课程；
- 可创建/修改/停用 Course；
- 可管理 Teacher-Course assignment；
- 可管理全部课程教学内容与图谱；
- 可查看全部学情。

若未来增加组织域，再单独收紧，不在 V0.6 假造不存在的 tenant。

#### TEACHER

仅可访问存在 ACTIVE `course_teacher_assignment` 的课程。

包括：

- 已分配课程内容教学视图与维护；
- 已分配课程的 KnowledgePoint / ExerciseUnit / Question 维护；
- 已分配课程的 GraphVersion / Evidence 治理；
- 已分配课程的学生学情；
- 已分配课程的学生答题/掌握度/推荐历史。

普通 TEACHER **不能**：

- 任意创建平台 Course；
- 修改/停用未分配或任意 Course；
- 自行给自己分配 Course；
- 因为拥有 TEACHER 角色绕过课程归属。

#### STUDENT

继续只通过 ACTIVE `course_enrollment` 访问自己的课程及自己的学习数据。

学生不能访问教师聚合接口或其他学生信息。

## 3. 统一 Course Access Contract

V0.6 不允许继续依赖“controller 上有角色注解所以 service 不检查课程”的做法。

统一提供语义明确的方法，例如：

```text
requireCourseReadAccess(courseId, currentUser)
requireTeachingAccess(courseId, currentUser)
requirePlatformTeachingAdmin(currentUser)
```

或等价设计。

要求：

1. student path 检查 enrollment；
2. teacher path 检查 assignment；
3. TEACH_ADMIN / SYSTEM_ADMIN 按上述平台级权限；
4. GraphVersion ID、Evidence ID、KnowledgePoint ID、ExerciseUnit ID、Question ID 等“间接课程资源”必须先解析所属 course，再做授权；
5. 不能仅靠前端隐藏菜单；
6. 不能仅靠 `@PreAuthorize(hasRole('TEACHER'))` 作为课程级授权；
7. controller 的角色注解与 service 的 course-level authorization 必须同时存在，前者负责角色粗筛，后者负责资源归属。

## 4. 现有 Admin API 的安全收紧

### 4.1 Course lifecycle

当前 `AdminCourseController` 对普通 TEACHER 开放 create/update/disable。

V0.6 固定改为：

```text
Course create/update/disable
  -> SYSTEM_ADMIN / TEACH_ADMIN only
```

普通 TEACHER 不获得平台 Course 生命周期管理权。

### 4.2 Course content

现有 Question / ExerciseUnit / Knowledge 等管理API如果继续允许 TEACHER，必须按资源所属 Course 做 ACTIVE assignment 校验。

例如：

```text
questionId
 -> ExerciseUnit
 -> courseId
 -> teacher assignment
```

以及：

```text
knowledgePointId -> courseId -> assignment
exerciseUnitId   -> courseId -> assignment
```

不得出现“有 TEACHER 角色即可改任意课程题库”的情况。

### 4.3 Graph / Evidence governance

当前图治理控制器允许 TEACHER 角色进入，但多个 API 以 `graphVersionId` 或 `evidenceId` 为入口。

V0.6 必须回归检查并收紧：

- create/list graph version；
- relation add/review/reject；
- Evidence import；
- Evidence re-resolution；
- validation/publish/retry；
- Evidence/history/conflict 查询。

对于 TEACHER：

```text
resource -> courseId -> ACTIVE teacher assignment -> allow
```

否则 403。

TEACH_ADMIN 必须按当前平台级教学管理员语义正确纳入允许角色，不能因旧注解遗漏而被意外拒绝。

这是 V0.6 的安全修复，不改变 Course、Question 或 Graph lifecycle 业务语义。

## 5. Teacher Analytics 数据权威

教师学情全部来自 Platform Business Domain：

- `course_enrollment`
- `answer_record`
- `question`
- `exercise_unit`
- `exercise_knowledge`
- `student_knowledge_mastery`
- `student_knowledge_mastery_history`
- `recommendation_snapshot`
- `recommendation_item`
- 当前 Published Graph metadata（只用于展示版本/知识结构）

禁止将 Junyi Research Student / ProblemLog 直接作为“班级学生”展示。

## 6. 统计语义

### 6.1 时间窗口

答题类统计必须接受显式 `from` / `to` 或使用受控默认窗口。

建议：

- 默认最近 30 天；
- 最大窗口 365 天；
- 时间统一按 UTC 存储/查询；
- API 响应返回实际 resolved window。

### 6.2 Mastery

教师端展示的 mastery 必须保留：

- `status=UNKNOWN/OBSERVED`
- `sourceType`
- `algorithmVersion`
- `attemptCount`
- `masteryScore`（UNKNOWN 为 null）

不得把 UNKNOWN 当 0，也不得把规则 mastery 冒充 AI 模型结果。

### 6.3 正确率

所有正确率必须同时返回分母，例如：

```text
correctCount
attemptCount
correctRate
```

`attemptCount=0` 时 rate 为 null，而不是 0%。

### 6.4 Weak KnowledgePoint

V0.6 可沿用 V0.4 当前规则阈值（如 mastery < 0.70）进行“弱项”展示，但必须：

- 标明 mastery algorithm/version；
- 只统计 OBSERVED mastery；
- UNKNOWN 单独计数；
- 不把阈值描述为教育学通用标准。

## 7. 不新增第二套 Analytics 真相

V0.6 第一版采用数据库聚合查询/服务层聚合，不新增 Kafka、OLAP、ES、数据仓库或定时物化大宽表。

理由：

- 当前项目目标为中等规模求职项目；
- 业务规模尚无真实生产负载证据；
- MySQL 已有权威数据，且可通过 V006 补充必要索引。

只有实际性能测试证明查询瓶颈后才允许增加汇总表/缓存。

## 8. V006 索引策略

允许 V006 增加支持教师查询的索引，例如：

```text
answer_record(course_id, answered_at, id)
answer_record(course_id, question_id, answered_at)
```

以及经过 EXPLAIN / 测试证明有价值的最小索引。

禁止为了“看起来专业”无依据堆大量索引。

## 9. Teacher Analytics 输出边界

V0.6 支持：

- assigned course list；
- course overview；
- KnowledgePoint mastery summary；
- student × KnowledgePoint heatmap（分页/受控规模）；
- high-error questions；
- student detail；
- student recent activity / mastery history / latest recommendation context。

不做：

- 自动给学生贴“差生/风险学生”标签；
- LLM生成学情结论；
- 自动干预/通知；
- 教师聊天Agent；
- 跨组织比较排名；
- 大屏炫技图表优先于数据语义。

## 10. 结果

V0.6 的目标不是多做几个图表，而是建立：

```text
Teacher authorization
  + auditable business metrics
  + drill-down student evidence
  + no cross-course leakage
```

这使平台第一次具备可用于教师教学决策的安全读模型，同时保持所有指标可以追溯到业务事实表。
