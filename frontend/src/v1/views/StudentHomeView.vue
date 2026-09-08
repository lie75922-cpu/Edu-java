<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, currentView, percent, run, selectedCourse, session, statusText } from '../store.js'

const courses = ref([])
const mastery = ref([])
const recommendation = ref(null)
const graph = ref(null)
const recentWeak = computed(() => mastery.value
  .filter(item => item.status === 'OBSERVED' && item.masteryScore !== null && Number(item.masteryScore) < 0.7)
  .sort((a, b) => Number(a.masteryScore) - Number(b.masteryScore))
  .slice(0, 4))
const observed = computed(() => mastery.value.filter(item => item.status === 'OBSERVED'))
const meanMastery = computed(() => {
  if (!observed.value.length) return null
  return observed.value.reduce((sum, item) => sum + Number(item.masteryScore), 0) / observed.value.length
})

async function loadHome() {
  const loadedCourses = await run(() => api('/courses'))
  if (!loadedCourses) return
  courses.value = loadedCourses
  if (!selectedCourse.value || !loadedCourses.some(item => item.id === selectedCourse.value.id)) {
    selectedCourse.value = loadedCourses.find(item => item.courseCode === 'DM-101') || loadedCourses[0] || null
  }
  if (!selectedCourse.value) return
  const courseId = selectedCourse.value.id
  mastery.value = (await api(`/courses/${courseId}/mastery`).catch(() => ({ items: [] })))?.items || []
  recommendation.value = await api(`/courses/${courseId}/recommendations/latest`).catch(() => null)
  graph.value = await api(`/courses/${courseId}/graph`).catch(() => null)
}

function openCourse(course) {
  selectedCourse.value = course
  currentView.value = '课程学习'
}

onMounted(loadHome)
</script>

<template>
  <section class="dashboard-page">
    <div class="welcome-banner">
      <div>
        <p class="eyebrow light">欢迎回来，{{ session?.user?.nickname || '同学' }}</p>
        <h2>今天继续把离散数学学得更清楚一点</h2>
        <p>从知识结构出发，结合你的答题记录和薄弱知识安排下一步学习。</p>
      </div>
      <button class="light-button" @click="currentView = '个性化学习'">查看我的学习建议</button>
    </div>

    <div class="metric-grid four">
      <article class="metric-card accent-blue"><span>已加入课程</span><strong>{{ courses.length }}</strong><small>门</small></article>
      <article class="metric-card accent-green"><span>已有学习记录知识点</span><strong>{{ observed.length }}</strong><small>个</small></article>
      <article class="metric-card accent-orange"><span>当前薄弱知识</span><strong>{{ recentWeak.length }}</strong><small>个重点关注</small></article>
      <article class="metric-card accent-purple"><span>平均掌握情况</span><strong>{{ meanMastery === null ? '暂无' : percent(meanMastery) }}</strong><small>{{ meanMastery === null ? '先完成几道练习' : '基于已有作答' }}</small></article>
    </div>

    <div class="two-column">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">我的课程</p><h3>继续学习</h3></div><button class="text-button" @click="currentView = '课程学习'">查看全部</button></div>
        <div class="course-cards">
          <article v-for="course in courses" :key="course.id" class="course-card" :class="{ featured: course.courseCode === 'DM-101' }">
            <div class="course-cover">{{ course.courseName.slice(0, 2) }}</div>
            <div class="course-info">
              <span class="pill">{{ course.courseCode === 'DM-101' ? '主修课程' : '专题训练' }}</span>
              <h4>{{ course.courseName }}</h4>
              <p>{{ course.description || '课程内容建设中' }}</p>
              <button class="primary-button" @click="openCourse(course)">进入学习</button>
            </div>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">学习诊断</p><h3>需要优先巩固</h3></div><button class="text-button" @click="currentView = '个性化学习'">查看详情</button></div>
        <div v-if="recentWeak.length" class="weak-list">
          <article v-for="item in recentWeak" :key="item.knowledgePointId">
            <div><strong>{{ item.knowledgeName }}</strong><span>{{ item.correctCount }}/{{ item.attemptCount }} 次答对</span></div>
            <div class="progress"><i :style="{ width: `${Math.round(Number(item.masteryScore) * 100)}%` }"></i></div>
            <b>{{ percent(item.masteryScore) }}</b>
          </article>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无明确薄弱知识</strong>
          <p>完成课程练习后，系统会根据学习记录识别需要优先巩固的内容。</p>
        </div>
      </section>
    </div>

    <div class="two-column">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">知识结构</p><h3>当前课程知识图谱</h3></div><button class="text-button" @click="currentView = '知识图谱'">打开图谱</button></div>
        <div class="graph-summary" v-if="graph">
          <div><strong>{{ graph.nodes?.length || 0 }}</strong><span>知识点</span></div>
          <div><strong>{{ graph.edges?.length || 0 }}</strong><span>先修关系</span></div>
          <p>从“数理逻辑、集合论与关系、图论、代数结构”四个模块理解课程知识脉络。</p>
        </div>
        <div v-else class="empty-state compact"><strong>知识图谱尚未加载</strong><p>进入《离散数学》课程后可查看已发布的知识结构。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">个性化推荐</p><h3>下一步建议</h3></div></div>
        <template v-if="recommendation?.items?.length">
          <article v-for="item in recommendation.items.slice(0, 3)" :key="item.id" class="recommend-card">
            <span class="recommend-rank">{{ item.rank }}</span>
            <div><strong>{{ item.knowledgeName }}</strong><p>{{ item.exerciseName || '建议先复习该知识点' }}</p><small>{{ item.reasonCode === 'UNMET_PREREQUISITE' ? '前置知识尚未巩固' : item.reasonCode === 'LOW_MASTERY' ? '当前掌握程度偏低' : '根据近期学习记录推荐' }}</small></div>
          </article>
        </template>
        <div v-else class="empty-state compact"><strong>等待更多学习记录</strong><p>完成练习后会生成可解释的学习建议。</p></div>
      </section>
    </div>
  </section>
</template>
