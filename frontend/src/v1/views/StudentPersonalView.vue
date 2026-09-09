<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, percent, run, selectedCourse } from '../store.js'
import { recommendationExplanation } from '../recommendationExplanation.js'

const courses = ref([])
const mastery = ref([])
const recommendation = ref(null)
const path = ref(null)
const targetId = ref('')
const graph = ref(null)

const weakItems = computed(() => mastery.value.filter(item => item.status === 'OBSERVED' && item.masteryScore !== null && Number(item.masteryScore) < 0.7))
const unknownItems = computed(() => mastery.value.filter(item => item.status === 'UNKNOWN'))
const targetOptions = computed(() => graph.value?.nodes || [])
const hasPublishedGraph = computed(() => Boolean(graph.value?.graphVersionId && graph.value?.nodes?.length))

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
  if (!targetId.value && graph.value?.nodes?.length) targetId.value = String(graph.value.nodes[graph.value.nodes.length - 1].id)
}

async function generate() {
  if (!selectedCourse.value) return
  recommendation.value = await run(() => api(`/courses/${selectedCourse.value.id}/recommendations`, { method: 'POST' }), '已根据最新学习记录生成学习建议。')
}

async function loadPath() {
  const id = Number(targetId.value)
  if (!id || !hasPublishedGraph.value) return
  path.value = await run(() => api(`/knowledge-points/${id}/learning-path`))
}

function explanationFor(item) {
  return recommendationExplanation(item)
}

function pathBadge(node) {
  return node.reasonCode === 'UNMET_PREREQUISITE' ? '前置知识' : '学习目标'
}

function pathDescription(node) {
  return node.masteryStatus === 'UNKNOWN'
    ? '暂无学习数据，建议先完成基础学习与练习。'
    : `当前掌握情况 ${percent(node.masteryScore)}。`
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
      <div><p class="eyebrow">个性化学习</p><h2>我的学习建议与路径</h2><p>学习建议优先依据真实答题记录、掌握情况、近期错误和复习间隔生成，不依赖知识图谱；只有存在安全发布的知识图谱时才生成先修学习路径。</p></div>
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
        <div v-else class="empty-state compact"><strong>当前没有明确薄弱知识</strong><p>系统会优先安排已有学习内容复习；新用户则按课程顺序提供入门建议。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">推荐任务</p><h3>下一步学什么</h3></div></div>
        <template v-if="recommendation?.items?.length">
          <article v-for="item in recommendation.items" :key="item.id" class="recommend-card large">
            <span class="recommend-rank">{{ item.rank }}</span>
            <div>
              <strong>{{ item.knowledgeName }}</strong>
              <p>{{ item.exerciseName || '学习或复习该知识点' }}</p>
              <small>{{ explanationFor(item).message }}</small>
              <small v-if="explanationFor(item).masteryScore !== null">当前掌握情况：{{ percent(explanationFor(item).masteryScore) }}</small>
              <small v-if="explanationFor(item).attemptCount !== null && explanationFor(item).correctCount !== null">已有作答 {{ explanationFor(item).attemptCount }} 次，答对 {{ explanationFor(item).correctCount }} 次。</small>
              <small v-if="explanationFor(item).recentErrorCount !== null && explanationFor(item).recentErrorCount > 0">近期错误 {{ explanationFor(item).recentErrorCount }} 次。</small>
              <small v-if="explanationFor(item).unmetPrerequisite">已发布知识图谱提示该知识与当前薄弱项存在先修关系。</small>
              <small>推荐规则：{{ explanationFor(item).ruleVersion || '常规学习建议' }}；{{ explanationFor(item).graphVersionId !== null ? `知识关系版本：${explanationFor(item).graphVersionId}` : '本次未使用知识图谱' }}</small>
            </div>
          </article>
        </template>
        <div v-else class="empty-state compact"><strong>暂时没有推荐任务</strong><p>点击“更新学习建议”，系统会根据已有学习记录或课程顺序生成建议。</p></div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-head"><div><p class="eyebrow">学习路径</p><h3>安全知识关系下的学习顺序</h3></div><div class="path-controls"><select v-model="targetId" :disabled="!hasPublishedGraph"><option value="">选择目标知识点</option><option v-for="node in targetOptions" :key="node.id" :value="String(node.id)">{{ node.knowledgeName }}</option></select><button class="primary-button" :disabled="!hasPublishedGraph || !targetId" @click="loadPath">生成学习路径</button></div></div>
      <div v-if="path?.nodes?.length" class="learning-path">
        <article v-for="(node, index) in path.nodes" :key="node.knowledgePointId" class="path-step">
          <div class="path-marker"><span>{{ index + 1 }}</span><i v-if="index < path.nodes.length - 1"></i></div>
          <div class="path-card"><span class="soft-badge">{{ pathBadge(node) }}</span><h4>{{ node.knowledgeName }}</h4><p>{{ pathDescription(node) }}</p><small>{{ node.hasAvailableExercise ? `已关联可答题练习：${node.exerciseName}` : '当前暂无合法可答题练习' }}</small></div>
        </article>
      </div>
      <div v-else-if="!hasPublishedGraph" class="empty-state"><strong>当前课程暂无安全发布的知识图谱</strong><p>上方常规学习建议仍可正常使用；系统不会为了生成路径而使用尚未通过校验的候选关系。</p></div>
      <div v-else class="empty-state"><strong>选择一个目标知识点</strong><p>系统会从已发布知识图谱中提取真实前置关系，并过滤已掌握节点形成学习路径。</p></div>
    </section>
  </section>
</template>
