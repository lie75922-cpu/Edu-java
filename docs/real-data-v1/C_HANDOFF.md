# C：前端真实数据产品接入交接

## 结论与范围

当前前端阶段 Gate：`GO_FRONTEND_REAL_DATA_V1`。

本阶段从包含 Foundation 与 Backend 的本地后端分支继续，只改动 `frontend/**` 与本文档；没有改动 backend、data-pipeline、model-service、e2e、Docker 或 CI。提交前的实际前端入口仍为 `frontend/src/main.js` → `V1App.vue`。

该 Gate 仅说明前端静态接入、中文展示审查和生产构建通过；它不证明真实 Java 数据库导入已经执行，也不证明全栈浏览器流程已通过。

## 已接入的实际 API

| 产品区域 | 实际接口 | 前端行为 |
| --- | --- | --- |
| 课程目录 | `GET /courses`、`GET /courses/{courseId}`、`GET /courses/{courseId}/knowledge-areas`、`GET /courses/{courseId}/knowledge-points` | 按后端返回的课程、知识领域和知识点中文名称显示；没有预置课程、节点或章节。 |
| 练习元数据 | `GET /exercise-units?courseId={courseId}` | 显示关联练习元数据及其目录状态。该 DTO 未提供题目可用性，因此不会把练习元数据展示为题干、选项或可作答题目。 |
| 学习状态与推荐 | `GET /courses/{courseId}/mastery`、`GET /courses/{courseId}/recommendations/latest`、`POST /courses/{courseId}/recommendations`、`GET /knowledge-points/{knowledgePointId}/learning-path` | 只解析后端 `explanationJson` 中真实提供的推荐说明、掌握情况、作答数、近期错误、规则版本和知识关系版本；字段缺失时不补造。 |
| 已发布知识关系 | `GET /courses/{courseId}/graph`、`GET /knowledge-points/{knowledgePointId}/prerequisites`、`GET /knowledge-points/{knowledgePointId}/successors` | 学生图谱支持课程切换、知识领域筛选、搜索、节点聚焦及前置/后继查看；只使用已发布关系查询，不显示候选关系为已发布图谱。 |
| 数据治理 | `GET /admin/data-governance/overview`、`GET /admin/data-governance/import-runs` | 系统管理员查看真实持久化计数和 ImportRun 审计字段。没有导入批次时明确显示“尚无导入批次”，不会用 Foundation 预期规模替代。 |
| 候选关系治理 | `GET /admin/graph-versions`、`GET /admin/graph-versions/{graphVersionId}/relations`、`GET /admin/graph-versions/{graphVersionId}/validation-issues` | 管理端以中文解释候选输入状态、发布状态和关系来源，并保留审计用原始状态或校验代码。 |

## 数据与产品边界

Foundation 的目录投影输入与数据库实际导入结果不是同一件事。后端交接中记录的输入规模是 8 个 Area、40 个 Topic、837 条练习元数据，其中 803 条符合目录导入条件、34 条隔离；另有 988 条原始先修证据和 78 条候选关系。这些是输入/预期处理边界，**不是**本机真实数据库已导入的事实。

当前候选关系图包含环，后端要求其发布状态保持受阻，直至人工审查完成。学生端因此只展示后端已发布查询的结果；候选关系只在管理员治理界面以“待人工审核、尚未发布”的中文状态呈现。

原始证据、策略候选关系、已发布知识关系分别展示和审计。研究学生、研究交互数据以及任何题干、选项、答案、解析均不进入这次目录前端展示。

## 中文与固定数据审查

* 学生和教师模板不直接渲染 `UNKNOWN`、`UNMET_PREREQUISITE`、`KnowledgePoint`、`ExerciseUnit` 或 `GraphVersion`；状态和推荐原因以中文展示。
* 管理端可以为审计显示来源名称、输入路径、格式版本、候选状态和校验代码，并在相邻位置提供中文字段说明或状态解释。
* 已移除旧的静态科研快照模块。V1 产品入口没有固定课程、知识点、题目、候选图规模或真实导入计数。
* 练习目录页面明确标出“题目内容尚未由目录数据提供”，避免以元数据伪造 Question 或题目可用状态。

## 验证

前端环境未提供 `npm` 可执行文件，因此请求的 `npm install --no-package-lock --no-audit --no-fund` 无法启动。使用桌面环境已提供的 `pnpm` 作为替代验证，执行 `pnpm install --lockfile=false --ignore-scripts` 后运行 `pnpm run build`，生产构建通过。

未完成的 E2E 验证：

* 以 SYSTEM_ADMIN 身份加载数据治理总览、导入批次与候选关系状态；
* 在真实 Java 数据库完成 dry-run、apply 和再次 apply 后，核对管理端的实际 ImportRun；
* 在有已发布知识关系的课程上验证学生图谱筛选、前置/后继和学习路径；
* 在实际推荐快照存在时验证 `explanationJson` 的每个可选字段；
* 在有独立题库内容的练习上验证题库流程。目录导入本身不会创建题目。

这些项未被标为通过：B 阶段已说明本机 Docker daemon 不可用，真实 Java 数据库 dry-run/apply/再次 apply 尚未执行。
