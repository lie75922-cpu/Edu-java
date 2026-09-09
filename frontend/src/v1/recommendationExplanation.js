const fallbackReasons = {
  UNMET_PREREQUISITE: '目标知识的前置内容尚未巩固',
  LOW_MASTERY: '已有学习记录显示当前掌握程度偏低',
  RECENT_ERRORS: '近期在相关练习中出现错误',
  REVIEW_DUE: '距离上次练习时间较长，建议复习',
  TARGET_PRACTICE: '目标知识需要进一步练习'
}

function parseExplanation(value) {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function numberOrNull(value) {
  if (value === null || value === undefined || value === '') return null
  return Number.isFinite(Number(value)) ? Number(value) : null
}

/**
 * The backend deliberately keeps recommendation evidence as explanationJson.
 * This adapter only renders fields actually present in that payload; it never
 * infers question content, import state, or recommendation evidence.
 */
export function recommendationExplanation(item) {
  const evidence = parseExplanation(item?.explanationJson)
  return {
    message: typeof evidence?.message === 'string' && evidence.message.trim()
      ? evidence.message
      : fallbackReasons[item?.reasonCode] || '系统未提供更具体的推荐说明。',
    masteryScore: numberOrNull(evidence?.masteryScore),
    masteryStatus: typeof evidence?.masteryStatus === 'string' ? evidence.masteryStatus : null,
    attemptCount: numberOrNull(evidence?.attemptCount),
    correctCount: numberOrNull(evidence?.correctCount),
    recentErrorCount: numberOrNull(evidence?.recentErrorCount),
    unmetPrerequisite: evidence?.unmetPrerequisite === true,
    ruleVersion: typeof evidence?.ruleVersion === 'string' ? evidence.ruleVersion : null,
    graphVersionId: numberOrNull(evidence?.graphVersionId)
  }
}
