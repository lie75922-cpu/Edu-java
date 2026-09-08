<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, percent, run, selectedCourse } from '../store.js'

const courses = ref([])
const mastery = ref([])
const recommendation = ref(null)
const path = ref(null)
const targetId = ref('')
const graph = ref(null)

const weakItems = computed(() => mastery.value.filter(item => item.status === 'OBSERVED' && item.masteryScore !== null && Number(item.masteryScore) < 0.7))
const unknownItems = computed(() => mastery.value.filter(item => item.status === 'UNKNOWN'))

async function load(course = null) {
  if (course) {
    selectedCourse.value = course
    targetId.value = ''
  }
  if (!selectedCourse.value) return
  const id = selectedCourse.value.id
  const [m, g] = await Promise.all([
    api(`/courses/${id}/mastery`).catch(() => ({ items: [] })),
    api(`/courses/${id}/graph`).catch(() => null)
  ])
  mastery.value = m?.items || []
  graph.value = g
  recommendation.value = await api(`/courses/${id}/recommendations/latest`).catch(() => null)
  path.value = null
  if (!targetId.value && weakItems.value[0]) targetId.value = String(weakItems.value[0].knowledgePointId)
  if (!targetId.value && graph.value?.nodes?.length) targetId.value = String(graph.value.nodes[graph.value.nodes.length - 1].id)
}

async function generate() {
  if (!selectedCourse.value) return
  recommendation.value = await run(() => api(`/courses/${selectedCourse.value.id}/recommendations`, { method: 'POST' }), '已根据最新学习记录生成学习建议。')
}

async function loadPath() {
  const id = Number(targetId.value)
  if (!id) return
  path.value = await run(() => api(`/knowledge-points/${id}/learning-path`))
}

function reasonText(code) {
  if (code === 'UNMET_PREREQUISITE') return '目标知识的前置内容尚未巩固'
  if (code === 'LOW_MASTERY') return '已有学习记录显示当前掌握程度偏低'
  if (code === 'RECENT_ERRORS') return '近期在相关练习中出现错误'
  if (code === 'REVIEW_DUE') return '距离上次练习时间较长，建议复习'
  if (code === 'TARGET_PRACTICE') return '目标知识需要进一步练习'
  return '根据当前学习记录推荐'
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!selectedCourse.value || !result.some(item => item.id === selectedCourse.value.id)) {
    selectedCourse.value = result[0] || null
  }
  await load()
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div><p class="eyebrow">个性化学习</p><h2>我的学习建议与路径</h2><p>根据真实答题记录识别薄弱知识，再结合已发布知识图谱安排学习顺序。</p></div>
      <div class="intro-actions"><select :value="selectedCourse?.id" @change="load(courses.find(item => item.id === Number($event.target.value)))"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option></select><button class="primary-button" @click="generate">更新学习建议</button></div>
    </div>

    <div class="metric-grid three">
      <article class="metric-card accent-orange"><span>薄弱知识</span><strong>{{ weakItems.length }}</strong><small>需要优先巩固</small></article>
      <article class="metric-card accent-blue"><span>已有学习记录</span><strong>{{ mastery.length - unknownItems.length }}</strong><small>个知识点</small></article>
      <article class="metric-card accent-purple"><span>暂无学习数据</span><strong>{{ unknownItems.length }}</strong><small>不伪造掌握分数</small></article>
    </div>

    <div class="two-column personal-layout">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">薄弱知识诊断</p><h3>建议优先巩固</h3></div></div>
        <div v-if="weakItems.length" class="mastery-list">
          <article v-for="item in weakItems" :key="item.knowledgePointId">
            <div class="mastery-title"><strong>{{ item.knowledgeName }}</strong><b>{{ percent(item.masteryScore) }}</b></div>
            <div class="progress large"><i :style="{ width: `${Math.round(Number(item.masteryScore) * 100)}%` }"></i></div>
            <p>已作答 {{ item.attemptCount }} 次，答对 {{ item.correctCount }} 次。建议先复习概念，再完成对应练习。</p>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>当前没有明确薄弱知识</strong><p>完成更多练习后，系统会逐步形成更完整的学习诊断。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">推荐任务</p><h3>下一步学什么</h3></div></div>
        <template v-if="recommendation?.items?.length">
          <article v-for="item in recommendation.items" :key="item.id" class="recommend-card large">
            <span class="recommend-rank">{{ item.rank }}</span>
            <div><strong>{{ item.knowledgeName }}</strong><p>{{ item.exerciseName || '复习该知识点' }}</p><small>{{ reasonText(item.reasonCode) }}</small></div>
          </article>
        </template>
        <div v-else class="empty-state compact"><strong>暂时没有推荐任务</strong><p>点击“更新学习建议”后，系统会结合掌握情况和知识先修关系生成推荐。</p></div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-head"><div><p class="eyebrow">学习路径</p><h3>从当前状态到目标知识</h3></div><div class="path-controls"><select v-model="targetId"><option value="">选择目标知识点</option><option v-for="node in graph?.nodes || []" :key="node.id" :value="String(node.id)">{{ node.knowledgeName }}</option></select><button class="primary-button" @click="loadPath">生成学习路径</button></div></div>
      <div v-if="path?.nodes?.length" class="learning-path">
        <article v-for="(node, index) in path.nodes" :key="node.knowledgePointId" class="path-step">
          <div class="path-marker"><span>{{ index + 1 }}</span><i v-if="index < path.nodes.length - 1"></i></div>
          <div class="path-card"><span class="soft-badge">{{ node.reasonCode === 'UNMET_PREREQUISITE' ? '前置知识' : '学习目标' }}</span><h4>{{ node.knowledgeName }}</h4><p>{{ node.masteryStatus === 'UNKNOWN' ? '暂无学习数据，建议先完成基础学习与练习。' : `当前掌握情况 ${percent(node.masteryScore)}。` }}</p><small>{{ node.hasAvailableExercise ? `已关联练习：${node.exerciseName}` : '当前暂无可用练习' }}</small></div>
        </article>
      </div>
      <div v-else class="empty-state"><strong>选择一个目标知识点</strong><p>系统会从当前已发布知识图谱中提取前置知识，并过滤已经掌握的节点，形成有顺序的学习路径。</p></div>
    </section>
  </section>
</template>
