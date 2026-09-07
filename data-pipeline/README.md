# DATA Pipeline

该目录属于 **Research Data Domain**，用于 Junyi 等公开教育数据的离线审计、清洗、采样和模型输入生成。

## 原则

- 原始数据放在 `.data/raw/`，不得提交 Git；
- 模型代码只依赖 Canonical Schema，不直接依赖原始列名；
- DATA-0 冻结前不在代码里声称最终学生数、Exercise数或交互数；
- Junyi 数据仅用于非商业研究/学习/求职Demo，并保留来源说明。

## DATA-0 审计入口

在获得合法的本地 Junyi 文件后，先安装开发依赖，再从本目录运行：

    python -m edu_data.data0 --metadata-dir <metadata-directory> --problem-log <junyi-problem-log.csv> --reference-root .data/reference --reports-dir reports --generated-root .data/generated --work-dir .data/work/data0 --manifest-path manifests/junyi_mid_v1.json --schema-path schemas/canonical-v1.json

该入口审计真实 CSV Schema、匿名交互、原始 prerequisite 文本、关系图、
Small/Medium/Large 候选集和学生级 train/valid/test 划分。原始数据、可复现
成员清单与派生 Canonical 行都位于 .data 下，不提交 Git。

数据来源不能直接验证为原始发布方时，必须标记为第三方来源，不能称为
PRIMARY。仓库规则禁止内容摘要，因此来源证据使用显式 URI、路径、文件大小、
Schema、样例和固定 seed；这一差异必须在 Final Gate 中如实保留。

DATA-0 的输出是独立审查所需证据，不会冻结 V0.2 Schema，也不会自动进入
V0.2。
