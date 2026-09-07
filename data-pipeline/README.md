# DATA Pipeline

该目录属于 **Research Data Domain**，用于 Junyi 等公开教育数据的离线审计、清洗、采样和模型输入生成。

## 原则

- 原始数据放在 `.data/raw/`，不得提交 Git；
- 模型代码只依赖 Canonical Schema，不直接依赖原始列名；
- DATA-0 冻结前不在代码里声称最终学生数、Exercise数或交互数；
- Junyi 数据仅用于非商业研究/学习/求职Demo，并保留来源说明。

## V0.1

当前只提供 Canonical Schema 和 Junyi Adapter 接口骨架。真实解析逻辑在 DATA-0 获得并审计原始数据后实现。
