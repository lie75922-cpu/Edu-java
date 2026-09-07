# Edu-java

基于知识图谱与图认知诊断的个性化学习平台。

> 当前阶段：**DATA-0 数据审计 + V0.1 工程骨架**。

本仓库遵循“先数据事实、再系统设计、后编码实现”的开发顺序。Junyi 等公开教育数据只用于离线研究/模型实验，不直接冒充平台真实用户或完整在线题库；平台业务数据与科研数据严格分域。

## 当前技术基线

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA / Neo4j / Redis
- Flyway
- MySQL 8.4
- Redis 7.4
- Neo4j 2026.07.1 Community（开发环境）
- Python + FastAPI（V0.1仅健康服务）
- Vue 3.5.42 + Vite 8.2.2（V0.1仅工程页）

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

## 文档

- `docs/DATA-0_IMPLEMENTATION.md`：DATA-0 数据获取、清洗、抽样、审计与冻结方案
- `docs/V0.1_SKELETON_DESIGN.md`：V0.1 工程骨架设计
- `docs/ADR/0001-data-domain-boundary.md`：科研数据域与平台业务域边界

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

## 当前验证状态

| 项目 | 状态 | 说明 |
|---|---|---|
| DATA-0 Canonical Schema测试 | ✅ 2/2 | 已在当前执行环境真实运行 |
| FastAPI健康测试 | ✅ 1/1 | 已在当前执行环境真实运行 |
| YAML/JSON静态解析 | ✅ | Compose YAML与前端package.json已解析 |
| Java Maven测试 | CI验证中 | 当前执行容器无Maven，不冒充已通过 |
| Vue build | CI验证中 | 当前执行容器无法访问npm网络 |
| Docker Compose启动 | CI/开发机验证 | 当前执行容器无Docker |

GitHub Actions会在push后执行后端、Python、前端和Compose配置检查。

## 开发原则

1. 不根据“理想字段”反推数据，任何模型输入必须来自实际可获得字段或平台真实运行数据。
2. MySQL 是在线业务事实的权威数据源；Neo4j 是已发布知识图的查询投影。
3. 认知诊断与推荐解耦；模型异常不能阻断登录、课程、答题等核心业务。
4. 模型必须绑定数据版本、知识Schema/图版本和适用范围；不对未见知识体系伪装泛化。
5. Junyi 原始数据明确限制商业使用，本项目仅按研究/学习/求职Demo口径使用并保留来源与许可说明。
6. DATA-0完成前，不提交原始大数据、不声称最终数据规模、不把Exercise伪装成完整Question题库。

## 下一阶段

1. 完成DATA-0真实数据获取和EDA；
2. 冻结`JUNYI_MID` Dataset Manifest；
3. 根据真实联结率确定V0.2的Course / ExerciseUnit / Question / KnowledgePoint详细Schema；
4. 再开始业务CRUD和答题闭环，不提前扩张Agent、微服务、Kafka等非核心技术。
