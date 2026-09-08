// 版本化科研快照：数据来自仓库内 DATA-0 / MODEL-3 已冻结报告。
// 这里只用于产品评审中解释“数据从哪里来、做过什么分析、算法结论是什么”。
// 它不代表平台业务用户实时统计，也不得替代后续正式的数据治理 API。
export const researchSnapshot = Object.freeze({
  dataset: {
    id: 'JUNYI_DATA0',
    displayName: 'Junyi 数学学习行为研究数据',
    provenance: '第三方镜像输入；官方主数据源未在当前执行环境直接取得，来源边界已登记',
    students: 247606,
    interactions: 25925992,
    metadataRows: 837,
    distinctExerciseIds: 835,
    topics: 40,
    areas: 8,
    correctRate: 0.827874,
    rawPrerequisiteEdges: 980,
    duplicateExerciseIdRecords: 2,
    missingTopicRows: 20,
    missingAreaRows: 20,
    selfLoops: 2,
    cyclicSccs: 3,
    boundary: '科研匿名学生与平台业务用户严格隔离；原始英文数据不篡改，中文仅作为产品展示映射。'
  },
  pipeline: [
    '数据源登记与来源边界审计',
    '字段结构与模式检查',
    '缺失、重复与时间字段质量检查',
    '学习行为与学生序列探索性分析',
    '知识关系自环、环路与连通性审计',
    '学生级数据划分与模型输入构造',
    '模型对照、消融与独立留出验证'
  ],
  model: {
    experiment: 'MODEL-3',
    baselineName: '题目历史正确率基线',
    baselineAuc: 0.707210,
    raschName: 'Rasch / IRT-1PL',
    raschAuc: 0.724675,
    raschAcc: 0.826387,
    raschRmse: 0.362658,
    aucDelta: 0.017465,
    aucCiLow: 0.012394,
    aucCiHigh: 0.022665,
    hierarchicalName: '分层 Rasch + 主题偏差',
    hierarchicalAuc: 0.710015,
    gate: 'GO_RASCH_ONLY_INTEGRATION',
    productionBoundary: '当前 Java 个性化推荐仍使用 RuleBeta + 已发布知识图谱；Rasch 尚未接入生产推荐，不做虚假归因。'
  }
})
