<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, canManage, run, statusText } from '../store.js'
import QuestionBatchImportPanel from '../components/QuestionBatchImportPanel.vue'

const courses = ref([])
const selectedCourseId = ref('')
const exercises = ref([])
const selectedExerciseId = ref('')
const questions = ref([])
const editingId = ref(null)

const form = reactive({
  questionType: 'SINGLE_CHOICE',
  stem: '',
  explanation: '',
  difficulty: 50,
  status: 'ACTIVE',
  answerOptionKeys: ['A'],
  options: [
    { optionKey: 'A', optionText: '', sortOrder: 1 },
    { optionKey: 'B', optionText: '', sortOrder: 2 },
    { optionKey: 'C', optionText: '', sortOrder: 3 },
    { optionKey: 'D', optionText: '', sortOrder: 4 }
  ]
})

const selectedCourse = computed(() => courses.value.find(item => String(item.id) === String(selectedCourseId.value)))
const selectedExercise = computed(() => exercises.value.find(item => String(item.id) === String(selectedExerciseId.value)))
const activeQuestionCount = computed(() => questions.value.filter(item => item.status === 'ACTIVE').length)

function normalizeCourse(item) {
  return {
    id: item.id ?? item.courseId,
    courseName: item.courseName,
    courseCode: item.courseCode
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    questionType: 'SINGLE_CHOICE',
    stem: '',
    explanation: '',
    difficulty: 50,
    status: 'ACTIVE',
    answerOptionKeys: ['A'],
    options: [
      { optionKey: 'A', optionText: '', sortOrder: 1 },
      { optionKey: 'B', optionText: '', sortOrder: 2 },
      { optionKey: 'C', optionText: '', sortOrder: 3 },
      { optionKey: 'D', optionText: '', sortOrder: 4 }
    ]
  })
}

function applyTypeDefaults() {
  if (form.questionType === 'TRUE_FALSE') {
    form.options = [
      { optionKey: 'TRUE', optionText: '正确', sortOrder: 1 },
      { optionKey: 'FALSE', optionText: '错误', sortOrder: 2 }
    ]
    form.answerOptionKeys = ['TRUE']
    return
  }
  if (form.options.some(item => item.optionKey === 'TRUE' || item.optionKey === 'FALSE')) {
    form.options = [
      { optionKey: 'A', optionText: '', sortOrder: 1 },
      { optionKey: 'B', optionText: '', sortOrder: 2 },
      { optionKey: 'C', optionText: '', sortOrder: 3 },
      { optionKey: 'D', optionText: '', sortOrder: 4 }
    ]
    form.answerOptionKeys = ['A']
  }
  if (form.questionType === 'SINGLE_CHOICE' && form.answerOptionKeys.length !== 1) {
    form.answerOptionKeys = [form.answerOptionKeys[0] || 'A']
  }
}

async function loadCourses() {
  const result = await run(() => api(canManage.value ? '/courses' : '/teacher/courses'))
  if (!result) return
  courses.value = result.map(normalizeCourse)
  if (!courses.value.some(item => String(item.id) === String(selectedCourseId.value))) {
    selectedCourseId.value = courses.value[0] ? String(courses.value[0].id) : ''
  }
  await loadExercises()
}

async function loadExercises() {
  questions.value = []
  selectedExerciseId.value = ''
  resetForm()
  const courseId = Number(selectedCourseId.value)
  if (!courseId) {
    exercises.value = []
    return
  }
  const result = await run(() => api(`/admin/exercise-units?courseId=${courseId}`))
  exercises.value = result || []
  if (exercises.value.length) {
    selectedExerciseId.value = String(exercises.value[0].id)
    await loadQuestions()
  }
}

async function loadQuestions() {
  resetForm()
  const exerciseId = Number(selectedExerciseId.value)
  if (!exerciseId) {
    questions.value = []
    return
  }
  questions.value = await run(() => api(`/admin/questions?exerciseUnitId=${exerciseId}`)) || []
}

async function afterBatchImport() {
  await loadQuestions()
}

function answerChecked(key) {
  return form.answerOptionKeys.includes(key)
}

function toggleAnswer(key) {
  if (form.questionType === 'SINGLE_CHOICE' || form.questionType === 'TRUE_FALSE') {
    form.answerOptionKeys = [key]
    return
  }
  form.answerOptionKeys = answerChecked(key)
    ? form.answerOptionKeys.filter(item => item !== key)
    : [...form.answerOptionKeys, key]
}

function editQuestion(item) {
  editingId.value = item.id
  form.questionType = item.questionType
  form.stem = item.stem
  form.explanation = item.explanation || ''
  form.difficulty = item.difficulty ?? 50
  form.status = item.status
  form.answerOptionKeys = [...(item.answerOptionKeys || [])]
  form.options = (item.options || []).map(option => ({
    optionKey: option.optionKey,
    optionText: option.optionText,
    sortOrder: option.sortOrder
  }))
}

async function saveQuestion() {
  const exerciseUnitId = Number(selectedExerciseId.value)
  if (!exerciseUnitId || !form.stem.trim()) return
  const options = form.options
    .map((item, index) => ({ ...item, optionText: item.optionText.trim(), sortOrder: index + 1 }))
    .filter(item => item.optionText)
  const optionKeys = new Set(options.map(item => item.optionKey))
  const answerOptionKeys = form.answerOptionKeys.filter(key => optionKeys.has(key))
  if (options.length < 2 || !answerOptionKeys.length) return
  const payload = {
    exerciseUnitId,
    questionType: form.questionType,
    stem: form.stem.trim(),
    answerOptionKeys,
    explanation: form.explanation.trim() || null,
    difficulty: Number(form.difficulty),
    status: form.status,
    options
  }
  const path = editingId.value ? `/admin/questions/${editingId.value}` : '/admin/questions'
  const method = editingId.value ? 'PUT' : 'POST'
  const saved = await run(() => api(path, { method, body: JSON.stringify(payload) }), editingId.value ? '题目已更新。' : '题目已创建。')
  if (!saved) return
  await loadQuestions()
}

async function disableQuestion(item) {
  await run(() => api(`/admin/questions/${item.id}`, { method: 'DELETE' }), '题目已停用。')
  await loadQuestions()
}

async function boot() {
  await loadCourses()
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div>
        <p class="eyebrow">教学内容</p>
        <h2>题库管理</h2>
        <p>为已有练习单元维护平台自编或已获授权题目、答案和解析。真实目录元数据不会自动伪造成题目。</p>
      </div>
      <div class="intro-actions">
        <select v-model="selectedCourseId" @change="loadExercises">
          <option v-for="course in courses" :key="course.id" :value="String(course.id)">{{ course.courseName }}</option>
        </select>
        <select v-model="selectedExerciseId" @change="loadQuestions">
          <option v-for="exercise in exercises" :key="exercise.id" :value="String(exercise.id)">{{ exercise.exerciseName }}</option>
        </select>
      </div>
    </div>

    <div class="metric-grid three">
      <article class="metric-card accent-blue"><span>当前课程练习单元</span><strong>{{ exercises.length }}</strong><small>{{ selectedCourse?.courseName || '未选择课程' }}</small></article>
      <article class="metric-card accent-green"><span>当前单元题目</span><strong>{{ questions.length }}</strong><small>{{ selectedExercise?.exerciseName || '未选择练习单元' }}</small></article>
      <article class="metric-card accent-purple"><span>启用题目</span><strong>{{ activeQuestionCount }}</strong><small>可进入学生答题流程</small></article>
    </div>

    <QuestionBatchImportPanel :exercises="exercises" @imported="afterBatchImport" />

    <div class="two-column admin-layout">
      <section class="panel">
        <div class="panel-head">
          <div><p class="eyebrow">已有题目</p><h3>{{ selectedExercise?.exerciseName || '请选择练习单元' }}</h3></div>
          <button class="secondary-button" @click="resetForm">新建题目</button>
        </div>
        <div v-if="questions.length">
          <article v-for="item in questions" :key="item.id" class="admin-list-card">
            <div>
              <strong>{{ item.stem }}</strong>
              <span>{{ item.questionType === 'SINGLE_CHOICE' ? '单选题' : item.questionType === 'MULTIPLE_CHOICE' ? '多选题' : '判断题' }} · 难度 {{ item.difficulty ?? '未设置' }}</span>
              <p>答案：{{ item.answerOptionKeys?.join('、') || '未设置' }}；{{ item.explanation || '暂无解析' }}</p>
            </div>
            <div class="intro-actions">
              <span class="soft-badge">{{ statusText(item.status) }}</span>
              <button class="text-button" @click="editQuestion(item)">编辑</button>
              <button v-if="item.status === 'ACTIVE'" class="text-button" @click="disableQuestion(item)">停用</button>
            </div>
          </article>
        </div>
        <div v-else class="empty-state compact"><strong>当前练习单元尚无题目</strong><p>可以在右侧创建平台自编/授权题目，或使用上方批量导入；不要从目录元数据自动生成题干、答案或解析。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">{{ editingId ? '编辑题目' : '创建题目' }}</p><h3>平台题目内容</h3></div></div>
        <form class="modern-form" @submit.prevent="saveQuestion">
          <label>题型
            <select v-model="form.questionType" @change="applyTypeDefaults">
              <option value="SINGLE_CHOICE">单选题</option>
              <option value="MULTIPLE_CHOICE">多选题</option>
              <option value="TRUE_FALSE">判断题</option>
            </select>
          </label>
          <label>题干<textarea v-model="form.stem" required placeholder="输入有明确来源和教学意义的题目"></textarea></label>
          <label>难度<input v-model.number="form.difficulty" type="number" min="0" max="100" step="1"></label>
          <label>状态<select v-model="form.status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
          <div>
            <strong>选项与正确答案</strong>
            <div v-for="option in form.options" :key="option.optionKey" class="simple-row">
              <label>
                <input
                  :type="form.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'"
                  name="correctAnswer"
                  :checked="answerChecked(option.optionKey)"
                  @change="toggleAnswer(option.optionKey)"
                >
                {{ option.optionKey }}
              </label>
              <input v-model="option.optionText" required :disabled="form.questionType === 'TRUE_FALSE'" :placeholder="`选项 ${option.optionKey}`">
            </div>
          </div>
          <label>答案解析<textarea v-model="form.explanation" placeholder="解释为什么选择该答案，便于学生完成后复盘"></textarea></label>
          <div class="warning-box"><strong>内容边界</strong><p>本页面创建的是教学人员维护的题目。除非有明确来源授权，不得把 Junyi Exercise metadata、第三方教材或网络内容标注成平台自有题目。</p></div>
          <div class="intro-actions"><button class="primary-button">{{ editingId ? '保存修改' : '创建题目' }}</button><button v-if="editingId" type="button" class="secondary-button" @click="resetForm">取消编辑</button></div>
        </form>
      </section>
    </div>
  </section>
</template>
