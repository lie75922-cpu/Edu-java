<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, percent, run, selectedCourse } from '../store.js'

const courses = ref([])
const mastery = ref([])
const masteryHistory = ref([])
const answerHistory = ref([])

const courseAnswers = computed(() => {
  const courseId = selectedCourse.value?.id
  if (!courseId) return []
  return answerHistory.value
    .filter(item => Number(item.courseId) === Number(courseId))
    .sort((a, b) => new Date(b.answeredAt || 0) - new Date(a.answeredAt || 0))
})

const observedItems = computed(() => mastery.value.filter(item => item.status === 'OBSERVED' && item.masteryScore !== null))
const weakItems = computed(() => observedItems.value
  .filter(item => Number(item.masteryScore) < 0.7)
  .sort((a, b) => Number(a.masteryScore) - Number(b.masteryScore)))
const masteredItems = computed(() => observedItems.value.filter(item => Number(item.masteryScore) >= 0.7))
const attemptCount = computed(() => courseAnswers.value.length)
const correctCount = computed(() => courseAnswers.value.filter(item => item.correct).length)
const correctRate = computed(() => attemptCount.value ? correctCount.value / attemptCount.value : null)
const meanMastery = computed(() => {
  if (!observedItems.value.length) return null
  return observedItems.value.reduce((sum, item) => sum + Number(item.masteryScore), 0) / observedItems.value.length
})
const activeDays = computed(() => new Set(courseAnswers.value
  .map(item => item.answeredAt ? new Date(item.answeredAt).toISOString().slice(0, 10) : null)
  .filter(Boolean)).size)
const latestActivity = computed(() => courseAnswers.value[0]?.answeredAt || null)
const recentMasteryChanges = computed(() => masteryHistory.value.slice(0, 8))

function normalizeCourse(item) {
  return {
    ...item,
    id: item.id ?? item.courseId,
    courseName: item.courseName,
    courseCode: item.courseCode
  }
}

function formatTime(value) {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date)
}

function formatDuration(value) {
  if (value === null || value === undefined) return '未记录'
  const seconds = Math.round(Number(value) / 1000)
  if (!Number.isFinite(seconds)) return '未记录'
  if (seconds < 60) return `${seconds} 秒`
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

function scoreDelta(item) {
  if (item.newScore === null || item.newScore === undefined) return null
  if (item.previousScore === null || item.previousScore === undefined) return Number(item.newScore)
  return Number(item.newScore) - Number(item.previousScore)
}

function deltaText(item) {
  const delta = scoreDelta(item)
  if (delta === null || Number.isNaN(delta)) return '—'
  const points = Math.abs(delta * 100).toFixed(1)
  if (delta > 0) return `+${points} 个百分点`
  if (delta < 0) return `-${points} 个百分点`
  return '无变化'
}

async function load(course = null) {
  if (course) selectedCourse.value = course
  if (!selectedCourse.value) return
  const courseId = selectedCourse.value.id
  const [masteryResult, historyResult, answersResult] = await Promise.all([
    api(`/courses/${courseId}/mastery`).catch(() => ({ items: [] })),
    api(`/courses/${courseId}/mastery/history`).catch(() => []),
    api('/learning/answer-history').catch(() => [])
  ])
  mastery.value = masteryResult?.items || []
  masteryHistory.value = historyResult || []
  answerHistory.value = answersResult || []
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result.map(normalizeCourse)
  if (!selectedCourse.value || !courses.value.some(item => Number(item.id) === Number(selectedCourse.value.id))) {
    selectedCourse.value = courses.value[0] || null
  }
  await load()
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div>
        <p class="eyebrow">学习分析</p>
        <h2>我的学习报告</h2>
        <p>基于平台真实答题记录与学习状态生成，不使用科研匿名日志，也不补造缺失学习数据。</p>
      </div>
      <div class="intro-actions">
        <select :value="selectedCourse?.id" @change="load(courses.find(item => Number(item.id) === Number($event.target.value)))">
          <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option>
        </select>
      </div>
    </div>

    <div class="metric-grid four">
      <article class="metric-card accent-blue"><span>累计作答</span><strong>{{ attemptCount }}</strong><small>次</small></article>
      <article class="metric-card accent-green"><span>答题正确率</span><strong>{{ percent(correctRate) }}</strong><small>{{ correctCount }} 次答对</small></article>
      <article class="metric-card accent-purple"><span>平均掌握情况</span><strong>{{ percent(meanMastery) }}</strong><small>{{ observedItems.length }} 个已有学习记录</small></article>
      <article class="metric-card accent-orange"><span>薄弱知识</span><strong>{{ weakItems.length }}</strong><small>{{ masteredItems.length }} 个达到 70%</small></article>
    </div>

    <div class="two-column">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">重点复习</p><h3>当前薄弱知识</h3></div><span class="soft-badge">阈值 70%</span></div>
        <div v-if="weakItems.length" class="mastery-list">
          <article v-for="item in weakItems.slice(0, 6)" :key="item.knowledgePointId">
            <div class="mastery-title"><strong>{{ item.knowledgeName }}</strong><b>{{ percent(item.masteryScore) }}</b></div>
            <div class="progress large"><i :style="{ width: `${Math.round(Number(item.masteryScore) * 100)}%` }"></i></div>
            <p>累计作答 {{ item.attemptCount }} 次，答对 {{ item.correctCount }} 次。</p>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>暂无明确薄弱知识</strong><p>完成更多平台练习后，这里会展示需要优先复习的内容。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">学习概况</p><h3>活跃与进度</h3></div></div>
        <div class="metric-grid two">
          <article class="metric-card"><span>学习活跃天数</span><strong>{{ activeDays }}</strong><small>按实际答题日期统计</small></article>
          <article class="metric-card"><span>最近学习</span><strong style="font-size:18px">{{ formatTime(latestActivity) }}</strong><small>最后一次平台答题</small></article>
        </div>
        <div class="detail-block">
          <strong>知识状态覆盖</strong>
          <p class="muted">已记录 {{ observedItems.length }} 个知识点，其中 {{ masteredItems.length }} 个达到 70%，{{ weakItems.length }} 个仍需巩固；其余知识点保持“暂无学习数据”，不会换算成虚假分数。</p>
        </div>
      </section>
    </div>

    <div class="two-column">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">变化记录</p><h3>最近掌握度更新</h3></div></div>
        <div v-if="recentMasteryChanges.length" class="teacher-point-list">
          <article v-for="item in recentMasteryChanges" :key="item.id || item.historyId">
            <div><strong>{{ item.knowledgeName }}</strong><span>{{ formatTime(item.createdAt) }} · 第 {{ item.newAttemptCount }} 次累计作答</span></div>
            <div class="teacher-point-score"><b>{{ percent(item.newScore) }}</b><small>{{ deltaText(item) }}</small></div>
            <div class="progress"><i :style="{ width: `${Math.round(Number(item.newScore || 0) * 100)}%` }"></i></div>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>暂无掌握度变化记录</strong><p>完成题目后，学习状态的变化会记录在这里。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">答题记录</p><h3>最近练习</h3></div><span class="soft-badge">最近 {{ Math.min(courseAnswers.length, 8) }} 条</span></div>
        <div v-if="courseAnswers.length" class="error-question-list">
          <article v-for="item in courseAnswers.slice(0, 8)" :key="item.answerRecordId">
            <div>
              <strong>练习单元 #{{ item.exerciseUnitId }}</strong>
              <span>{{ formatTime(item.answeredAt) }} · 第 {{ item.attemptNo }} 次作答 · {{ formatDuration(item.durationMs) }}</span>
            </div>
            <b :style="{ color: item.correct ? '#2d9560' : '#bd5a43' }">{{ item.correct ? '正确' : '错误' }}</b>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>暂无答题记录</strong><p>从“课程学习”完成平台题目后，答题结果会出现在这里。</p></div>
      </section>
    </div>
  </section>
</template>
