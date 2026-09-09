# 演示指南：离散数学智慧教学平台

## 1. 演示边界

当前演示数据全部为 Platform Business Domain 合成数据，仅在 `APP_DEMO_SEED_ENABLED=true` 时启用。

**不会读取、复制或展示 Junyi 匿名科研学生身份。** Junyi 继续只服务于 MODEL-0A～MODEL-3 的科研实验。

所有演示账号共享本地演示密码：

`LocalDemoOnly!2026`

该密码仅用于本地/评审环境，不是公开部署凭据。

| 用户名 | 角色 | 中文身份 | 主要课程 |
| --- | --- | --- | --- |
| `demo-admin` | SYSTEM_ADMIN | 系统管理员 | 全平台管理 |
| `demo-teacher-a` | TEACHER | 张老师 | 离散数学、数理逻辑专题、集合与关系专题 |
| `demo-teacher-b` | TEACHER | 李老师 | 图论专题训练 |
| `demo-student-alice` | STUDENT | 陈晓同学 | 离散数学及专题训练 |
| `demo-student-bob` | STUDENT | 王晨同学 | 离散数学 |
| `demo-student-carol` | STUDENT | 刘悦同学 | 离散数学 |
| `demo-student-dave` | STUDENT | 赵宁同学 | 图论专题训练 |

## 2. 《离散数学》主课程内容

主课程编码：`DM-101`

课程目前覆盖四个一级模块：

1. 第一章 数理逻辑
   - 命题与逻辑联结词
   - 命题等值演算
   - 析取范式与合取范式
   - 命题逻辑推理理论
2. 第二章 集合论与关系
   - 集合与集合运算
   - 二元关系及其性质
   - 等价关系与划分
   - 偏序关系与哈斯图
3. 第三章 图论
   - 图的基本概念
   - 路径、回路与连通性
   - 欧拉图与欧拉回路
   - 树与生成树
4. 第四章 代数结构
   - 代数系统与运算
   - 群与子群
   - 环与域
   - 格与布尔代数

演示基线：

- 16 个核心知识点；
- 16 个练习单元；
- 16 道中文选择题；
- 12 条已审核先修关系；
- 一张真实 Published Graph；
- 一次受控错误作答，用于生成薄弱知识、推荐和学习路径上下文。

## 3. 启动

按照 [DEPLOYMENT.md](DEPLOYMENT.md) 生成 `.env.release`，然后启动：

```powershell
.\scripts\generate-local-release-env.ps1 -Path .env.release
docker compose --env-file .env.release -f docker-compose.full.yml up --build -d
```

前端：`http://localhost:8081/`

后端：`http://localhost:8080/`

## 4. 学生演示

使用 `demo-student-alice` 登录。

建议演示顺序：

1. **首页**：查看课程、已有学习记录、薄弱知识和下一步学习建议；
2. **课程学习**：进入《离散数学》，按数理逻辑、集合论与关系、图论、代数结构浏览知识目录；
3. 在“命题与逻辑联结词”点击 **开始练习**，提交答案并查看结果；
4. **个性化学习**：查看薄弱知识、更新学习建议；
5. 选择“命题逻辑推理理论”为目标，点击 **生成学习路径**；
6. **知识图谱**：查看整门课程知识关系，点击节点查看前置/后继知识。

学生页面不再展示 `KnowledgePoint`、`ExerciseUnit`、`GraphVersion`、`RuleBeta`、`UNKNOWN` 等研发术语。

## 5. 教师演示

使用 `demo-teacher-a` 登录。

1. 进入 **教师工作台**；
2. 选择《离散数学》；
3. 查看在读学生、近期活动、作答次数和正确率；
4. 查看 **班级知识掌握概览**；
5. 查看 **学生 × 知识点掌握情况**；
6. 点击学生姓名打开 **学生学情详情**；
7. 查看高频错误题与学生当前学习建议；
8. 使用接口直接访问李老师的“图论专题训练”，必须返回 HTTP 403。

## 6. 管理员演示

使用 `demo-admin` 登录。

当前 V1 预览将管理端拆分为独立业务模块，不再使用超长管理页：

- **课程与教学**：查看课程、知识点和练习规模，新建课程；
- **教师授权**：为课程分配课程负责人/授课教师；
- **知识图谱治理**：查看版本、关系数、校验问题，创建草稿、运行校验、发布图谱；
- **系统状态**：查看 V0.7 工程底座构成。

教学资源中心、完整章节/节/知识集模型和多元评价管理仍需要下一阶段后端资源模型扩展后接入。

## 7. 自动验收

```powershell
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run smoke
docker compose --env-file .env.release -f docker-compose.full.yml --profile test run --rm e2e npm run e2e
```

当前 E2E 必须覆盖：

- 中文学生课程、练习、推荐、路径、知识图谱；
- 中文教师学情与学生下钻；
- Teacher A → Course B 跨课程 403；
- 中文模块化管理员工作台。
