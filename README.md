# Edu-java

基于知识图谱与图认知诊断的个性化学习平台。

> 当前阶段：DATA-0 数据审计与 V0.1 工程骨架。

本仓库遵循“先数据事实、再系统设计、后编码实现”的开发顺序。Junyi 等公开教育数据只用于离线研究/模型实验，不直接冒充平台真实用户或完整在线题库；平台业务数据与科研数据严格分域。

## 当前技术基线

- Java 21
- Spring Boot 4.1.x
- Spring Data JPA
- Spring Data Neo4j
- Spring Data Redis
- Flyway
- MySQL 8
- Redis 7
- Neo4j
- Python + FastAPI（模型服务，V0.1仅骨架）
- Vue 3（V0.1仅骨架）

## 文档

- `docs/DATA-0_IMPLEMENTATION.md`：DATA-0 数据获取、清洗、抽样、审计与冻结方案
- `docs/V0.1_SKELETON_DESIGN.md`：V0.1 工程骨架设计
- `docs/ADR/0001-data-domain-boundary.md`：科研数据域与平台业务域边界

## 开发原则

1. 不根据“理想字段”反推数据，任何模型输入必须来自实际可获得字段或平台真实运行数据。
2. MySQL 是在线业务事实的权威数据源；Neo4j 是已发布知识图的查询投影。
3. 认知诊断与推荐解耦；模型异常不能阻断登录、课程、答题等核心业务。
4. 模型必须绑定数据版本、知识Schema/图版本和适用范围；不对未见知识体系伪装泛化。
5. Junyi 原始数据明确限制商业使用，本项目仅按研究/学习/求职Demo口径使用并保留来源与许可说明。

