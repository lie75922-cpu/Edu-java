# Edu-java

基于知识图谱与图认知诊断的个性化学习平台。

> 当前阶段：**DATA-0 数据审计 + V0.1 工程骨架**。

本仓库遵循“先数据事实、再系统设计、后编码实现”的开发顺序。Junyi 等公开教育数据只用于离线研究/模型实验，不直接冒充平台真实用户或完整在线题库；平台业务数据与科研数据严格分域。

## 实验性功能：Research Exercise Catalog

当前评审分支保留一个真实 Junyi Exercise 元数据目录 PoC，用于验证研究数据经受控投影后能否被 Java API 与 Vue 页面稳定读取。它具备搜索、topic/area筛选和分页能力，但有明确边界：

- **不是 DATA-0 完成标志**；
- **不是平台 Question 题库**，不包含稳定题干、选项、答案和解析；
- **不是 KnowledgePoint 的最终定义**；
- **不代表答题—诊断—推荐闭环已经实现**；
- 在 DATA-0 完成 prerequisite 语义审计之前，目录中的 `prerequisites` 只能视为源字段的解析视图，不能直接发布为 Neo4j `PREREQUISITE` 关系。

目录 Schema v2 要求保存来源分类、来源标签、原文件名、原文件 SHA256、可选来源 URI/获取时间以及转换说明；每条 Exercise 同时保存 `prerequisiteRaw` 与 `prerequisites`，避免镜像/预处理数据覆盖原始语义。

接口：

```text
GET /api/v1/research/exercises
```

支持：`page`、`size`、`q`、`topic`、`area`。

数据导出与来源契约见 `data-pipeline/README.md`。

## 当前技术基线

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA / Neo4j / Redis
- Flyway
- MySQL 8.4
- Redis 7.4
- Neo4j 2026.07.1 Community（开发环境）
- Python + FastAPI（V0.1仅健康服务）
- Vue 3.5.42 + Vite 8.2.2

## 仓库结构

```text
Edu-java/
├── backend/         # Spring Boot 在线业务主系统
├── frontend/        # Vue 前端
├── model-service/   # 独立 Python 模型服务
├── data-pipeline/   # Research Data Domain / DATA-0
├── docs/            # 软件工程与架构文档
└── docker-compose.yml
```

## 设计文档

- `docs/DATA-0_IMPLEMENTATION.md`：DATA-0 数据获取、清洗、抽样、审计与冻结方案
- `docs/V0.1_SKELETON_DESIGN.md`：V0.1 工程骨架设计
- `docs/ADR/0001-data-domain-boundary.md`：科研数据域与平台业务域边界
- `docs/DATA0_CODEX_HANDOFF.md`：Codex 后续真实环境 DATA-0 执行与回传规范

## 本地启动顺序

### 1. 基础设施

```bash
cp .env.example .env
docker compose up -d
```

### 2. Java 后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/v1/system/health
GET http://localhost:8080/actuator/health
```

### 3. 模型服务

```bash
cd model-service
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

V0.1只返回真实服务状态，不提供伪造模型预测：

```text
GET http://localhost:8001/health
```

### 4. 前端

```bash
cd frontend
npm install
npm run dev
```

### 5. DATA-0 单元测试

```bash
cd data-pipeline
pip install -e '.[dev]'
python -m pytest -q
```

## main 基线验证状态

| 项目 | 状态 | 说明 |
|---|---|---|
| DATA-0 Canonical Schema测试 | ✅ | main 已通过 |
| FastAPI健康测试 | ✅ | main 已通过 |
| Java Maven测试 | ✅ | main 的 GitHub Actions 使用 Java 21 编译并测试通过 |
| Vue build | ✅ | main 的 GitHub Actions 已通过 |
| Docker Compose配置 | ✅ | main 的 GitHub Actions `docker compose config` 已通过 |
| 完整基础设施容器启动 | 待开发机烟测 | 不把未运行的容器集成测试冒充已通过 |

评审分支中的 Research Exercise Catalog 修改必须通过 PR CI 后才能考虑合并；在 DATA-0 Gate 完成前，是否合并该实验功能由后续架构审查决定。

## 开发原则

1. 不根据“理想字段”反推数据，任何模型输入必须来自实际可获得字段或平台真实运行数据。
2. MySQL 是在线业务事实的权威数据源；Neo4j 是已发布知识图的查询投影。
3. 认知诊断与推荐解耦；模型异常不能阻断登录、课程、答题等核心业务。
4. 模型必须绑定数据版本、知识Schema/图版本和适用范围；不对未见知识体系伪装泛化。
5. Junyi 原始数据明确限制商业使用，本项目仅按研究/学习/求职Demo口径使用并保留来源与许可说明。
6. DATA-0完成前，不提交原始大数据、不声称最终数据规模、不把Exercise伪装成完整Question题库。
7. 镜像、作者预处理数据和第三方处理数据必须在 Manifest 中保留独立 provenance，禁止统一标成“官方原始数据”。
8. Test split 冻结后不得因模型结果好坏重新划分。

## 下一阶段：DATA-0 Gate

下一阶段只做真实数据审计和数据基线冻结：

1. 核实 Junyi 原始数据与当前镜像/处理副本的 provenance；
2. 完成 Exercise、ProblemLog、relationship annotation 的真实 Schema 审计；
3. 完成学生行为 EDA、时间质量、序列长度和数据质量统计；
4. 分别审计 Exercise prerequisite、relationship annotation、RCD `K_Directed` 的语义；
5. 比较 Exercise / Topic / Area 在领域模型中的三种抽象；
6. 形成 Small / Medium / Large 三档可重复子集；
7. 评估 NCDM、RCD、ORCDF、GEAR-CD 输入准备度与成本；
8. 生成 Dataset Manifest、Bad Cases、Cost Report 与最终 `GO / CONDITIONAL_GO / NO_GO`；
9. DATA-0完成后停止，由 ChatGPT/Owner 重新冻结 V0.2 设计。
