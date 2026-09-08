<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const storedSession = (() => {
  try {
    return JSON.parse(localStorage.getItem('edu-session') || 'null')
  } catch {
    return null
  }
})()

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/+$/, '')

const session = ref(storedSession)
const view = ref(storedSession ? 'courses' : 'login')
const notice = ref('')
const error = ref('')
const loading = ref(false)
const courses = ref([])
const selectedCourse = ref(null)
const knowledgePoints = ref([])
const exerciseUnits = ref([])
const activeQuestion = ref(null)
const selectedOptionKeys = ref([])
const answerResult = ref(null)
const answerHistory = ref([])
const clientRequestId = ref('')

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '' })

const admin = reactive({
  course: { id: '', courseCode: '', courseName: '', description: '', status: 'ACTIVE' },
  area: { id: '', courseId: '', areaCode: '', areaName: '', sourceType: 'PLATFORM', externalId: '', status: 'ACTIVE' },
  point: { id: '', courseId: '', areaId: '', knowledgeCode: '', knowledgeName: '', sourceType: 'PLATFORM', externalId: '', mappingStatus: 'MAPPED', status: 'ACTIVE' },
  exercise: { id: '', courseId: '', exerciseCode: '', exerciseName: '', sourceType: 'PLATFORM', externalId: '', identityStatus: 'RESOLVED', mappingStatus: 'UNMAPPED', difficulty: '', status: 'ACTIVE' },
  question: { id: '', exerciseUnitId: '', questionType: 'SINGLE_CHOICE', stem: '', answerOptionKeys: 'A', explanation: '', difficulty: '', status: 'ACTIVE', optionsJson: '[\n  {"optionKey":"A","optionText":"","sortOrder":1},\n  {"optionKey":"B","optionText":"","sortOrder":2}\n]' },
  mapping: { exerciseUnitId: '', knowledgePointId: '', mappingSource: 'MANUAL', confidence: '1.0000', verified: false },
  catalogCourseId: '',
  areas: [],
  points: [],
  exercises: [],
  questions: [],
  seedJson: '{\n  "courseCode": "MATH-101",\n  "courseName": "Mathematics",\n  "areas": [],\n  "topics": [],\n  "exercises": []\n}',
  seedResult: null
})

const graph = reactive({
  courseId: '',
  data: null,
  search: '',
  selected: null,
  pathFrom: '',
  pathTo: '',
  pathResult: null
})

const graphAdmin = reactive({
  courseId: '',
  versions: [],
  selectedVersion: null,
  relations: [],
  validationIssues: [],
  evidence: [],
  relationEvidence: [],
  version: { description: '', copyActive: false },
  manual: { sourceKnowledgePointId: '', targetKnowledgePointId: '', confidence: '1.0000' },
  evidenceJson: '[\n  {\n    "externalEvidenceId": "junyi-row-1",\n    "sourceExerciseExternalId": "exercise-a",\n    "targetExerciseExternalId": "exercise-b",\n    "rawPayload": { "source": "Junyi prerequisite" }\n  }\n]',
  importResult: null,
  reresolution: { evidenceIds: '', triggerType: 'MAPPING_CHANGE', result: null, history: [] }
})

const personalization = reactive({
  courseId: '',
  mastery: [],
  masteryHistory: [],
  recommendation: null,
  targetKnowledgePointId: '',
  learningPath: null
})

const teacher = reactive({
  courses: [],
  selectedCourseId: '',
  from: '',
  to: '',
  overview: null,
  knowledgePoints: [],
  heatmap: null,
  highErrors: [],
  heatmapPage: 0,
  heatmapSize: 25,
  minimumAttempts: 5,
  student: null
})

const teacherAssignments = reactive({
  courseId: '',
  teacherId: '',
  assignmentRole: 'INSTRUCTOR',
  items: []
})

const authenticated = computed(() => Boolean(session.value?.accessToken))
const roles = computed(() => session.value?.user?.roles || [])
const isStudent = computed(() => roles.value.includes('STUDENT'))
const canTeach = computed(() => roles.value.includes('SYSTEM_ADMIN') || roles.value.includes('TEACH_ADMIN') || roles.value.includes('TEACHER'))
const canManageCourses = computed(() => roles.value.includes('SYSTEM_ADMIN') || roles.value.includes('TEACH_ADMIN'))
const canManage = computed(() => canTeach.value)
const canImport = computed(() => roles.value.includes('SYSTEM_ADMIN'))
const filteredGraphNodes = computed(() => {
  const nodes = graph.data?.nodes || []
  const query = graph.search.trim().toLowerCase()
  if (!query) return nodes
  return nodes.filter(node => `${node.knowledgeCode} ${node.knowledgeName}`.toLowerCase().includes(query))
})
const weakKnowledge = computed(() => personalization.mastery.filter(item =>
  item.status === 'OBSERVED' && item.masteryScore !== null && Number(item.masteryScore) < 0.7
))

function setMessage(message = '', failure = '') {
  notice.value = message
  error.value = failure
}

function newClientRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `answer-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function rememberSession(value) {
  session.value = value
  if (value) localStorage.setItem('edu-session', JSON.stringify(value))
  else localStorage.removeItem('edu-session')
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (session.value?.accessToken) headers.Authorization = `Bearer ${session.value.accessToken}`
  if (options.body) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 'OK') {
    if (response.status === 401) logout()
    throw new Error(payload?.message || `Request failed (${response.status})`)
  }
  return payload.data
}

async function run(action, successMessage = '') {
  loading.value = true
  setMessage()
  try {
    const result = await action()
    if (successMessage) notice.value = successMessage
    return result
  } catch (reason) {
    error.value = reason.message || '请求失败'
    return null
  } finally {
    loading.value = false
  }
}

async function login() {
  const result = await run(() => api('/auth/login', { method: 'POST', body: JSON.stringify(loginForm) }))
  if (result) {
    rememberSession(result)
    view.value = 'courses'
    await loadCourses()
  }
}

async function register() {
  const result = await run(() => api('/auth/register', { method: 'POST', body: JSON.stringify(registerForm) }))
  if (result) {
    rememberSession(result)
    view.value = 'courses'
    await loadCourses()
  }
}

function logout() {
  rememberSession(null)
  selectedCourse.value = null
  activeQuestion.value = null
  answerResult.value = null
  view.value = 'login'
}

async function loadCourses() {
  const result = await run(() => api('/courses'))
  if (result) courses.value = result
}

async function enterCourse(course) {
  if (isStudent.value) {
    const enrolled = await run(() => api(`/courses/${course.id}/enroll`, { method: 'POST' }))
    if (!enrolled) return
  }
  const loadedCourse = await run(() => api(`/courses/${course.id}`))
  if (!loadedCourse) return
  selectedCourse.value = loadedCourse
  knowledgePoints.value = await api(`/courses/${course.id}/knowledge-points`).catch(reason => {
    error.value = reason.message
    return []
  })
  exerciseUnits.value = await api(`/exercise-units?courseId=${course.id}`).catch(reason => {
    error.value = reason.message
    return []
  })
  view.value = 'course'
}

function teacherWindowQuery() {
  if (Boolean(teacher.from) !== Boolean(teacher.to)) {
    error.value = '时间窗口必须同时填写开始和结束，或全部留空以使用最近 30 天。'
    return null
  }
  const parameters = new URLSearchParams()
  if (teacher.from && teacher.to) {
    const from = new Date(teacher.from)
    const to = new Date(teacher.to)
    if (Number.isNaN(from.valueOf()) || Number.isNaN(to.valueOf()) || from >= to) {
      error.value = '请输入有效且结束晚于开始的时间窗口。'
      return null
    }
    parameters.set('from', from.toISOString())
    parameters.set('to', to.toISOString())
  }
  return parameters
}

async function openTeacherWorkspace() {
  const coursesForTeacher = await run(() => api('/teacher/courses'))
  if (!coursesForTeacher) return
  teacher.courses = coursesForTeacher
  if (!coursesForTeacher.some(course => String(course.courseId) === String(teacher.selectedCourseId))) {
    teacher.selectedCourseId = coursesForTeacher[0] ? String(coursesForTeacher[0].courseId) : ''
  }
  teacher.student = null
  view.value = 'teacher'
  if (teacher.selectedCourseId) await loadTeacherAnalytics()
}

async function loadTeacherAnalytics() {
  const courseId = numberOrNull(teacher.selectedCourseId)
  if (!courseId) {
    error.value = '请选择已授权课程。'
    return
  }
  const windowParameters = teacherWindowQuery()
  if (!windowParameters) return
  const analyticsParameters = windowParameters.toString()
  const withWindow = analyticsParameters ? `?${analyticsParameters}` : ''
  const errorParameters = new URLSearchParams(windowParameters)
  errorParameters.set('minimumAttempts', String(teacher.minimumAttempts || 5))
  const loaded = await run(() => Promise.all([
    api(`/teacher/courses/${courseId}/analytics/overview${withWindow}`),
    api(`/teacher/courses/${courseId}/analytics/knowledge-points${withWindow}`),
    api(`/teacher/courses/${courseId}/analytics/mastery-heatmap?page=${teacher.heatmapPage}&size=${teacher.heatmapSize}`),
    api(`/teacher/courses/${courseId}/analytics/questions/errors?${errorParameters}`)
  ]))
  if (loaded) {
    const [overview, knowledgePoints, heatmap, highErrors] = loaded
    teacher.overview = overview
    teacher.knowledgePoints = knowledgePoints.items
    teacher.heatmap = heatmap
    teacher.highErrors = highErrors.items
    teacher.student = null
    view.value = 'teacher'
  }
}

async function changeTeacherHeatmapPage(delta) {
  const nextPage = teacher.heatmapPage + delta
  if (nextPage < 0) return
  teacher.heatmapPage = nextPage
  await loadTeacherAnalytics()
}

async function loadTeacherStudent(studentId) {
  const courseId = numberOrNull(teacher.selectedCourseId)
  if (!courseId) return
  const result = await run(() => api(`/teacher/courses/${courseId}/students/${studentId}/analytics`))
  if (result) {
    teacher.student = result
    view.value = 'teacher-student'
  }
}

async function filterExercises(pointId) {
  if (!selectedCourse.value) return
  const suffix = pointId ? `&knowledgePointId=${pointId}` : ''
  const result = await run(() => api(`/exercise-units?courseId=${selectedCourse.value.id}${suffix}`))
  if (result) exerciseUnits.value = result
}

async function startExercise(exercise) {
  const question = await run(() => api(`/exercise-units/${exercise.id}/questions/next`))
  if (!question) return
  activeQuestion.value = question
  selectedOptionKeys.value = []
  answerResult.value = null
  clientRequestId.value = newClientRequestId()
  view.value = 'question'
}

function toggleOption(key, checked) {
  if (activeQuestion.value?.questionType !== 'MULTIPLE_CHOICE') {
    selectedOptionKeys.value = checked ? [key] : []
    return
  }
  selectedOptionKeys.value = checked
    ? [...new Set([...selectedOptionKeys.value, key])]
    : selectedOptionKeys.value.filter(value => value !== key)
}

async function submitAnswer() {
  if (!activeQuestion.value || selectedOptionKeys.value.length === 0) {
    error.value = '请选择答案后再提交。'
    return
  }
  const result = await run(() => api(`/questions/${activeQuestion.value.id}/answers`, {
    method: 'POST',
    body: JSON.stringify({
      selectedOptionKeys: selectedOptionKeys.value,
      durationMs: 0,
      clientRequestId: clientRequestId.value
    })
  }))
  if (result) answerResult.value = result
}

async function loadHistory() {
  const result = await run(() => api('/learning/answer-history'))
  if (result) {
    answerHistory.value = result
    view.value = 'history'
  }
}

function personalizationCourseId() {
  return numberOrNull(personalization.courseId) || selectedCourse.value?.id
}

async function loadPersonalization() {
  const courseId = personalizationCourseId()
  if (!courseId) {
    error.value = '请先进入课程，或输入已加入课程的 ID。'
    return
  }
  const loaded = await run(async () => {
    const [mastery, masteryHistory] = await Promise.all([
      api(`/courses/${courseId}/mastery`),
      api(`/courses/${courseId}/mastery/history`)
    ])
    let recommendation = null
    try {
      recommendation = await api(`/courses/${courseId}/recommendations/latest`)
    } catch (reason) {
      if (!String(reason.message || '').includes('no recommendation snapshot')) throw reason
    }
    return { mastery, masteryHistory, recommendation }
  })
  if (loaded) {
    personalization.courseId = String(courseId)
    personalization.mastery = loaded.mastery.items
    personalization.masteryHistory = loaded.masteryHistory
    personalization.recommendation = loaded.recommendation
    personalization.learningPath = null
    view.value = 'personalization'
  }
}

async function generateRecommendations() {
  const courseId = personalizationCourseId()
  if (!courseId) {
    error.value = '请先选择或输入课程 ID。'
    return
  }
  const recommendation = await run(
    () => api(`/courses/${courseId}/recommendations`, { method: 'POST' }),
    '推荐快照已生成；它不会修改 Published Graph。'
  )
  if (recommendation) {
    personalization.courseId = String(courseId)
    personalization.recommendation = recommendation
  }
}

async function loadLearningPath() {
  const targetKnowledgePointId = numberOrNull(personalization.targetKnowledgePointId)
  if (!targetKnowledgePointId) {
    error.value = '请输入目标 KnowledgePoint ID。'
    return
  }
  const result = await run(() => api(`/knowledge-points/${targetKnowledgePointId}/learning-path`))
  if (result) personalization.learningPath = result
}

function numberOrNull(value) {
  return value === '' || value === null || value === undefined ? null : Number(value)
}

function resetCourseForm() {
  Object.assign(admin.course, { id: '', courseCode: '', courseName: '', description: '', status: 'ACTIVE' })
}

function resetAreaForm() {
  Object.assign(admin.area, { id: '', courseId: '', areaCode: '', areaName: '', sourceType: 'PLATFORM', externalId: '', status: 'ACTIVE' })
}

function resetPointForm() {
  Object.assign(admin.point, { id: '', courseId: '', areaId: '', knowledgeCode: '', knowledgeName: '', sourceType: 'PLATFORM', externalId: '', mappingStatus: 'MAPPED', status: 'ACTIVE' })
}

function resetExerciseForm() {
  Object.assign(admin.exercise, { id: '', courseId: '', exerciseCode: '', exerciseName: '', sourceType: 'PLATFORM', externalId: '', identityStatus: 'RESOLVED', mappingStatus: 'UNMAPPED', difficulty: '', status: 'ACTIVE' })
}

function resetQuestionForm() {
  Object.assign(admin.question, { id: '', exerciseUnitId: '', questionType: 'SINGLE_CHOICE', stem: '', answerOptionKeys: 'A', explanation: '', difficulty: '', status: 'ACTIVE', optionsJson: '[\n  {"optionKey":"A","optionText":"","sortOrder":1},\n  {"optionKey":"B","optionText":"","sortOrder":2}\n]' })
}

async function saveCourse() {
  const payload = { ...admin.course }
  delete payload.id
  const method = admin.course.id ? 'PUT' : 'POST'
  const path = admin.course.id ? `/admin/courses/${admin.course.id}` : '/admin/courses'
  const result = await run(() => api(path, { method, body: JSON.stringify(payload) }), '课程已保存。')
  if (result) {
    resetCourseForm()
    await loadCourses()
  }
}

function editCourse(course) {
  Object.assign(admin.course, course)
}

async function disableCourse(course) {
  const result = await run(() => api(`/admin/courses/${course.id}`, { method: 'DELETE' }), '课程已禁用。')
  if (result !== null) await loadCourses()
}

async function loadTeacherAssignments() {
  const courseId = numberOrNull(teacherAssignments.courseId)
  if (!courseId) {
    error.value = '请输入课程 ID。'
    return
  }
  const result = await run(() => api(`/admin/courses/${courseId}/teachers`))
  if (result) teacherAssignments.items = result
}

async function saveTeacherAssignment() {
  const courseId = numberOrNull(teacherAssignments.courseId)
  const teacherId = numberOrNull(teacherAssignments.teacherId)
  if (!courseId || !teacherId) {
    error.value = '请输入课程 ID 和教师 ID。'
    return
  }
  const result = await run(() => api(`/admin/courses/${courseId}/teachers`, {
    method: 'POST',
    body: JSON.stringify({ teacherId, assignmentRole: teacherAssignments.assignmentRole })
  }), '教师课程分配已保存。')
  if (result) {
    teacherAssignments.teacherId = ''
    await loadTeacherAssignments()
  }
}

async function deactivateTeacherAssignment(assignment) {
  const courseId = numberOrNull(teacherAssignments.courseId)
  if (!courseId) return
  const result = await run(
    () => api(`/admin/courses/${courseId}/teachers/${assignment.teacherId}`, { method: 'DELETE' }),
    '教师课程分配已停用。'
  )
  if (result !== null) await loadTeacherAssignments()
}

async function loadCatalog() {
  const courseId = numberOrNull(admin.catalogCourseId)
  if (!courseId) {
    error.value = '请输入课程 ID。'
    return
  }
  const loaded = await run(async () => Promise.all([
    api(`/admin/knowledge-areas?courseId=${courseId}`),
    api(`/admin/knowledge-points?courseId=${courseId}`),
    api(`/admin/exercise-units?courseId=${courseId}`)
  ]))
  if (loaded) {
    const [areas, points, exercises] = loaded
    admin.areas = areas
    admin.points = points
    admin.exercises = exercises
  }
}

async function saveArea() {
  const payload = { ...admin.area, courseId: numberOrNull(admin.area.courseId) }
  delete payload.id
  const path = admin.area.id ? `/admin/knowledge-areas/${admin.area.id}` : '/admin/knowledge-areas'
  const result = await run(() => api(path, { method: admin.area.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }), '知识领域已保存。')
  if (result) await loadCatalog()
}

function editArea(area) {
  Object.assign(admin.area, area)
}

async function disableArea(area) {
  const result = await run(() => api(`/admin/knowledge-areas/${area.id}`, { method: 'DELETE' }), '知识领域已禁用。')
  if (result !== null) await loadCatalog()
}

async function savePoint() {
  const payload = { ...admin.point, courseId: numberOrNull(admin.point.courseId), areaId: numberOrNull(admin.point.areaId) }
  delete payload.id
  const path = admin.point.id ? `/admin/knowledge-points/${admin.point.id}` : '/admin/knowledge-points'
  const result = await run(() => api(path, { method: admin.point.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }), '知识点已保存。')
  if (result) await loadCatalog()
}

function editPoint(point) {
  Object.assign(admin.point, { ...point, areaId: point.areaId || '' })
}

async function disablePoint(point) {
  const result = await run(() => api(`/admin/knowledge-points/${point.id}`, { method: 'DELETE' }), '知识点已禁用。')
  if (result !== null) await loadCatalog()
}

async function saveExercise() {
  const payload = { ...admin.exercise, courseId: numberOrNull(admin.exercise.courseId), difficulty: numberOrNull(admin.exercise.difficulty) }
  delete payload.id
  const path = admin.exercise.id ? `/admin/exercise-units/${admin.exercise.id}` : '/admin/exercise-units'
  const result = await run(() => api(path, { method: admin.exercise.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }), 'ExerciseUnit 已保存。')
  if (result) await loadCatalog()
}

function editExercise(exercise) {
  Object.assign(admin.exercise, { ...exercise, difficulty: exercise.difficulty || '' })
}

async function disableExercise(exercise) {
  const result = await run(() => api(`/admin/exercise-units/${exercise.id}`, { method: 'DELETE' }), 'ExerciseUnit 已禁用。')
  if (result !== null) await loadCatalog()
}

async function saveMapping() {
  const exerciseUnitId = numberOrNull(admin.mapping.exerciseUnitId)
  const payload = {
    knowledgePointId: numberOrNull(admin.mapping.knowledgePointId),
    mappingSource: admin.mapping.mappingSource,
    confidence: numberOrNull(admin.mapping.confidence),
    verified: admin.mapping.verified
  }
  if (!exerciseUnitId || !payload.knowledgePointId) {
    error.value = '请输入 ExerciseUnit 和 KnowledgePoint ID。'
    return
  }
  const result = await run(() => api(`/admin/exercise-units/${exerciseUnitId}/knowledge-points`, { method: 'POST', body: JSON.stringify(payload) }), '映射已保存。')
  if (result) await loadCatalog()
}

async function removeMapping(exercise, mapping) {
  const result = await run(() => api(`/admin/exercise-units/${exercise.id}/knowledge-points/${mapping.knowledgePointId}`, { method: 'DELETE' }), '映射已移除。')
  if (result !== null) await loadCatalog()
}

async function loadQuestions() {
  const exerciseUnitId = numberOrNull(admin.question.exerciseUnitId)
  if (!exerciseUnitId) {
    error.value = '请输入 ExerciseUnit ID。'
    return
  }
  const result = await run(() => api(`/admin/questions?exerciseUnitId=${exerciseUnitId}`))
  if (result) admin.questions = result
}

async function saveQuestion() {
  let options
  try {
    options = JSON.parse(admin.question.optionsJson)
  } catch {
    error.value = '选项必须是合法 JSON 数组。'
    return
  }
  const payload = {
    exerciseUnitId: numberOrNull(admin.question.exerciseUnitId),
    questionType: admin.question.questionType,
    stem: admin.question.stem,
    answerOptionKeys: admin.question.answerOptionKeys.split(',').map(value => value.trim()).filter(Boolean),
    explanation: admin.question.explanation,
    difficulty: numberOrNull(admin.question.difficulty),
    status: admin.question.status,
    options
  }
  const path = admin.question.id ? `/admin/questions/${admin.question.id}` : '/admin/questions'
  const result = await run(() => api(path, { method: admin.question.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }), '题目已保存。')
  if (result) await loadQuestions()
}

function editQuestion(question) {
  Object.assign(admin.question, {
    ...question,
    answerOptionKeys: question.answerOptionKeys.join(','),
    difficulty: question.difficulty || '',
    optionsJson: JSON.stringify(question.options, null, 2)
  })
}

async function disableQuestion(question) {
  const result = await run(() => api(`/admin/questions/${question.id}`, { method: 'DELETE' }), '题目已禁用。')
  if (result !== null) await loadQuestions()
}

async function runSeed(mode) {
  let payload
  try {
    payload = JSON.parse(admin.seedJson)
  } catch {
    error.value = 'Seed 输入必须是合法 JSON。'
    return
  }
  const result = await run(() => api(`/admin/seed-imports/${mode}`, { method: 'POST', body: JSON.stringify(payload) }))
  if (result) admin.seedResult = result
}

async function openGraph() {
  const courseId = numberOrNull(graph.courseId) || selectedCourse.value?.id
  if (!courseId) {
    error.value = '请先进入课程，或输入已加入课程的 ID。'
    return
  }
  const result = await run(() => api(`/courses/${courseId}/graph`))
  if (result) {
    graph.courseId = String(courseId)
    graph.data = result
    graph.selected = null
    graph.pathResult = null
    view.value = 'graph'
  }
}

async function inspectGraphNode(node, direction) {
  const suffix = direction === 'prerequisites' ? 'prerequisites' : 'successors'
  const result = await run(() => api(`/knowledge-points/${node.id}/${suffix}`))
  if (result) graph.selected = { node, direction, graph: result }
}

async function findGraphPath() {
  const courseId = numberOrNull(graph.courseId)
  const from = numberOrNull(graph.pathFrom)
  const to = numberOrNull(graph.pathTo)
  if (!courseId || !from || !to) {
    error.value = '请输入课程 ID、起点和终点 KnowledgePoint ID。'
    return
  }
  const result = await run(() => api(`/courses/${courseId}/graph/path?from=${from}&to=${to}`))
  if (result) graph.pathResult = result
}

async function loadGraphAdmin() {
  const courseId = numberOrNull(graphAdmin.courseId)
  if (!courseId) {
    error.value = '请输入课程 ID。'
    return
  }
  const loaded = await run(async () => Promise.all([
    api(`/admin/graph-versions?courseId=${courseId}`),
    api(`/admin/evidence?courseId=${courseId}`)
  ]))
  if (loaded) {
    const [versions, evidence] = loaded
    graphAdmin.versions = versions
    graphAdmin.evidence = evidence
  }
}

async function createGraphVersion() {
  const courseId = numberOrNull(graphAdmin.courseId)
  if (!courseId) {
    error.value = '请输入课程 ID。'
    return
  }
  const result = await run(() => api('/admin/graph-versions', {
    method: 'POST',
    body: JSON.stringify({
      courseId,
      description: graphAdmin.version.description || null,
      copyActive: graphAdmin.version.copyActive
    })
  }), 'GraphVersion 已创建。')
  if (result) {
    graphAdmin.version.description = ''
    await loadGraphAdmin()
    await selectGraphVersion(result.id)
  }
}

async function selectGraphVersion(versionId) {
  const loaded = await run(async () => Promise.all([
    api(`/admin/graph-versions/${versionId}`),
    api(`/admin/graph-versions/${versionId}/relations`),
    api(`/admin/graph-versions/${versionId}/validation-issues`)
  ]))
  if (loaded) {
    const [version, relations, validationIssues] = loaded
    graphAdmin.selectedVersion = version
    graphAdmin.relations = relations
    graphAdmin.validationIssues = validationIssues
    graphAdmin.relationEvidence = []
  }
}

async function addManualGraphRelation() {
  if (!graphAdmin.selectedVersion) {
    error.value = '请先选择 Draft GraphVersion。'
    return
  }
  const sourceKnowledgePointId = numberOrNull(graphAdmin.manual.sourceKnowledgePointId)
  const targetKnowledgePointId = numberOrNull(graphAdmin.manual.targetKnowledgePointId)
  if (!sourceKnowledgePointId || !targetKnowledgePointId) {
    error.value = '请输入两个 KnowledgePoint ID。'
    return
  }
  const result = await run(() => api(`/admin/graph-versions/${graphAdmin.selectedVersion.id}/relations`, {
    method: 'POST',
    body: JSON.stringify({
      sourceKnowledgePointId,
      targetKnowledgePointId,
      confidence: numberOrNull(graphAdmin.manual.confidence)
    })
  }), '人工关系已加入 Draft。')
  if (result) await selectGraphVersion(graphAdmin.selectedVersion.id)
}

async function reviewGraphRelation(relation, reviewStatus) {
  if (!graphAdmin.selectedVersion) return
  const result = await run(() => api(
    `/admin/graph-versions/${graphAdmin.selectedVersion.id}/relations/${relation.id}/review`,
    { method: 'PUT', body: JSON.stringify({ reviewStatus }) }
  ), `关系已${reviewStatus === 'APPROVED' ? '批准' : '拒绝'}。`)
  if (result) await selectGraphVersion(graphAdmin.selectedVersion.id)
}

async function inspectRelationEvidence(relation) {
  if (!graphAdmin.selectedVersion) return
  const result = await run(() => api(
    `/admin/graph-versions/${graphAdmin.selectedVersion.id}/relations/${relation.id}/evidence`
  ))
  if (result) graphAdmin.relationEvidence = result
}

async function importGraphEvidence(mode) {
  if (!graphAdmin.selectedVersion) {
    error.value = '请先选择 Draft GraphVersion。'
    return
  }
  let parsed
  try {
    parsed = JSON.parse(graphAdmin.evidenceJson)
  } catch {
    error.value = 'Evidence 输入必须是合法 JSON。'
    return
  }
  const payload = Array.isArray(parsed) ? { evidence: parsed } : parsed
  const result = await run(() => api(
    `/admin/graph-versions/${graphAdmin.selectedVersion.id}/evidence-imports/${mode}`,
    { method: 'POST', body: JSON.stringify(payload) }
  ), mode === 'apply' ? 'Evidence 已导入 Draft。' : 'Evidence dry-run 已完成。')
  if (result) {
    graphAdmin.importResult = result
    await loadGraphAdmin()
    await selectGraphVersion(graphAdmin.selectedVersion.id)
  }
}

function reresolutionEvidenceIds() {
  const values = String(graphAdmin.reresolution.evidenceIds || '')
    .split(',').map(value => value.trim()).filter(Boolean).map(value => Number(value))
  if (!values.length || values.some(value => !Number.isInteger(value) || value <= 0)) {
    error.value = '请输入至少一个有效 Evidence ID，多个 ID 用逗号分隔。'
    return null
  }
  return values
}

function selectEvidenceForReresolution(evidence) {
  graphAdmin.reresolution.evidenceIds = String(evidence.id)
  graphAdmin.reresolution.result = null
}

async function reresolveEvidence(mode) {
  if (!graphAdmin.selectedVersion) {
    error.value = '请先选择一个可编辑 Draft GraphVersion。'
    return
  }
  const evidenceIds = reresolutionEvidenceIds()
  if (!evidenceIds) return
  const result = await run(() => api(
    `/admin/graph-versions/${graphAdmin.selectedVersion.id}/evidence-reresolutions/${mode}`,
    { method: 'POST', body: JSON.stringify({ evidenceIds, triggerType: graphAdmin.reresolution.triggerType }) }
  ), mode === 'apply' ? 'Evidence 当前解析与目标 Draft 已重协调；尚未发布图。' : 'Evidence 重解析 dry-run 已完成，未写入持久化数据。')
  if (result) {
    graphAdmin.reresolution.result = result
    if (mode === 'apply') {
      await loadGraphAdmin()
      await selectGraphVersion(graphAdmin.selectedVersion.id)
    }
  }
}

async function inspectEvidenceResolutionHistory(evidence) {
  const result = await run(() => api(`/admin/evidence/${evidence.id}/resolution-history`))
  if (result) {
    graphAdmin.reresolution.evidenceIds = String(evidence.id)
    graphAdmin.reresolution.history = result
  }
}

async function validateGraphVersion() {
  if (!graphAdmin.selectedVersion) return
  const result = await run(() => api(`/admin/graph-versions/${graphAdmin.selectedVersion.id}/validate`, { method: 'POST' }))
  if (result) {
    notice.value = result.status === 'READY' ? '校验通过，版本已 READY。' : '校验发现阻断问题，版本已标记为 VALIDATION_FAILED。'
    await selectGraphVersion(graphAdmin.selectedVersion.id)
  }
}

async function publishGraphVersion() {
  if (!graphAdmin.selectedVersion) return
  const result = await run(() => api(`/admin/graph-versions/${graphAdmin.selectedVersion.id}/publish`, { method: 'POST' }), '发布请求已写入 GRAPH_REBUILD_REQUEST outbox。')
  if (result) await selectGraphVersion(graphAdmin.selectedVersion.id)
}

async function retryGraphProjection() {
  if (!graphAdmin.selectedVersion) return
  const result = await run(() => api(`/admin/graph-versions/${graphAdmin.selectedVersion.id}/retry-projection`, { method: 'POST' }), '投影重试请求已写入 outbox。')
  if (result) await selectGraphVersion(graphAdmin.selectedVersion.id)
}

onMounted(() => {
  if (authenticated.value) loadCourses()
})
</script>

<template>
  <main>
    <header>
      <div>
        <h1>Edu-java</h1>
        <p>V0.7 Release Candidate · Platform Business Domain 全栈演示</p>
      </div>
      <nav v-if="authenticated">
        <button @click="view = 'courses'; loadCourses()">课程</button>
        <button @click="openGraph">知识图</button>
        <button v-if="isStudent" @click="loadPersonalization">我的学习</button>
        <button v-if="isStudent" @click="loadHistory">学习记录</button>
        <button v-if="canTeach" @click="openTeacherWorkspace">教师工作台</button>
        <button v-if="canManage" @click="view = 'admin'; loadCourses()">管理</button>
        <button class="secondary" @click="logout">退出</button>
      </nav>
    </header>

    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="view === 'login'" class="card narrow">
      <h2>登录</h2>
      <form @submit.prevent="login">
        <label>用户名 <input v-model="loginForm.username" required maxlength="64"></label>
        <label>密码 <input v-model="loginForm.password" type="password" required maxlength="128"></label>
        <button :disabled="loading">登录</button>
      </form>
      <p>没有账号？<button class="link" @click="view = 'register'">注册</button></p>
    </section>

    <section v-else-if="view === 'register'" class="card narrow">
      <h2>注册学生账号</h2>
      <form @submit.prevent="register">
        <label>用户名 <input v-model="registerForm.username" required minlength="3" maxlength="64"></label>
        <label>昵称 <input v-model="registerForm.nickname" maxlength="64"></label>
        <label>密码（至少 12 位） <input v-model="registerForm.password" type="password" required minlength="12" maxlength="128"></label>
        <button :disabled="loading">注册并进入课程</button>
      </form>
      <p>已有账号？<button class="link" @click="view = 'login'">登录</button></p>
    </section>

    <section v-else-if="view === 'courses'" class="card">
      <div class="section-title"><h2>已授权课程</h2><button class="secondary" @click="loadCourses">刷新</button></div>
      <p v-if="courses.length === 0">当前没有已授权的 ACTIVE 课程。</p>
      <article v-for="course in courses" :key="course.id" class="list-item">
        <div><strong>{{ course.courseCode }} · {{ course.courseName }}</strong><p>{{ course.description || '暂无课程说明。' }}</p></div>
        <button @click="enterCourse(course)">{{ isStudent ? '进入课程' : '查看课程' }}</button>
      </article>
    </section>

    <section v-else-if="view === 'teacher' && canTeach" class="card">
      <div class="section-title">
        <div>
          <h2>教师课程工作台</h2>
          <p>仅统计 Platform Business Domain 的课程、选课、作答、掌握度与推荐快照；不使用 Junyi 匿名 Research Student 数据。</p>
        </div>
        <button class="secondary" @click="openTeacherWorkspace">刷新课程</button>
      </div>
      <p v-if="teacher.courses.length === 0">当前没有可查看的 ACTIVE 课程。</p>
      <template v-else>
        <div class="inline-form">
          <label>课程
            <select v-model="teacher.selectedCourseId" @change="teacher.heatmapPage = 0; loadTeacherAnalytics()">
              <option v-for="course in teacher.courses" :key="course.courseId" :value="String(course.courseId)">
                {{ course.courseCode }} · {{ course.courseName }}（在读 {{ course.activeEnrollmentCount }} 人）
              </option>
            </select>
          </label>
          <label>开始时间（可空）<input v-model="teacher.from" type="datetime-local"></label>
          <label>结束时间（可空）<input v-model="teacher.to" type="datetime-local"></label>
          <label>高错题最小作答数 <input v-model.number="teacher.minimumAttempts" type="number" min="1"></label>
          <button @click="teacher.heatmapPage = 0; loadTeacherAnalytics()">加载分析</button>
        </div>

        <template v-if="teacher.overview">
          <p class="analytics-caption">时间窗口：{{ new Date(teacher.overview.window.from).toLocaleString() }} 至 {{ new Date(teacher.overview.window.to).toLocaleString() }}。</p>
          <div class="analytics-grid">
            <article class="metric"><small>ACTIVE 在读学生</small><strong>{{ teacher.overview.activeEnrolledStudents }}</strong></article>
            <article class="metric"><small>窗口内有活动学生</small><strong>{{ teacher.overview.studentsWithActivity }}</strong></article>
            <article class="metric"><small>作答次数</small><strong>{{ teacher.overview.attemptCount }}</strong></article>
            <article class="metric"><small>正确率</small><strong>{{ teacher.overview.correctRate === null ? '—' : `${(Number(teacher.overview.correctRate) * 100).toFixed(2)}%` }}</strong></article>
            <article class="metric"><small>ACTIVE 知识点</small><strong>{{ teacher.overview.activeKnowledgePointCount }}</strong></article>
            <article class="metric"><small>已观测掌握度学生</small><strong>{{ teacher.overview.observedMasteryStudents }}</strong></article>
          </div>
          <p>掌握度版本：{{ teacher.overview.masteryAlgorithmVersions.join('、') || '暂无已观测掌握度' }}。</p>
        </template>

        <section class="graph-detail">
          <h3>KnowledgePoint 学情</h3>
          <p>UNKNOWN 不伪造分数，且不计入平均掌握度。{{ teacher.knowledgePoints.length === 0 ? '当前没有 ACTIVE KnowledgePoint。' : '' }}</p>
          <article v-for="point in teacher.knowledgePoints" :key="point.knowledgePointId" class="admin-row">
            <span>
              <strong>{{ point.knowledgeCode }} · {{ point.knowledgeName }}</strong>
              <small>OBSERVED {{ point.observedStudentCount }} · UNKNOWN {{ point.unknownStudentCount }} · 弱项 {{ point.weakObservedStudentCount }} · 作答 {{ point.attemptCount }} · 正确 {{ point.correctCount }}</small>
            </span>
            <span>平均掌握度 {{ point.meanMastery === null ? '—' : Number(point.meanMastery).toFixed(4) }} · 正确率 {{ point.correctRate === null ? '—' : `${(Number(point.correctRate) * 100).toFixed(2)}%` }}</span>
          </article>
        </section>

        <section v-if="teacher.heatmap" class="graph-detail">
          <div class="section-title"><h3>Student × KnowledgePoint 掌握度热力表</h3><span>第 {{ teacher.heatmap.page + 1 }} 页，共 {{ teacher.heatmap.totalStudents }} 名 ACTIVE 学生</span></div>
          <div class="heatmap-wrap">
            <table>
              <thead><tr><th>学生</th><th v-for="point in teacher.heatmap.knowledgePoints" :key="point.knowledgePointId">{{ point.knowledgeCode }}</th></tr></thead>
              <tbody>
                <tr v-for="student in teacher.heatmap.students" :key="student.studentId">
                  <th><button class="link" @click="loadTeacherStudent(student.studentId)">{{ student.displayName }} #{{ student.studentId }}</button></th>
                  <td v-for="item in student.items" :key="item.knowledgePointId" :class="item.status === 'UNKNOWN' ? 'mastery-unknown' : 'mastery-observed'">
                    {{ item.status === 'UNKNOWN' ? 'UNKNOWN' : Number(item.masteryScore).toFixed(4) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="inline-actions">
            <button class="secondary" :disabled="teacher.heatmapPage === 0 || loading" @click="changeTeacherHeatmapPage(-1)">上一页</button>
            <button class="secondary" :disabled="(teacher.heatmapPage + 1) * teacher.heatmapSize >= teacher.heatmap.totalStudents || loading" @click="changeTeacherHeatmapPage(1)">下一页</button>
          </div>
        </section>

        <section class="graph-detail">
          <h3>高错题</h3>
          <p v-if="teacher.highErrors.length === 0">当前窗口内没有达到最小作答数的高错题。</p>
          <article v-for="question in teacher.highErrors" :key="question.questionId" class="admin-row">
            <span><strong>{{ question.exerciseCode }} · Question #{{ question.questionId }}</strong><small>{{ question.stemPreview }} · 关联：{{ question.associatedKnowledgePoints.map(point => point.knowledgeName).join('、') || 'UNMAPPED' }}</small></span>
            <span>错误 {{ question.wrongCount }}/{{ question.attemptCount }}（{{ (Number(question.wrongRate) * 100).toFixed(2) }}%）· {{ question.distinctStudents }} 人</span>
          </article>
        </section>
      </template>
    </section>

    <section v-else-if="view === 'teacher-student' && teacher.student" class="card">
      <button class="link" @click="view = 'teacher'">← 返回教师工作台</button>
      <div class="section-title"><div><h2>{{ teacher.student.displayName }} 的课程学情</h2><p>Student #{{ teacher.student.studentId }} · Course #{{ teacher.student.courseId }}</p></div><button class="secondary" @click="loadTeacherStudent(teacher.student.studentId)">刷新</button></div>
      <p>作答 {{ teacher.student.activity.attemptCount }} 次 · 正确 {{ teacher.student.activity.correctCount }} 次 · 正确率 {{ teacher.student.activity.correctRate === null ? '—' : `${(Number(teacher.student.activity.correctRate) * 100).toFixed(2)}%` }} · 最近活动 {{ teacher.student.activity.latestActivityAt ? new Date(teacher.student.activity.latestActivityAt).toLocaleString() : '—' }}</p>
      <section class="graph-detail">
        <h3>当前掌握度</h3>
        <article v-for="item in teacher.student.mastery" :key="item.knowledgePointId" class="admin-row"><span>{{ item.knowledgeCode }} · {{ item.knowledgeName }}</span><span>{{ item.status === 'UNKNOWN' ? 'UNKNOWN' : `${Number(item.masteryScore).toFixed(4)} · ${item.correctCount}/${item.attemptCount}` }}</span></article>
      </section>
      <section class="graph-detail">
        <h3>最近作答</h3>
        <p v-if="teacher.student.recentAnswers.length === 0">暂无作答记录。</p>
        <article v-for="answer in teacher.student.recentAnswers" :key="answer.answerRecordId" class="admin-row"><span>{{ answer.exerciseCode }} · {{ answer.stemPreview }}</span><span :class="answer.correct ? 'correct-text' : 'incorrect-text'">{{ answer.correct ? '正确' : '错误' }} · {{ new Date(answer.answeredAt).toLocaleString() }}</span></article>
      </section>
      <section class="graph-detail">
        <h3>掌握度变化历史</h3>
        <p v-if="teacher.student.masteryHistory.length === 0">暂无掌握度变化历史。</p>
        <article v-for="history in teacher.student.masteryHistory" :key="history.historyId" class="admin-row"><span>{{ history.knowledgeCode }} · {{ history.previousScore === null ? 'UNKNOWN' : Number(history.previousScore).toFixed(4) }} → {{ Number(history.newScore).toFixed(4) }}</span><span>{{ new Date(history.createdAt).toLocaleString() }}</span></article>
      </section>
      <section class="graph-detail">
        <h3>最新推荐上下文</h3>
        <p v-if="!teacher.student.latestRecommendation">暂无推荐快照。</p>
        <template v-else><p>GraphVersion #{{ teacher.student.latestRecommendation.graphVersionId }} · {{ teacher.student.latestRecommendation.masteryAlgorithmVersion }} · {{ teacher.student.latestRecommendation.recommendationRuleVersion }}</p><article v-for="item in teacher.student.latestRecommendation.items" :key="item.rank" class="admin-row"><span>{{ item.rank }}. {{ item.knowledgeName }}</span><span>{{ item.reasonCode }} · {{ item.masteryScore === null ? 'UNKNOWN' : Number(item.masteryScore).toFixed(4) }}</span></article></template>
      </section>
    </section>

    <section v-else-if="view === 'course' && selectedCourse" class="card">
      <button class="link" @click="view = 'courses'">← 返回课程</button>
      <h2>{{ selectedCourse.courseName }}</h2>
      <p>{{ selectedCourse.description || '暂无课程说明。' }}</p>
      <h3>知识主题</h3>
      <div class="chips"><button class="chip" @click="filterExercises(null)">全部</button><button v-for="point in knowledgePoints" :key="point.id" class="chip" @click="filterExercises(point.id)">{{ point.knowledgeName }}</button></div>
      <h3>ExerciseUnit</h3>
      <p v-if="exerciseUnits.length === 0">尚无可作答的 ExerciseUnit。</p>
      <article v-for="exercise in exerciseUnits" :key="exercise.id" class="list-item">
        <div><strong>{{ exercise.exerciseCode }} · {{ exercise.exerciseName }}</strong><p>{{ exercise.knowledgePoints.map(point => point.knowledgeName).join('、') || 'UNMAPPED' }}</p></div>
        <button @click="startExercise(exercise)">开始答题</button>
      </article>
    </section>

    <section v-else-if="view === 'graph'" class="card">
      <div class="section-title">
        <div><h2>Published Knowledge Graph</h2><p v-if="graph.data">当前 GraphVersion：#{{ graph.data.graphVersionId }}</p></div>
        <button class="secondary" @click="openGraph">刷新</button>
      </div>
      <label>课程 ID <input v-model="graph.courseId" type="number" @change="openGraph"></label>
      <p v-if="!graph.data">该课程尚无 Published Graph。只有投影成功后才可查询。</p>
      <template v-else>
        <label>搜索 KnowledgePoint <input v-model="graph.search" placeholder="按编码或名称搜索"></label>
        <div class="graph-layout">
          <section>
            <h3>知识点（{{ filteredGraphNodes.length }}/{{ graph.data.nodes.length }}）</h3>
            <article v-for="node in filteredGraphNodes" :key="node.id" class="graph-node">
              <div><strong>#{{ node.id }} · {{ node.knowledgeName }}</strong><small>{{ node.knowledgeCode }}</small></div>
              <span><button class="link" @click="inspectGraphNode(node, 'prerequisites')">前驱</button><button class="link" @click="inspectGraphNode(node, 'successors')">后继</button></span>
            </article>
          </section>
          <section>
            <h3>正式边（{{ graph.data.edges.length }}）</h3>
            <p v-if="graph.data.edges.length === 0">该版本没有已发布的先修边。</p>
            <article v-for="edge in graph.data.edges" :key="edge.relationId" class="graph-edge">
              #{{ edge.sourceKnowledgePointId }} → #{{ edge.targetKnowledgePointId }}
            </article>
          </section>
        </div>
        <section v-if="graph.selected" class="graph-detail">
          <h3>{{ graph.selected.node.knowledgeName }} 的{{ graph.selected.direction === 'prerequisites' ? '前驱' : '后继' }}</h3>
          <p>GraphVersion #{{ graph.selected.graph.graphVersionId }}</p>
          <p>{{ graph.selected.graph.nodes.map(node => `#${node.id} ${node.knowledgeName}`).join('、') || '无直接关系。' }}</p>
        </section>
        <section class="graph-detail">
          <h3>两点路径查询</h3>
          <div class="inline-form">
            <label>起点 KnowledgePoint ID <input v-model="graph.pathFrom" type="number"></label>
            <label>终点 KnowledgePoint ID <input v-model="graph.pathTo" type="number"></label>
            <button @click="findGraphPath">查询路径</button>
          </div>
          <p v-if="graph.pathResult">GraphVersion #{{ graph.pathResult.graphVersionId }}：{{ graph.pathResult.knowledgePointIds.length ? graph.pathResult.knowledgePointIds.map(id => `#${id}`).join(' → ') : '两点之间无有向先修路径。' }}</p>
        </section>
      </template>
    </section>

    <section v-else-if="view === 'personalization'" class="card">
      <div class="section-title">
        <div>
          <h2>我的知识掌握与学习建议</h2>
          <p>掌握度来自透明规则 <code>RULE_BETA_1_1_V1</code>，不是 AI 认知诊断。</p>
        </div>
        <button class="secondary" @click="loadPersonalization">刷新</button>
      </div>
      <div class="inline-form">
        <label>课程 ID <input v-model="personalization.courseId" type="number" @change="loadPersonalization"></label>
        <button @click="generateRecommendations">生成推荐快照</button>
      </div>

      <section class="graph-detail">
        <h3>我的知识掌握情况</h3>
        <p v-if="personalization.mastery.length === 0">该课程没有 ACTIVE KnowledgePoint，或尚未加载。</p>
        <div class="mastery-grid" v-else>
          <article v-for="item in personalization.mastery" :key="item.knowledgePointId" class="mastery-item">
            <strong>#{{ item.knowledgePointId }} · {{ item.knowledgeName }}</strong>
            <p v-if="item.status === 'UNKNOWN'">UNKNOWN（尚无答题历史，不显示伪掌握度）</p>
            <p v-else>规则掌握度：{{ Number(item.masteryScore).toFixed(4) }} · {{ item.correctCount }}/{{ item.attemptCount }}</p>
          </article>
        </div>
      </section>

      <section class="graph-detail">
        <h3>薄弱知识</h3>
        <p v-if="weakKnowledge.length === 0">当前没有已观测且低于 0.70 的知识点。</p>
        <p v-else>{{ weakKnowledge.map(item => `#${item.knowledgePointId} ${item.knowledgeName} (${Number(item.masteryScore).toFixed(4)})`).join('；') }}</p>
      </section>

      <section class="graph-detail">
        <h3>推荐练习与原因</h3>
        <p v-if="!personalization.recommendation">尚无推荐快照。生成时会绑定当前 active Published Graph。</p>
        <template v-else>
          <p>快照 #{{ personalization.recommendation.id }} · GraphVersion #{{ personalization.recommendation.graphVersionId }} · {{ personalization.recommendation.recommendationRuleVersion }}</p>
          <p v-if="personalization.recommendation.items.length === 0">没有同时满足知识点、活动 ExerciseUnit 和活动 Question 条件的可推荐练习。</p>
          <article v-for="item in personalization.recommendation.items" :key="item.id" class="list-item">
            <div>
              <strong>#{{ item.rank }} · {{ item.knowledgeName }}</strong>
              <p>{{ item.exerciseName || '仅知识点建议' }} · {{ item.reasonCode }}</p>
              <small>掌握度：{{ item.masteryStatus === 'UNKNOWN' ? 'UNKNOWN（无答题历史）' : Number(item.masteryScore).toFixed(4) }}</small>
              <pre class="explanation">{{ item.explanationJson }}</pre>
            </div>
          </article>
        </template>
      </section>

      <section class="graph-detail">
        <h3>目标知识点学习路径</h3>
        <div class="inline-form">
          <label>目标 KnowledgePoint ID <input v-model="personalization.targetKnowledgePointId" type="number"></label>
          <button @click="loadLearningPath">查询学习路径</button>
        </div>
        <template v-if="personalization.learningPath">
          <p>GraphVersion #{{ personalization.learningPath.graphVersionId }}</p>
          <p v-if="personalization.learningPath.nodes.length === 0">没有需要安排的未掌握节点。</p>
          <article v-for="node in personalization.learningPath.nodes" :key="node.knowledgePointId" class="list-item">
            <div>
              <strong>{{ node.order }}. #{{ node.knowledgePointId }} · {{ node.knowledgeName }}</strong>
              <p>{{ node.reasonCode }} · {{ node.masteryStatus === 'UNKNOWN' ? 'UNKNOWN（无答题历史）' : Number(node.masteryScore).toFixed(4) }}</p>
            </div>
            <span>{{ node.hasAvailableExercise ? `练习：${node.exerciseName}` : '暂无可用 ExerciseUnit' }}</span>
          </article>
        </template>
      </section>

      <section class="graph-detail">
        <h3>Mastery 变化历史</h3>
        <p v-if="personalization.masteryHistory.length === 0">还没有掌握度更新历史。</p>
        <article v-for="entry in personalization.masteryHistory" :key="entry.historyId" class="list-item">
          <div>#{{ entry.knowledgePointId }} · {{ entry.knowledgeName }}<p>{{ entry.previousScore === null ? 'UNKNOWN' : Number(entry.previousScore).toFixed(4) }} → {{ Number(entry.newScore).toFixed(4) }} · AnswerRecord #{{ entry.answerRecordId }}</p></div>
          <span>{{ new Date(entry.createdAt).toLocaleString() }}</span>
        </article>
      </section>
    </section>

    <section v-else-if="view === 'question' && activeQuestion" class="card narrow">
      <button class="link" @click="view = 'course'">← 返回 ExerciseUnit</button>
      <h2>题目</h2>
      <p class="stem">{{ activeQuestion.stem }}</p>
      <label v-for="option in activeQuestion.options" :key="option.id" class="option">
        <input :type="activeQuestion.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'" :name="`question-${activeQuestion.id}`" :checked="selectedOptionKeys.includes(option.optionKey)" @change="toggleOption(option.optionKey, $event.target.checked)">
        <strong>{{ option.optionKey }}.</strong> {{ option.optionText }}
      </label>
      <button v-if="!answerResult" :disabled="loading" @click="submitAnswer">提交答案</button>
      <section v-if="answerResult" class="result" :class="answerResult.correct ? 'correct' : 'incorrect'">
        <h3>{{ answerResult.correct ? '回答正确' : '回答不正确' }}</h3>
        <p>正确答案：{{ answerResult.correctOptionKeys.join('、') }}</p>
        <p>{{ answerResult.explanation || '暂无解析。' }}</p>
        <button @click="view = 'course'">返回课程</button>
      </section>
    </section>

    <section v-else-if="view === 'history'" class="card">
      <div class="section-title"><h2>基础学习记录</h2><button class="secondary" @click="loadHistory">刷新</button></div>
      <p v-if="answerHistory.length === 0">还没有答题记录。</p>
      <article v-for="record in answerHistory" :key="record.answerRecordId" class="list-item">
        <div>Question #{{ record.questionId }} · ExerciseUnit #{{ record.exerciseUnitId }}<p>{{ new Date(record.answeredAt).toLocaleString() }} · 第 {{ record.attemptNo }} 次</p></div>
        <span :class="record.correct ? 'correct-text' : 'incorrect-text'">{{ record.correct ? '正确' : '错误' }}</span>
      </article>
    </section>

    <section v-else-if="view === 'admin' && canManage" class="admin-grid">
      <section v-if="canManageCourses" class="card">
        <h2>课程管理</h2>
        <form @submit.prevent="saveCourse">
          <label>课程编码 <input v-model="admin.course.courseCode" required></label>
          <label>课程名称 <input v-model="admin.course.courseName" required></label>
          <label>说明 <textarea v-model="admin.course.description"></textarea></label>
          <label>状态 <select v-model="admin.course.status"><option>ACTIVE</option><option>DISABLED</option></select></label>
          <button>保存课程</button><button type="button" class="secondary" @click="resetCourseForm">新建</button>
        </form>
        <article v-for="course in courses" :key="course.id" class="admin-row"><span>#{{ course.id }} {{ course.courseCode }} · {{ course.courseName }}</span><span><button class="link" @click="editCourse(course)">编辑</button><button class="link danger" @click="disableCourse(course)">禁用</button></span></article>
        <h3>教师课程分配</h3>
        <div class="inline-form">
          <label>课程 ID <input v-model="teacherAssignments.courseId" type="number"></label>
          <label>教师 ID <input v-model="teacherAssignments.teacherId" type="number"></label>
          <label>分配角色 <select v-model="teacherAssignments.assignmentRole"><option>OWNER</option><option>INSTRUCTOR</option></select></label>
          <button @click="saveTeacherAssignment">保存分配</button>
          <button class="secondary" @click="loadTeacherAssignments">加载分配</button>
        </div>
        <article v-for="assignment in teacherAssignments.items" :key="assignment.id" class="admin-row"><span>{{ assignment.displayName }} (#{{ assignment.teacherId }}) · {{ assignment.assignmentRole }} · {{ assignment.status }}</span><button v-if="assignment.status === 'ACTIVE'" class="link danger" @click="deactivateTeacherAssignment(assignment)">停用</button></article>
      </section>

      <section class="card">
        <h2>目录管理</h2>
        <label>查看/维护的课程 ID <input v-model="admin.catalogCourseId" type="number"><button class="secondary" @click="loadCatalog">加载目录</button></label>
        <h3>KnowledgeArea</h3>
        <form @submit.prevent="saveArea">
          <label>课程 ID <input v-model="admin.area.courseId" type="number" required></label>
          <label>编码 <input v-model="admin.area.areaCode" required></label>
          <label>名称 <input v-model="admin.area.areaName" required></label>
          <label>来源 <input v-model="admin.area.sourceType" required></label>
          <label>来源 ID <input v-model="admin.area.externalId"></label>
          <button>保存 KnowledgeArea</button><button type="button" class="secondary" @click="resetAreaForm">新建</button>
        </form>
        <article v-for="area in admin.areas" :key="area.id" class="admin-row"><span>#{{ area.id }} {{ area.areaName }}</span><span><button class="link" @click="editArea(area)">编辑</button><button class="link danger" @click="disableArea(area)">禁用</button></span></article>
        <h3>KnowledgePoint</h3>
        <form @submit.prevent="savePoint">
          <label>课程 ID <input v-model="admin.point.courseId" type="number" required></label>
          <label>Area ID（可空） <input v-model="admin.point.areaId" type="number"></label>
          <label>编码 <input v-model="admin.point.knowledgeCode" required></label>
          <label>名称 <input v-model="admin.point.knowledgeName" required></label>
          <label>来源 <input v-model="admin.point.sourceType" required></label>
          <label>来源 ID <input v-model="admin.point.externalId"></label>
          <label>映射状态 <select v-model="admin.point.mappingStatus"><option>MAPPED</option><option>UNMAPPED</option></select></label>
          <button>保存 KnowledgePoint</button><button type="button" class="secondary" @click="resetPointForm">新建</button>
        </form>
        <article v-for="point in admin.points" :key="point.id" class="admin-row"><span>#{{ point.id }} {{ point.knowledgeName }}</span><span><button class="link" @click="editPoint(point)">编辑</button><button class="link danger" @click="disablePoint(point)">禁用</button></span></article>
      </section>

      <section class="card">
        <h2>ExerciseUnit 管理</h2>
        <form @submit.prevent="saveExercise">
          <label>课程 ID <input v-model="admin.exercise.courseId" type="number" required></label>
          <label>编码 <input v-model="admin.exercise.exerciseCode" required></label>
          <label>名称 <input v-model="admin.exercise.exerciseName" required></label>
          <label>来源 <input v-model="admin.exercise.sourceType" required></label>
          <label>来源 ID <input v-model="admin.exercise.externalId"></label>
          <label>难度 <input v-model="admin.exercise.difficulty" type="number" min="0" max="100" step="0.01"></label>
          <label>映射状态 <select v-model="admin.exercise.mappingStatus"><option>MAPPED</option><option>UNMAPPED</option></select></label>
          <button>保存 ExerciseUnit</button><button type="button" class="secondary" @click="resetExerciseForm">新建</button>
        </form>
        <article v-for="exercise in admin.exercises" :key="exercise.id" class="admin-row"><span>#{{ exercise.id }} {{ exercise.exerciseName }}<small v-if="exercise.knowledgePoints.length"> · {{ exercise.knowledgePoints.map(point => point.knowledgeName).join('、') }}</small></span><span><button class="link" @click="editExercise(exercise)">编辑</button><button class="link danger" @click="disableExercise(exercise)">禁用</button><button v-for="mapping in exercise.knowledgePoints" :key="mapping.id" class="link danger" @click="removeMapping(exercise, mapping)">移除 {{ mapping.knowledgeCode }}</button></span></article>
        <h3>ExerciseUnit → KnowledgePoint 映射</h3>
        <form @submit.prevent="saveMapping">
          <label>ExerciseUnit ID <input v-model="admin.mapping.exerciseUnitId" type="number" required></label>
          <label>KnowledgePoint ID <input v-model="admin.mapping.knowledgePointId" type="number" required></label>
          <label>映射来源 <input v-model="admin.mapping.mappingSource" required></label>
          <label>置信度 <input v-model="admin.mapping.confidence" type="number" min="0" max="1" step="0.0001"></label>
          <label><input v-model="admin.mapping.verified" type="checkbox"> 已人工验证</label>
          <button>保存映射</button>
        </form>
      </section>

      <section class="card">
        <h2>题库管理</h2>
        <form @submit.prevent="saveQuestion">
          <label>ExerciseUnit ID <input v-model="admin.question.exerciseUnitId" type="number" required><button type="button" class="secondary" @click="loadQuestions">加载题目</button></label>
          <label>题型 <select v-model="admin.question.questionType"><option>SINGLE_CHOICE</option><option>MULTIPLE_CHOICE</option><option>TRUE_FALSE</option></select></label>
          <label>题干 <textarea v-model="admin.question.stem" required></textarea></label>
          <label>正确选项（逗号分隔） <input v-model="admin.question.answerOptionKeys" required></label>
          <label>解析 <textarea v-model="admin.question.explanation"></textarea></label>
          <label>选项 JSON <textarea v-model="admin.question.optionsJson" class="code" required></textarea></label>
          <button>保存题目</button><button type="button" class="secondary" @click="resetQuestionForm">新建</button>
        </form>
        <article v-for="question in admin.questions" :key="question.id" class="admin-row"><span>#{{ question.id }} {{ question.questionType }} · {{ question.stem.slice(0, 24) }}</span><span><button class="link" @click="editQuestion(question)">编辑</button><button class="link danger" @click="disableQuestion(question)">禁用</button></span></article>
      </section>

      <section class="card wide">
        <h2>V0.3 Knowledge Relation Governance</h2>
        <p>Evidence 只形成 Draft candidate；只有通过校验、异步 Neo4j 投影成功的版本才会成为课程 active graph。</p>
        <div class="inline-form">
          <label>课程 ID <input v-model="graphAdmin.courseId" type="number"></label>
          <button class="secondary" @click="loadGraphAdmin">加载 GraphVersion 与 Evidence</button>
        </div>
        <form class="compact-form" @submit.prevent="createGraphVersion">
          <label>新版本说明 <input v-model="graphAdmin.version.description" maxlength="500"></label>
          <label><input v-model="graphAdmin.version.copyActive" type="checkbox"> 从当前 active Published Graph 复制为 Draft</label>
          <button>创建 GraphVersion</button>
        </form>
        <h3>GraphVersion</h3>
        <p v-if="graphAdmin.versions.length === 0">尚未加载或该课程没有版本。</p>
        <article v-for="version in graphAdmin.versions" :key="version.id" class="admin-row">
          <span>#{{ version.id }} · v{{ version.versionNo }} · {{ version.status }}<small v-if="version.active"> · 当前 active</small><small v-if="version.failureReason"> · {{ version.failureReason }}</small></span>
          <button class="link" @click="selectGraphVersion(version.id)">查看治理详情</button>
        </article>

        <template v-if="graphAdmin.selectedVersion">
          <section class="graph-detail">
            <h3>版本 #{{ graphAdmin.selectedVersion.id }} · {{ graphAdmin.selectedVersion.status }}</h3>
            <p v-if="graphAdmin.selectedVersion.failureReason" class="error">Projection/validation failure：{{ graphAdmin.selectedVersion.failureReason }}</p>
            <div class="inline-actions">
              <button @click="validateGraphVersion">运行 GraphValidator</button>
              <button class="secondary" :disabled="graphAdmin.selectedVersion.status !== 'READY'" @click="publishGraphVersion">发布</button>
              <button v-if="graphAdmin.selectedVersion.status === 'PROJECTION_FAILED'" class="secondary" @click="retryGraphProjection">重试投影</button>
            </div>
          </section>

          <section class="graph-detail">
            <h3>手工关系</h3>
            <div class="inline-form">
              <label>Source KnowledgePoint ID <input v-model="graphAdmin.manual.sourceKnowledgePointId" type="number"></label>
              <label>Target KnowledgePoint ID <input v-model="graphAdmin.manual.targetKnowledgePointId" type="number"></label>
              <label>Confidence <input v-model="graphAdmin.manual.confidence" type="number" min="0" max="1" step="0.0001"></label>
              <button @click="addManualGraphRelation">加入 Draft</button>
            </div>
            <article v-for="relation in graphAdmin.relations" :key="relation.id" class="admin-row">
              <span>#{{ relation.id }} {{ relation.sourceKnowledgeName }} → {{ relation.targetKnowledgeName }} · {{ relation.reviewStatus }} · Evidence {{ relation.evidenceCount }}</span>
              <span><button class="link" @click="inspectRelationEvidence(relation)">Evidence</button><button v-if="relation.reviewStatus === 'CANDIDATE'" class="link" @click="reviewGraphRelation(relation, 'APPROVED')">批准</button><button v-if="relation.reviewStatus !== 'REJECTED'" class="link danger" @click="reviewGraphRelation(relation, 'REJECTED')">拒绝</button></span>
            </article>
            <p v-if="graphAdmin.relationEvidence.length">当前关系 Evidence：{{ graphAdmin.relationEvidence.map(evidence => `#${evidence.id} ${evidence.resolutionStatus}`).join('；') }}</p>
          </section>

          <section class="graph-detail">
            <h3>Junyi raw prerequisite Evidence importer</h3>
            <p>外部 Exercise ID 必须唯一解析到 ExerciseUnit；未解析、歧义和同知识点自环会保留为 Evidence conflict，不会写入正式图。</p>
            <textarea v-model="graphAdmin.evidenceJson" class="code seed"></textarea>
            <button @click="importGraphEvidence('dry-run')">Dry-run</button>
            <button class="secondary" @click="importGraphEvidence('apply')">Apply</button>
            <pre v-if="graphAdmin.importResult">{{ JSON.stringify(graphAdmin.importResult, null, 2) }}</pre>
          </section>

          <section class="graph-detail">
            <h3>V0.5 Evidence 重解析与 Draft 影响</h3>
            <p>Raw external ID、source type 与 payload 不可改。仅重新计算当前 resolution，并只协调当前选择的 Draft；不会修改 Published/Archived 快照、active Neo4j 图或触发自动发布。</p>
            <div class="inline-form">
              <label>Evidence ID（逗号分隔）<input v-model="graphAdmin.reresolution.evidenceIds" placeholder="例如：12,13"></label>
              <label>触发原因
                <select v-model="graphAdmin.reresolution.triggerType">
                  <option>ADMIN_REQUEST</option><option>MAPPING_CHANGE</option><option>CONFLICT_REVIEW</option>
                </select>
              </label>
              <button @click="reresolveEvidence('dry-run')">重解析 Dry-run</button>
              <button class="secondary" @click="reresolveEvidence('apply')">Apply 到当前 Draft</button>
            </div>
            <template v-if="graphAdmin.reresolution.result">
              <p>{{ graphAdmin.reresolution.result.mode }} · changed {{ graphAdmin.reresolution.result.changedEvidenceCount }} · unchanged {{ graphAdmin.reresolution.result.unchangedEvidenceCount }}</p>
              <article v-for="item in graphAdmin.reresolution.result.evidence" :key="item.evidenceId" class="admin-row">
                <span>#{{ item.evidenceId }}：{{ item.currentResolution.resolutionStatus }} → {{ item.proposedResolution.resolutionStatus }}<small v-if="item.proposedResolution.conflictCode"> · conflict {{ item.proposedResolution.conflictCode }}</small></span>
                <small>Draft changes：{{ item.affectedDraftRelations.map(change => `${change.action} (${change.beforeEvidenceCount}→${change.afterEvidenceCount})`).join('；') || '无' }}</small>
              </article>
            </template>
            <h4>选中 Evidence 的 resolution history</h4>
            <p v-if="graphAdmin.reresolution.history.length === 0">从下方课程 Evidence 点击“历史”查看每次 apply 的 before/after、operator、trigger 与 Draft 关系影响。</p>
            <article v-for="history in graphAdmin.reresolution.history" :key="history.id" class="admin-row">
              <span>#{{ history.id }} · Draft #{{ history.graphVersionId }} · {{ history.oldResolutionStatus }} → {{ history.newResolutionStatus }}</span>
              <small>operator #{{ history.operatorId || '—' }} · {{ history.triggerType }} · {{ history.createdAt }}</small>
            </article>
          </section>

          <section class="graph-detail">
            <h3>Validation issues</h3>
            <p v-if="graphAdmin.validationIssues.length === 0">尚无已保存的校验问题。</p>
            <article v-for="issue in graphAdmin.validationIssues" :key="issue.id" class="admin-row"><span>{{ issue.severity }} · {{ issue.issueCode }} · relation #{{ issue.relationId || '—' }}</span><small>{{ issue.detailJson }}</small></article>
          </section>
        </template>

        <section class="graph-detail">
          <h3>课程 Evidence</h3>
          <p v-if="graphAdmin.evidence.length === 0">尚无 Evidence。</p>
          <article v-for="evidence in graphAdmin.evidence" :key="evidence.id" class="admin-row">
            <span>#{{ evidence.id }} {{ evidence.sourceExternalId }} → {{ evidence.targetExternalId }} · current {{ evidence.resolutionStatus }}<small> · {{ evidence.sourceType }} · raw {{ evidence.rawPayloadJson }}</small></span>
            <span><small>{{ evidence.conflictCode || 'resolved candidate eligible' }}</small><button class="link" @click="selectEvidenceForReresolution(evidence)">选入重解析</button><button class="link" @click="inspectEvidenceResolutionHistory(evidence)">历史</button></span>
          </article>
        </section>
      </section>

      <section v-if="canImport" class="card wide">
        <h2>受控 Junyi catalog seed importer</h2>
        <p>仅接受 Area、Topic 和 Exercise 元数据；不会接收用户、ProblemLog、先修关系或评分关系。</p>
        <textarea v-model="admin.seedJson" class="code seed"></textarea>
        <button @click="runSeed('dry-run')">Dry-run</button>
        <button class="secondary" @click="runSeed('apply')">Apply</button>
        <pre v-if="admin.seedResult">{{ JSON.stringify(admin.seedResult, null, 2) }}</pre>
      </section>
    </section>
  </main>
</template>

<style scoped>
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; background: #f4f7fb; color: #172033; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
main { max-width: 1180px; margin: 0 auto; padding: 28px 20px 56px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
h1, h2, h3, p { margin-top: 0; }
h1 { margin-bottom: 4px; }
header p { color: #536078; margin-bottom: 0; }
nav { display: flex; flex-wrap: wrap; gap: 8px; }
button { border: 0; border-radius: 7px; padding: 9px 13px; background: #205ecf; color: #fff; cursor: pointer; font: inherit; }
button:hover { background: #174ca9; }
button:disabled { opacity: .55; cursor: wait; }
button.secondary { background: #dbe5f5; color: #1c3155; }
button.secondary:hover { background: #c7d6ed; }
button.link { padding: 2px 4px; color: #205ecf; background: transparent; text-decoration: underline; }
button.link:hover { color: #174ca9; background: transparent; }
button.danger { color: #b42318; }
.card { margin: 18px 0; padding: 24px; background: #fff; border: 1px solid #d9e1ef; border-radius: 12px; box-shadow: 0 2px 8px rgb(29 54 97 / 6%); }
.narrow { max-width: 700px; margin-left: auto; margin-right: auto; }
.section-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
form { display: grid; gap: 12px; }
label { display: grid; gap: 5px; color: #33415d; font-size: .94rem; }
input, textarea, select { width: 100%; border: 1px solid #bfcce1; border-radius: 6px; padding: 9px; color: #172033; background: #fff; font: inherit; }
textarea { min-height: 76px; resize: vertical; }
.list-item, .admin-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 0; border-top: 1px solid #e6ebf3; }
.list-item p { margin: 5px 0 0; color: #66728a; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 18px; }
.chip { background: #e9f0ff; color: #174ca9; }
.option { display: block; margin: 12px 0; padding: 12px; border: 1px solid #d9e1ef; border-radius: 8px; cursor: pointer; }
.option input { width: auto; margin-right: 10px; }
.stem { font-size: 1.12rem; white-space: pre-wrap; }
.result { margin-top: 20px; padding: 16px; border-radius: 8px; }
.correct { background: #eaf8ef; border: 1px solid #a4d9b5; }
.incorrect { background: #fff0ef; border: 1px solid #f2b7b2; }
.correct-text { color: #137333; font-weight: 700; }
.incorrect-text { color: #b42318; font-weight: 700; }
.notice, .error { padding: 12px 14px; border-radius: 8px; }
.notice { color: #14532d; background: #dcfce7; }
.error { color: #991b1b; background: #fee2e2; }
.admin-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.admin-grid .card { margin: 0; }
.wide { grid-column: 1 / -1; }
.admin-row { font-size: .9rem; }
.code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; min-height: 155px; }
.seed { min-height: 250px; }
pre { overflow: auto; padding: 14px; background: #101827; color: #dbeafe; border-radius: 8px; }
.graph-layout { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin: 18px 0; }
.graph-node, .graph-edge { padding: 10px 0; border-top: 1px solid #e6ebf3; }
.graph-node { display: flex; justify-content: space-between; gap: 12px; }
.graph-node small, .admin-row small { display: block; color: #66728a; margin-top: 3px; overflow-wrap: anywhere; }
.graph-detail { margin-top: 18px; padding: 16px; border: 1px solid #d9e1ef; border-radius: 8px; background: #fafcff; }
.mastery-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.mastery-item { padding: 12px; border: 1px solid #d9e1ef; border-radius: 8px; background: #fff; }
.mastery-item p { margin: 6px 0 0; color: #536078; }
.analytics-caption { color: #536078; font-size: .9rem; margin: 14px 0; }
.analytics-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }
.metric { padding: 14px; border: 1px solid #d9e1ef; border-radius: 8px; background: #fff; }
.metric small { display: block; color: #536078; }
.metric strong { display: block; margin-top: 6px; font-size: 1.28rem; }
.heatmap-wrap { overflow: auto; margin: 12px 0; }
table { width: 100%; border-collapse: collapse; font-size: .86rem; }
th, td { padding: 9px; border: 1px solid #d9e1ef; text-align: left; white-space: nowrap; }
th { background: #edf3ff; }
.mastery-unknown { background: #f1f5f9; color: #64748b; }
.mastery-observed { background: #eaf8ef; color: #14532d; font-variant-numeric: tabular-nums; }
.explanation { margin: 8px 0 0; min-height: 0; max-height: 150px; font-size: .78rem; }
.inline-form, .inline-actions { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; }
.inline-form label { flex: 1 1 180px; }
.compact-form { grid-template-columns: minmax(220px, 1fr) auto auto; align-items: end; margin: 12px 0; }
@media (max-width: 800px) { .graph-layout, .mastery-grid, .analytics-grid { grid-template-columns: 1fr; } .compact-form { grid-template-columns: 1fr; } }
@media (max-width: 800px) { header { align-items: flex-start; flex-direction: column; } .admin-grid { grid-template-columns: 1fr; } }
</style>
