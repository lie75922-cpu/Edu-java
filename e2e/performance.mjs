import { mkdir, writeFile } from 'node:fs/promises'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = 'LocalDemoOnly!2026'
const sampleCount = 10

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function request(path, { method = 'GET', token, body, expected = 200 } = {}) {
  const headers = { 'X-Request-Id': `release-performance-${Date.now()}-${Math.random().toString(36).slice(2)}` }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const started = performance.now()
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const elapsedMs = performance.now() - started
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : await response.text()
  assert(response.status === expected, `${method} ${path} expected ${expected}, received ${response.status}`)
  return { payload, elapsedMs }
}

async function login(username) {
  const { payload } = await request('/api/v1/auth/login', {
    method: 'POST',
    body: { username, password: demoPassword }
  })
  assert(payload.code === 'OK' && payload.data?.accessToken, `login failed for ${username}`)
  return payload.data
}

function data(result, operation) {
  assert(result.payload?.code === 'OK', `${operation} did not return the standard success envelope`)
  return result.payload.data
}

function percentile(sortedValues, fraction) {
  const index = Math.max(0, Math.ceil(sortedValues.length * fraction) - 1)
  return sortedValues[index]
}

async function measure(name, operation, count = sampleCount) {
  await operation()
  const values = []
  for (let index = 0; index < count; index += 1) {
    const started = performance.now()
    await operation(index)
    values.push(performance.now() - started)
  }
  values.sort((left, right) => left - right)
  return {
    name,
    samples: values.length,
    minMs: Number(values[0].toFixed(1)),
    p50Ms: Number(percentile(values, 0.5).toFixed(1)),
    p95Ms: Number(percentile(values, 0.95).toFixed(1)),
    maxMs: Number(values.at(-1).toFixed(1))
  }
}

const student = await login('demo-student-alice')
const studentCourses = data(await request('/api/v1/courses', { token: student.accessToken }), 'student courses')
const course = studentCourses.find(item => item.courseCode === 'DEMO-ALG-101')
assert(course, 'synthetic algebra course is unavailable')
const points = data(await request(`/api/v1/courses/${course.id}/knowledge-points`, { token: student.accessToken }), 'knowledge points')
const target = points.find(point => point.knowledgeCode === 'DEMO-ALG-APPLICATION')
assert(target, 'learning-path target is unavailable')
const exercises = data(await request(`/api/v1/exercise-units?courseId=${course.id}`, { token: student.accessToken }), 'exercise units')
assert(exercises.length > 0, 'no exercise unit is available')
const nextQuestion = data(await request(`/api/v1/exercise-units/${exercises[0].id}/questions/next`, { token: student.accessToken }), 'next question')
const selectedOptionKey = nextQuestion.options?.[0]?.optionKey
assert(selectedOptionKey, 'question does not expose an answerable option')

const teacher = await login('demo-teacher-a')
const teacherCourses = data(await request('/api/v1/teacher/courses', { token: teacher.accessToken }), 'teacher courses')
assert(teacherCourses.length === 1 && teacherCourses[0].courseId === course.id, 'teacher fixture is not course-isolated')
const heatmap = data(await request(`/api/v1/teacher/courses/${course.id}/analytics/mastery-heatmap?page=0&size=10`, { token: teacher.accessToken }), 'teacher heatmap')
const studentId = heatmap.students?.[0]?.studentId
assert(studentId, 'teacher heatmap does not contain a synthetic student')

const metrics = []
metrics.push(await measure('answer_submission', async index => {
  const result = await request(`/api/v1/questions/${nextQuestion.id}/answers`, {
    method: 'POST',
    token: student.accessToken,
    body: {
      selectedOptionKeys: [selectedOptionKey],
      durationMs: 100,
      clientRequestId: `release-performance-answer-${Date.now()}-${index}`
    }
  })
  data(result, 'answer submission')
}))
metrics.push(await measure('mastery_read', async () => {
  data(await request(`/api/v1/courses/${course.id}/mastery`, { token: student.accessToken }), 'mastery read')
}))
metrics.push(await measure('recommendation_generation', async () => {
  data(await request(`/api/v1/courses/${course.id}/recommendations`, { method: 'POST', token: student.accessToken }), 'recommendation generation')
}))
metrics.push(await measure('learning_path', async () => {
  data(await request(`/api/v1/knowledge-points/${target.id}/learning-path`, { token: student.accessToken }), 'learning path')
}))
metrics.push(await measure('teacher_overview', async () => {
  data(await request(`/api/v1/teacher/courses/${course.id}/analytics/overview`, { token: teacher.accessToken }), 'teacher overview')
}))
metrics.push(await measure('teacher_knowledge_point_analytics', async () => {
  data(await request(`/api/v1/teacher/courses/${course.id}/analytics/knowledge-points`, { token: teacher.accessToken }), 'teacher KnowledgePoint analytics')
}))
metrics.push(await measure('teacher_heatmap', async () => {
  data(await request(`/api/v1/teacher/courses/${course.id}/analytics/mastery-heatmap?page=0&size=10`, { token: teacher.accessToken }), 'teacher heatmap')
}))
metrics.push(await measure('teacher_student_detail', async () => {
  data(await request(`/api/v1/teacher/courses/${course.id}/students/${studentId}/analytics`, { token: teacher.accessToken }), 'teacher student detail')
}))

const concurrentStarted = performance.now()
const concurrentResponses = await Promise.all(Array.from({ length: 10 }, () => request(
  `/api/v1/courses/${course.id}/mastery`,
  { token: student.accessToken }
)))
concurrentResponses.forEach(result => data(result, 'concurrent mastery read'))

const evidence = {
  measuredAt: new Date().toISOString(),
  mode: 'local synthetic Platform Demo fixture; fixed 10 sequential samples after one warm-up per endpoint',
  dataScale: {
    activeCourses: 2,
    platformUsers: 7,
    targetCourseCode: course.courseCode,
    targetKnowledgePointCode: target.knowledgeCode
  },
  metrics,
  concurrency: {
    operation: 'mastery_read',
    parallelRequests: 10,
    wallClockMs: Number((performance.now() - concurrentStarted).toFixed(1)),
    completedRequests: concurrentResponses.length
  }
}

await mkdir('artifacts', { recursive: true })
await writeFile('artifacts/performance-v07.json', `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
console.log(JSON.stringify(evidence))
