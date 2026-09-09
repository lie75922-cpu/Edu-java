# F 阶段交接

## 完成范围

分支：`codex/foundation-real-data-v1`

已新增 deterministic business export 实现 `data-pipeline/src/edu_data/business_export.py` 及其 pytest 覆盖。实际运行会在本机生成 `data-pipeline/business_export/`；该目录含需保留的 raw source value，因此不纳入源代码提交。没有读取或扫描完整 ProblemLog；没有改动 `backend`、`frontend`、`model-service`、`e2e`、compose 或原工作树 `D:\Code\java\Edu-java-submit`。

## 实际结果

* 8 Area、40 Topic、837 metadata 行、835 个不同 Exercise external ID。
* 803 个 Exercise 可作为业务导入候选；4 行重复 identity、18 行缺分类、12 行 biology 跨学科项均隔离。
* 8+40 条 Area/Topic 中文 display mapping；830 行源提供的 Exercise 中文 display，7 行缺失且未臆造。
* 988 条 raw prerequisite token 全量保留；980 条唯一可解析的 Exercise 边进入原始拓扑统计。
* 原始 Exercise 图：833 节点、979 条不同边、2 个 self-loop、3 个 cyclic SCC。
* Topic 投影：823 条同 Topic 折叠、156 条跨 Topic 输入边、78 条不同 candidate pair、9 条未解析；candidate graph 为 39 节点、78 边、0 self-loop、4 个 cyclic SCC。

## 验证

指定 Python 初始没有 pytest runner；首次使用默认 Windows 临时目录启动 pytest 时，在 fixture 创建前遭遇 `WinError 5`。该结果不是测试通过，也不是实现断言失败。

随后将 pytest 安装到 worktree 外临时目录，并使用同样 worktree 外的专用 `--basetemp D:\Code\java\_tmp_f_pytest_20260909` 重跑：

```text
16 passed in 0.19s
```

该专用 base temp 已移除。还用指定解释器完成实际 export 和 `python -m compileall -q src\edu_data`。临时 pytest 依赖也会在 F 结束前移除，不写入 `pyproject.toml`、lockfile 或工作树。

## Gate 与下一步

`GO_FOUNDATION_V1` 成立，因为目录投影、中文 Area/Topic display、确定性 export、quarantine 和 raw/candidate 分离均有真实数据与测试证据。

该 Gate **不**等价于 `Published Graph` 可发布：4 个 candidate cyclic SCC 是明确的图发布阻塞。只有取得此 Gate 后，协调方才可按既定顺序启动 B 和 R；F 本身不启动、修改或代替这些阶段。

后续 B 必须遵守 [BUSINESS_EXPORT_CONTRACT.md](BUSINESS_EXPORT_CONTRACT.md) 和 [API_REQUIREMENTS.md](API_REQUIREMENTS.md)，尤其是 raw Evidence 与 candidate relation 分离、ImportRun 幂等、display 更新、quarantine 与发布前 cycle 检查。
