<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, newRequestId, run, selectedCourse } from '../store.js'

const courses = ref([])
const points = ref([])
const exercises = ref([])
const selectedChapter = ref('全部')
const activeQuestion = ref(null)
const selectedOptions = ref([])
const answerResult = ref(null)
const requestId = ref('')

const chapters = [
  { key: 'LOGIC', name: '第一章 数理逻辑', desc: '命题、等值演算、范式与推理理论' },
  { key: 'SETREL', name: '第二章 集合论与关系', desc: '集合运算、二元关系、等价关系与偏序' },
  { key: 'GRAPH', name: '第三章 图论', desc: '图、路径、连通性、欧拉图与树' },
  { key: 'ALG', name: '第四章 代数结构', desc: '代数系统、群、环、域、格与布尔代数' }
]

function chapterOf(point) {
  const code = point.knowledgeCode || ''
  if (code.includes('LOGIC')) return 'LOGIC'
  if (code.includes('SET') || code.includes('REL')) return 'SETREL'
  if (code.includes('GRAPH')) return 'GRAPH'
  if (code.includes('ALG')) return 'ALG'
  return 'OTHER'
}

const visiblePoints = computed(() => selectedChapter.value === '全部'
  ? points.value
  : points.value.filter(item => chapterOf(item) === selectedChapter.value))

function pointExercise(point) {
  return exercises.value.find(exercise => (exercise.knowledgePoints || []).some(item => item.id === point.id || item.knowledgePointId === point.id))
}

async function loadCourse(course = null) {
  if (course) selectedCourse.value = course
  if (!selectedCourse.value) return
  await api(`/courses/${selectedCourse.value.id}/enroll`, { method: 'POST' }).catch(() => null)
  const [courseDetail, pointList, exerciseList] = await Promise.all([
    api(`/courses/${selectedCourse.value.id}`),
    api(`/courses/${selectedCourse.value.id}/knowledge-points`),
    api(`/exercise-units?courseId=${selectedCourse.value.id}`)
  ])
  selectedCourse.value = courseDetail
  points.value = pointList
  exercises.value = exerciseList
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!selectedCourse.value) selectedCourse.value = result.find(item => item.courseCode === 'DM-101') || result[0] || null
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
      <div><p class="eyebrow">课程学习</p><h2>{{ selectedCourse?.courseName || '离散数学' }}</h2><p>{{ selectedCourse?.description || '按章节与知识关系组织学习内容。' }}</p></div>
      <select v-if="courses.length > 1" :value="selectedCourse?.id" @change="loadCourse(courses.find(item => item.id === Number($event.target.value)))">
        <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option>
      </select>
    </div>

    <div class="chapter-strip" v-if="selectedCourse?.courseCode === 'DM-101'">
      <button :class="{ active: selectedChapter === '全部' }" @click="selectedChapter = '全部'"><strong>全部内容</strong><span>{{ points.length }} 个知识点</span></button>
      <button v-for="chapter in chapters" :key="chapter.key" :class="{ active: selectedChapter === chapter.key }" @click="selectedChapter = chapter.key">
        <strong>{{ chapter.name }}</strong><span>{{ chapter.desc }}</span>
      </button>
    </div>

    <section class="panel">
      <div class="panel-head"><div><p class="eyebrow">知识目录</p><h3>{{ selectedChapter === '全部' ? '课程知识点' : chapters.find(item => item.key === selectedChapter)?.name }}</h3></div><span class="soft-badge">{{ visiblePoints.length }} 个知识点</span></div>
      <div class="knowledge-list">
        <article v-for="(point, index) in visiblePoints" :key="point.id" class="knowledge-row">
          <div class="knowledge-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="knowledge-main"><strong>{{ point.knowledgeName }}</strong><span>{{ point.knowledgeCode }}</span></div>
          <div class="knowledge-actions">
            <span v-if="pointExercise(point)" class="resource-count">1 组练习</span>
            <button v-if="pointExercise(point)" class="primary-button small" @click="startPractice(point)">开始练习</button>
            <span v-else class="muted">暂无练习</span>
          </div>
        </article>
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
