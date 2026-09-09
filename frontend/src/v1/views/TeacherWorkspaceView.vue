<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, percent, run } from '../store.js'

const courses = ref([])
const selectedCourseId = ref('')
const overview = ref(null)
const pointAnalytics = ref([])
const heatmap = ref(null)
const highErrors = ref([])
const student = ref(null)
const page = ref(0)
const size = 20
const minimumAttempts = ref(1)

const selectedCourse = computed(() => courses.value.find(item => String(item.courseId) === String(selectedCourseId.value)))
const weakPointCount = computed(() => pointAnalytics.value.filter(item => Number(item.meanMastery) < 0.7 && item.meanMastery !== null).length)

async function loadCourses() {
  const result = await run(() => api('/teacher/courses'))
  if (!result) return
  courses.value = result
  if (!result.some(item => String(item.courseId) === String(selectedCourseId.value))) {
    selectedCourseId.value = result[0] ? String(result[0].courseId) : ''
  }
}

async function loadAnalytics() {
  if (!selectedCourseId.value) return
  const id = Number(selectedCourseId.value)
  const params = new URLSearchParams({ minimumAttempts: String(minimumAttempts.value || 1) })
  const loaded = await run(() => Promise.all([
    api(`/teacher/courses/${id}/analytics/overview`),
    api(`/teacher/courses/${id}/analytics/knowledge-points`),
    api(`/teacher/courses/${id}/analytics/mastery-heatmap?page=${page.value}&size=${size}`),
    api(`/teacher/courses/${id}/analytics/questions/errors?${params.toString()}`)
  ]))
  if (!loaded) return
  overview.value = loaded[0]
  pointAnalytics.value = loaded[1]?.items || []
  heatmap.value = loaded[2]
  highErrors.value = loaded[3]?.items || []
  student.value = null
}

async function changeCourse() {
  page.value = 0
  await loadAnalytics()
}

async function openStudent(studentId) {
  if (!selectedCourseId.value) return
  student.value = await run(() => api(`/teacher/courses/${selectedCourseId.value}/students/${studentId}/analytics`))
}

async function nextPage(delta) {
  page.value = Math.max(0, page.value + delta)
  await loadAnalytics()
}

function associatedNames(item) {
  return item.associatedKnowledgePoints?.map(point => point.knowledgeName).join('、') || '未关联知识点'
}

function heatClass(cell) {
  if (cell.status === 'UNKNOWN') return 'unknown'
  if (Number(cell.masteryScore) < 0.5) return 'low'
  if (Number(cell.masteryScore) < 0.7) return 'mid'
  return 'high'
}

function masteryText(item) {
  return item.status === 'UNKNOWN' ? '暂无学习数据' : percent(item.masteryScore)
}

function recommendationText(item) {
  if (item.reasonCode === 'UNMET_PREREQUISITE') return '前置知识待巩固'
  if (item.reasonCode === 'LOW_MASTERY') return '掌握程度偏低'
  return '建议复习'
}

async function boot() {
  await loadCourses()
  if (selectedCourseId.value) await loadAnalytics()
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div><p class="eyebrow">教师工作台</p><h2>{{ selectedCourse?.courseName || '我的课程' }}</h2><p>从班级整体到知识点、题目和学生个体逐层查看学习情况。</p></div>
      <div class="intro-actions"><select v-model="selectedCourseId" @change="changeCourse"><option v-for="course in courses" :key="course.courseId" :value="String(course.courseId)">{{ course.courseName }}（在读 {{ course.activeEnrollmentCount }} 人）</option></select><button class="primary-button" @click="loadAnalytics">刷新学情</button></div>
    </div>

    <div v-if="overview" class="metric-grid four">
      <article class="metric-card accent-blue"><span>在读学生</span><strong>{{ overview.activeEnrolledStudents }}</strong><small>人</small></article>
      <article class="metric-card accent-green"><span>近期有学习记录</span><strong>{{ overview.studentsWithActivity }}</strong><small>人</small></article>
      <article class="metric-card accent-purple"><span>累计作答</span><strong>{{ overview.attemptCount }}</strong><small>次</small></article>
      <article class="metric-card accent-orange"><span>整体正确率</span><strong>{{ percent(overview.correctRate) }}</strong><small>{{ weakPointCount }} 个知识点需关注</small></article>
    </div>

    <div class="two-column teacher-layout">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">知识点诊断</p><h3>班级知识掌握概览</h3></div><span class="soft-badge">{{ pointAnalytics.length }} 个知识点</span></div>
        <div class="teacher-point-list">
          <article v-for="item in pointAnalytics" :key="item.knowledgePointId">
            <div><strong>{{ item.knowledgeName }}</strong><span>已有记录 {{ item.observedStudentCount }} 人 · 暂无记录 {{ item.unknownStudentCount }} 人</span></div>
            <div class="teacher-point-score"><b>{{ item.meanMastery === null ? '暂无' : percent(item.meanMastery) }}</b><small>班级平均掌握</small></div>
            <div class="progress"><i :style="{ width: `${item.meanMastery === null ? 0 : Math.round(Number(item.meanMastery) * 100)}%` }"></i></div>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">高频错误</p><h3>近期需要讲解的题目</h3></div><label class="mini-filter">最少作答<input v-model.number="minimumAttempts" type="number" min="1" @change="loadAnalytics"></label></div>
        <div v-if="highErrors.length" class="error-question-list">
          <article v-for="item in highErrors.slice(0, 8)" :key="item.questionId">
            <div><strong>{{ item.stemPreview }}</strong><span>{{ associatedNames(item) }}</span></div>
            <b>{{ percent(item.wrongRate) }} 错误率</b>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>当前没有明显高错题</strong><p>随着学生完成更多练习，这里会聚合需要重点讲解的题目。</p></div>
      </section>
    </div>

    <section v-if="heatmap" class="panel">
      <div class="panel-head"><div><p class="eyebrow">学生画像</p><h3>学生 × 知识点掌握情况</h3></div><span class="muted">点击学生姓名查看详情</span></div>
      <div class="heatmap-wrap modern">
        <table>
          <thead><tr><th>学生</th><th v-for="point in heatmap.knowledgePoints" :key="point.knowledgePointId">{{ point.knowledgeName }}</th></tr></thead>
          <tbody>
            <tr v-for="item in heatmap.students" :key="item.studentId">
              <th><button class="text-button strong" @click="openStudent(item.studentId)">{{ item.displayName }}</button></th>
              <td v-for="cell in item.items" :key="cell.knowledgePointId"><span :class="['heat-cell', heatClass(cell)]">{{ masteryText(cell) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pager"><button class="secondary-button" :disabled="page === 0" @click="nextPage(-1)">上一页</button><span>第 {{ page + 1 }} 页</span><button class="secondary-button" :disabled="(page + 1) * size >= heatmap.totalStudents" @click="nextPage(1)">下一页</button></div>
    </section>

    <section v-if="student" class="panel student-detail-panel">
      <div class="panel-head">
        <div>
          <p class="eyebrow">学生画像</p>
          <h3>学生学情详情</h3>
          <p class="student-name-line">{{ student.displayName }}</p>
        </div>
        <button class="text-button" @click="student = null">关闭详情</button>
      </div>
      <div class="metric-grid three compact-metrics"><article class="metric-card"><span>作答次数</span><strong>{{ student.activity.attemptCount }}</strong></article><article class="metric-card"><span>正确次数</span><strong>{{ student.activity.correctCount }}</strong></article><article class="metric-card"><span>正确率</span><strong>{{ percent(student.activity.correctRate) }}</strong></article></div>
      <div class="two-column">
        <div><h4>当前掌握情况</h4><article v-for="item in student.mastery" :key="item.knowledgePointId" class="simple-row"><span>{{ item.knowledgeName }}</span><b>{{ masteryText(item) }}</b></article></div>
        <div><h4>最近作答</h4><article v-for="answer in student.recentAnswers.slice(0, 8)" :key="answer.answerRecordId" class="simple-row"><span>{{ answer.stemPreview }}</span><b :class="answer.correct ? 'good-text' : 'bad-text'">{{ answer.correct ? '正确' : '错误' }}</b></article></div>
      </div>
      <div v-if="student.latestRecommendation" class="detail-block"><h4>当前学习建议</h4><article v-for="item in student.latestRecommendation.items" :key="item.rank" class="simple-row"><span>{{ item.rank }}. {{ item.knowledgeName }}</span><b>{{ recommendationText(item) }}</b></article></div>
    </section>
  </section>
</template>
