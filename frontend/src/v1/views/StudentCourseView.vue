<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, run, selectedCourse, statusText } from '../store.js'

const courses = ref([])
const areas = ref([])
const points = ref([])
const exercises = ref([])
const selectedAreaId = ref('ALL')
const selectedExercise = ref(null)

const areaMap = computed(() => new Map(areas.value.map(area => [String(area.id), area])))

const orderedPoints = computed(() => [...points.value].sort((left, right) => {
  const leftArea = areaMap.value.get(String(left.areaId))
  const rightArea = areaMap.value.get(String(right.areaId))
  const leftAreaCode = leftArea?.areaCode || 'ZZZ'
  const rightAreaCode = rightArea?.areaCode || 'ZZZ'
  const areaCompare = leftAreaCode.localeCompare(rightAreaCode)
  if (areaCompare !== 0) return areaCompare
  return String(left.knowledgeName).localeCompare(String(right.knowledgeName), 'zh-CN')
}))

const visiblePoints = computed(() => selectedAreaId.value === 'ALL'
  ? orderedPoints.value
  : orderedPoints.value.filter(item => String(item.areaId) === selectedAreaId.value))

function areaName(point) {
  return areaMap.value.get(String(point.areaId))?.areaName || '其他知识'
}

function pointCount(areaId) {
  return points.value.filter(item => String(item.areaId) === String(areaId)).length
}

function pointExercise(point) {
  return exercises.value.find(exercise => (exercise.knowledgePoints || []).some(item =>
    Number(item.id ?? item.knowledgePointId) === Number(point.id)
  ))
}

function exerciseAvailability(exercise) {
  if (!exercise) return '当前知识点暂无已关联练习元数据'
  if (exercise.status === 'ACTIVE') return '目录记录可用；题目内容需由题库单独接入'
  return '当前目录记录不可用'
}

async function loadCourse(course = null) {
  if (course) selectedCourse.value = course
  if (!selectedCourse.value) return
  selectedAreaId.value = 'ALL'
  await api(`/courses/${selectedCourse.value.id}/enroll`, { method: 'POST' }).catch(() => null)
  const [courseDetail, areaList, pointList, exerciseList] = await Promise.all([
    api(`/courses/${selectedCourse.value.id}`),
    api(`/courses/${selectedCourse.value.id}/knowledge-areas`).catch(() => []),
    api(`/courses/${selectedCourse.value.id}/knowledge-points`),
    api(`/exercise-units?courseId=${selectedCourse.value.id}`)
  ])
  selectedCourse.value = courseDetail
  areas.value = areaList
  points.value = pointList
  exercises.value = exerciseList
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!selectedCourse.value || !result.some(item => item.id === selectedCourse.value.id)) {
    selectedCourse.value = result[0] || null
  }
  if (selectedCourse.value) await run(() => loadCourse())
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div>
        <p class="eyebrow">课程学习</p>
        <h2>{{ selectedCourse?.courseName || '我的课程' }}</h2>
        <p>{{ selectedCourse?.description || '按课程知识领域和知识关系组织学习内容。' }}</p>
      </div>
      <select v-if="courses.length > 1" :value="selectedCourse?.id" @change="loadCourse(courses.find(item => item.id === Number($event.target.value)))">
        <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option>
      </select>
    </div>

    <div class="chapter-strip" v-if="areas.length">
      <button :class="{ active: selectedAreaId === 'ALL' }" @click="selectedAreaId = 'ALL'">
        <strong>全部内容</strong><span>{{ points.length }} 个知识点</span>
      </button>
      <button v-for="area in areas" :key="area.id" :class="{ active: selectedAreaId === String(area.id) }" @click="selectedAreaId = String(area.id)">
        <strong>{{ area.areaName }}</strong><span>{{ pointCount(area.id) }} 个知识点</span>
      </button>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <p class="eyebrow">知识目录</p>
          <h3>{{ selectedAreaId === 'ALL' ? '课程知识点' : (areaMap.get(selectedAreaId)?.areaName || '课程知识') }}</h3>
        </div>
        <span class="soft-badge">{{ visiblePoints.length }} 个知识点</span>
      </div>
      <div class="knowledge-list">
        <article v-for="(point, index) in visiblePoints" :key="point.id" class="knowledge-row">
          <div class="knowledge-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="knowledge-main"><strong>{{ point.knowledgeName }}</strong><span>{{ areaName(point) }}</span></div>
          <div class="knowledge-actions">
            <span v-if="pointExercise(point)" class="resource-count">已关联练习元数据</span>
            <button v-if="pointExercise(point)" class="secondary-button small" @click="selectedExercise = pointExercise(point)">查看练习状态</button>
            <span v-else class="muted">暂无已关联练习元数据</span>
          </div>
        </article>
      </div>
      <div v-if="!visiblePoints.length" class="empty-state compact">
        <strong>当前领域暂无知识点</strong><p>课程知识结构由后台业务数据驱动，不在前端写死章节和知识编码。</p>
      </div>
    </section>

    <div v-if="selectedExercise" class="modal-backdrop" @click.self="selectedExercise = null">
      <section class="question-modal">
        <button class="close-button" @click="selectedExercise = null">×</button>
        <p class="eyebrow">练习目录信息</p>
        <h3>{{ selectedExercise.exerciseName }}</h3>
        <p>目录状态：{{ statusText(selectedExercise.status) }}。{{ exerciseAvailability(selectedExercise) }}</p>
        <p v-if="selectedExercise.difficulty !== null && selectedExercise.difficulty !== undefined">标注难度：{{ selectedExercise.difficulty }}</p>
        <p>关联知识：{{ selectedExercise.knowledgePoints?.map(item => item.knowledgeName).filter(Boolean).join('、') || '暂未提供' }}</p>
        <div class="warning-box">
          <strong>题目内容尚未由目录数据提供</strong>
          <p>本页只展示后端返回的练习元数据。题干、选项、答案和解析属于独立题库数据，当前不会将目录元数据伪装成可作答题目。</p>
        </div>
      </section>
    </div>
  </section>
</template>
