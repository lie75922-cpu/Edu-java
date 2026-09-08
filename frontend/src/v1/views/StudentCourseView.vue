<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, newRequestId, run, selectedCourse } from '../store.js'

const courses = ref([])
const areas = ref([])
const points = ref([])
const exercises = ref([])
const selectedAreaId = ref('ALL')
const activeQuestion = ref(null)
const selectedOptions = ref([])
const answerResult = ref(null)
const requestId = ref('')

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

async function startPractice(point) {
  const exercise = pointExercise(point)
  if (!exercise) return
  const question = await run(() => api(`/exercise-units/${exercise.id}/questions/next`))
  if (!question) return
  activeQuestion.value = { ...question, point, exercise }
  selectedOptions.value = []
  answerResult.value = null
  requestId.value = newRequestId()
}

function chooseOption(key) {
  if (activeQuestion.value?.questionType === 'MULTIPLE_CHOICE') {
    selectedOptions.value = selectedOptions.value.includes(key)
      ? selectedOptions.value.filter(item => item !== key)
      : [...selectedOptions.value, key]
  } else {
    selectedOptions.value = [key]
  }
}

async function submit() {
  if (!activeQuestion.value || !selectedOptions.value.length) return
  const result = await run(() => api(`/questions/${activeQuestion.value.id}/answers`, {
    method: 'POST',
    body: JSON.stringify({ selectedOptionKeys: selectedOptions.value, durationMs: 0, clientRequestId: requestId.value })
  }))
  if (result) answerResult.value = result
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
            <span v-if="pointExercise(point)" class="resource-count">已关联练习</span>
            <button v-if="pointExercise(point)" class="primary-button small" @click="startPractice(point)">开始练习</button>
            <span v-else class="muted">暂无练习</span>
          </div>
        </article>
      </div>
      <div v-if="!visiblePoints.length" class="empty-state compact">
        <strong>当前领域暂无知识点</strong><p>课程知识结构由后台业务数据驱动，不在前端写死章节和知识编码。</p>
      </div>
    </section>

    <div v-if="activeQuestion" class="modal-backdrop" @click.self="activeQuestion = null">
      <section class="question-modal">
        <button class="close-button" @click="activeQuestion = null">×</button>
        <p class="eyebrow">{{ activeQuestion.point.knowledgeName }} · 知识点练习</p>
        <h3>{{ activeQuestion.stem }}</h3>
        <div class="option-list">
          <button v-for="option in activeQuestion.options" :key="option.id" :class="['answer-option', { selected: selectedOptions.includes(option.optionKey) }]" @click="chooseOption(option.optionKey)">
            <span>{{ option.optionKey }}</span><strong>{{ option.optionText }}</strong>
          </button>
        </div>
        <button v-if="!answerResult" class="primary-button full" :disabled="!selectedOptions.length" @click="submit">提交答案</button>
        <div v-else :class="['answer-feedback', answerResult.correct ? 'right' : 'wrong']">
          <strong>{{ answerResult.correct ? '回答正确' : '这道题还需要再巩固一下' }}</strong>
          <p>正确答案：{{ answerResult.correctOptionKeys.join('、') }}</p>
          <p>{{ answerResult.explanation || '暂无解析。' }}</p>
          <button class="primary-button" @click="activeQuestion = null">完成本次练习</button>
        </div>
      </section>
    </div>
  </section>
</template>
