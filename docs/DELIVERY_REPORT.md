# Research Exercise Catalog PoC 交付与评审报告

## 1. 交付定位

本次交付来源于 `codex/junyi-catalog-20260907`，属于 **Research Exercise Catalog PoC**，用于验证真实 Junyi Exercise 元数据经研究域投影后，能否被 Java API 和 Vue 前端稳定查询。

该交付 **不等于 DATA-0 完成**，也不代表平台已具备完整题库、答题、认知诊断或推荐能力。

## 2. Codex原始交付内容

1. Python CLI读取真实Junyi Exercise元数据并生成只读目录JSON；
2. Java研究目录API支持认证、分页、名称搜索、topic/area精确筛选；
3. Vue目录页面支持搜索、筛选、分页和错误状态；
4. 本地研究启动脚本支持无数据库联调；
5. 原始交付报告记录837行、835个不同外部名称、4行重复external ID；
6. 原始交付记录后端8项测试、目录导出4项测试通过，并完成真实API与前端代理联调。

这些结果是原始PoC的执行记录，不应扩展解释为 DATA-0 的行为日志、知识图或模型准备度结论。

## 3. ChatGPT独立评审发现

评审确认该PoC具有工程价值，但发现以下需要修正的问题：

### R-01 数据来源过于笼统

原实现将来源固定写成 `Junyi via USTC mirror`，不能证明具体仓库、文件、commit/URL和本地源文件SHA256。

### R-02 prerequisite语义过早规范化

原实现仅保存逗号split后的 `prerequisites`，可能掩盖镜像字段与Junyi原始字段之间的差异。

### R-03 运行脚本绑定单机绝对路径

原PowerShell脚本硬编码 `D:/Code/java/...` 和本机JDK路径，不具备可移植性。

### R-04 Java查询每次重新读取并解析目录JSON

对837条记录可以工作，但不是合理的长期读取方式。

### R-05 文档出现未经Owner确认的方向变化

原README出现“用户已调整为功能优先推进”等表述，与DATA-0 Gate不一致，已删除。

## 4. 评审分支修正

评审分支：

```text
chatgpt/review-junyi-catalog-20260907
```

修正内容：

1. Catalog升级为Schema v2；
2. 新增 `provenance`，保存：
   - `sourceType`
   - `sourceLabel`
   - `sourceUri`
   - `sourceFileName`
   - `sourceSha256`
   - `acquiredAt`
   - `transformation`
3. 每条Exercise同时保存 `prerequisiteRaw` 与 `prerequisites`；
4. Java端验证source type、SHA256和raw/parsed prerequisite一致性；
5. Java端增加按文件mtime+size失效的目录缓存，避免每次请求重复解析；
6. `application.yml`改为跨环境相对默认路径；
7. PowerShell启动脚本改为从 `JAVA_HOME/PATH` 与仓库相对路径解析，不再绑定开发机绝对路径；
8. README恢复DATA-0为强制Gate；
9. 新增 `docs/DATA0_CODEX_HANDOFF.md`，明确后续Codex真实DATA-0工作包。

## 5. 当前验收状态

### 可接受

- 研究目录PoC代码可保留；
- 重复ID不擅自合并的策略正确；
- Research Data Domain 与 Platform Business Domain 边界保持清晰；
- Catalog不得冒充Question/KnowledgePoint的口径正确。

### 尚不能接受为DATA-0完成

仍缺少：

- Junyi主数据provenance闭环；
- ProblemLog真实Schema；
- Student Interaction EDA；
- Sequence分布；
- relationship annotation审计；
- prerequisite / RCD关系语义比较；
- Graph Audit；
- Exercise/Topic/Area领域抽象比较；
- Small/Medium/Large采样；
- Dataset Manifest；
- NCDM/RCD/ORCDF/GEAR-CD readiness；
- 成本报告；
- Bad Cases；
- Final Gate。

## 6. 合并策略

在DATA-0完成以前，该PoC不应因“功能已经能跑”直接合并main。

正确流程：

1. 评审分支先通过PR CI；
2. DATA-0由Codex从main另建 `codex/data0-junyi-*` 分支执行；
3. DATA-0完成后由ChatGPT重新冻结ExerciseUnit/KnowledgePoint定义；
4. 再决定该目录PoC是直接合并、重构后cherry-pick，还是废弃。

这样避免实验功能反向绑架正式领域模型。
