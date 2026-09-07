# Research Exercise Catalog 验收记录

该功能用于研究域 Exercise 目录验证，不是完整题库、答题闭环或认知诊断系统。

## 1. Codex原始PoC本地验证（Schema v1历史记录）

Codex原始交付曾在其Windows真实环境完成以下验证：

- 后端Surefire：8项测试，失败/错误/跳过均0；
- 目录导出测试：4项通过；
- 浏览器打开 `http://127.0.0.1:5173/`，页面控件正常渲染；
- 使用临时Basic凭据经前端 `/api` 代理请求真实Java服务：总数837；
- 首页20项，第二页从记录21开始；
- 搜索 `matrix_mul_two` 返回3项；
- area/topic相关筛选曾验证 `algebra` 结果258项；
- 不存在搜索词返回0项；
- 无认证返回401；`size=101`返回400。

该记录证明原PoC链路可以工作，但不能作为DATA-0行为数据、图数据或模型准备度的证据。

## 2. ChatGPT评审后的Schema v2变化

评审分支将目录契约升级为v2：

- 目录文件默认位置：`data-pipeline/data/processed/junyi_catalog_v2.json`；
- 输出必须包含provenance和源文件SHA256；
- 每条记录保留 `prerequisiteRaw`；
- Java端验证raw/parsed prerequisite一致性；
- Java端按文件mtime+size缓存目录；
- PowerShell脚本改用仓库相对路径和 `JAVA_HOME/PATH`，不再绑定某台开发机；
- 启动脚本要求先生成可执行jar：在 `backend/` 执行 `mvn --batch-mode package`。

Schema v2代码必须通过PR CI后才视为代码层验收通过；真实837条目录的v2重新导出与本机浏览器回归测试交由Codex在真实环境补做。

## 3. 当前本地启动方式（评审版本）

1. 使用审计过的CSV重新导出v2目录；
2. 在 `backend/` 执行：

```text
mvn --batch-mode package
```

3. 在PowerShell中设置：

```text
EDU_RESEARCH_BASIC_PASSWORD
```

4. 运行：

```text
backend/scripts/start-research-catalog-local.ps1
```

脚本默认只监听 `127.0.0.1:8080`，并通过参数排除本地研究目录PoC暂不需要的数据库自动配置。

## 4. 仍未验收的事项

- v2真实837条目录重新导出后的浏览器点击回归；
- MySQL/Redis/Neo4j完整容器集成；
- 生产部署；
- 正式账号体系；
- DATA-0完整数据审计；
- 答题—诊断—推荐闭环。

## 5. 合并约束

即使Schema v2 PR CI全部通过，也只能说明该PoC代码质量达到可审查状态。是否进入main，要等DATA-0确认Exercise/Topic/Area的正式领域抽象后再决定。
