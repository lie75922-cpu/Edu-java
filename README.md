# Edu-java

**基于知识图谱与个性化学习分析的 Java 智能教学平台**

> 当前状态：V0.7 工程底座已达到 Release Candidate，但产品层正在按原《基于〈离散数学〉数字教材的知识图谱建设与应用》申报书重新对齐。工程底座（Spring Boot / MySQL / Neo4j / 权限 / Outbox / 图谱版本治理 / 推荐与学习路径 / 教师学情 / Docker / E2E）保留；课程内容、信息架构、前端视觉、角色门户和教学资源体系进入产品重构阶段。

## 当前必须区分的两条线

- **工程底座**：V0.7 已完成，可复现全栈、权限、可靠性、图谱治理、RuleBeta 掌握度、可解释推荐、学习路径、教师分析、备份恢复和浏览器 E2E 均保留。
- **产品形态**：当前 V0.7 Demo 不作为最终产品验收形态。后续将按申报书重构为中文的《离散数学》教学优化平台，核心为知识图谱可视化、教学资源整合与管理、个性化学习推荐、多元化教学评估四大模块。

## 产品重构目标

最终平台围绕《离散数学》组织，不再以 Junyi Demo 课程作为前台主产品。课程知识体系按：

```text
离散数学
├─ 集合论与关系
├─ 数理逻辑
├─ 图论
├─ 代数结构
└─ 组合数学/扩展内容
```

并进一步细分为“章 → 节 → 知识集 → 知识点”，知识点节点可关联教材、课件、视频、动画、案例、练习题、测试题和代码/虚拟实验等资源。

前台全部使用自然中文名称；开发/研究术语（KnowledgePoint、ExerciseUnit、GraphVersion、Evidence、RuleBeta 等）仅保留在技术文档与运维/高级治理界面，不直接暴露给普通学生和教师。

## 已保留的工程能力

- Java 21 + Spring Boot + Spring Security + JWT + RBAC
- MySQL 8.4 权威业务存储；Neo4j 为已发布知识图查询投影；Redis 为可选缓存
- 课程、知识点、练习、题目、答题记录
- AnswerRecord 幂等与 Outbox
- Evidence → Draft → GraphValidator → Published Graph → Neo4j 版本化发布
- RuleBeta 知识点掌握度（无历史为“未知”，不伪造 0.5）
- 可解释推荐与基于先修 DAG 的学习路径
- 教师课程授权与学情分析
- Docker Compose、Flyway V001–V006、OpenAPI、健康检查、Playwright E2E、备份恢复

## 科研数据边界

Junyi 研究数据继续作为算法实验数据，不再作为最终中文前台课程内容：

- 247,606 名匿名学习者
- 25,925,992 条交互
- 835 个不同 Exercise external ID
- 8 Area / 40 Topic
- DATA-0 固定先修证据图 scope：815 Exercise 节点 / 979 条 raw prerequisite Evidence

最终业务平台的《离散数学》知识图谱将依据数字教材、课程结构和教学资源重新建设，不能把 Junyi 的 815 Exercise 节点冒充离散数学知识点。

## 模型研究最终结论

MODEL-3 在唯一一次独立 5,000 人 final holdout 上得到：

- ExerciseRate AUC 0.707210
- Rasch/IRT-1PL AUC 0.724675
- Rasch 相对 ExerciseRate：AUC +0.017465，95% CI [0.012394, 0.022665]
- Rasch + student-Topic deviation AUC 0.710015，显著弱于 Rasch

因此接受 `GO_RASCH_ONLY_INTEGRATION`，但 Rasch 只表示全局 Student Ability / response-prediction signal，不等于 KnowledgePoint Mastery。当前生产 Mastery 仍由 RuleBeta 负责。

## 下一阶段

产品层按申报书重构，重点包括：

1. 全中文化；
2. 学生端、教师端、管理员端分离的信息架构；
3. 离散数学真实课程知识体系和教学资源；
4. 高质量知识图谱可视化与知识点详情；
5. 学生画像、学习进度、薄弱点、推荐路径、资源推荐；
6. 教师资源管理、备课支持、过程性/诊断性/结果性评价；
7. 管理端按用户/课程/资源/知识图谱/系统设置拆分，不再所有功能堆在一个页面；
8. 保留当前工程可靠性与测试能力，不重复造底座。
