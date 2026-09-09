import { mkdir, writeFile } from 'node:fs/promises'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'
const studentUsername = process.env.E2E_STUDENT_USERNAME || 'demo-student-alice'
const teacherUsername = process.env.E2E_TEACHER_USERNAME || 'demo-teacher-a'
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

async function optionalData(path, token) {
  const response = await fetch(`${apiBase}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : await response.text()
  if (response.status === 404) return null
  assert(response.status === 200, `GET ${path} expected 200 or 404, received ${response.status}`)
  return data({ payload }, path)
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

async function findAnswerablePublishedCourse(courses, token) {
  for (const course of courses) {
    const [points, exercises, graph] = await Promise.all([
      data(await request(`/api/v1/courses/${course.id}/knowledge-points`, { token }), 'knowledge points'),
      data(await request(`/api/v1/exercise-units?courseId=${course.id}`, { token }), 'exercise units'),
      optionalData(`/api/v1/courses/${course.id}/graph`, token)
    ])
    if (!points.length || !graph?.nodes?.length) continue
    for (const exercise of exercises) {
      if (exercise.status !== 'ACTIVE') continue
      const question = await optionalData(`/api/v1/exercise-units/${exercise.id}/questions/next`, token)
      if (question?.id && question.options?.length) return { course, points, exercises, graph, exercise, question }
    }
  }
  throw new Error('No student-accessible course has a published graph and an answerable independently authored question')
}

const student = await login(studentUsername)
const studentCourses = data(await request('/api/v1/courses', { token: student.accessToken }), 'student courses')
const learning = await findAnswerablePublishedCourse(studentCourses, student.accessToken)
const target = learning.graph.nodes.at(-1)
const selectedOptionKey = learning.question.options[0]?.optionKey
assert(selectedOptionKey, 'question does not expose an answerable option')

const teacher = await login(teacherUsername)
const teacherCourses = data(await request('/api/v1/teacher/courses', { token: teacher.accessToken }), 'teacher courses')
assert(teacherCourses.length > 0, 'teacher has no authorized course')
const teacherCourse = teacherCourses[0]
const heatmap = data(await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/mastery-heatmap?page=0&size=10`, { token: teacher.accessToken }), 'teacher heatmap')
const studentId = heatmap.students?.[0]?.studentId
assert(studentId, 'teacher heatmap does not contain an authorized student')

const metrics = []
metrics.push(await measure('answer_submission', async index => {
  const result = await request(`/api/v1/questions/${learning.question.id}/answers`, {
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
  data(await request(`/api/v1/courses/${learning.course.id}/mastery`, { token: student.accessToken }), 'mastery read')
}))
metrics.push(await measure('recommendation_generation', async () => {
  data(await request(`/api/v1/courses/${learning.course.id}/recommendations`, { method: 'POST', token: student.accessToken }), 'recommendation generation')
}))
metrics.push(await measure('learning_path', async () => {
  data(await request(`/api/v1/knowledge-points/${target.id}/learning-path`, { token: student.accessToken }), 'learning path')
}))
metrics.push(await measure('teacher_overview', async () => {
  data(await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/overview`, { token: teacher.accessToken }), 'teacher overview')
}))
metrics.push(await measure('teacher_knowledge_point_analytics', async () => {
  data(await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/knowledge-points`, { token: teacher.accessToken }), 'teacher KnowledgePoint analytics')
}))
metrics.push(await measure('teacher_heatmap', async () => {
  data(await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/mastery-heatmap?page=0&size=10`, { token: teacher.accessToken }), 'teacher heatmap')
}))
metrics.push(await measure('teacher_student_detail', async () => {
  data(await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/students/${studentId}/analytics`, { token: teacher.accessToken }), 'teacher student detail')
}))

const concurrentStarted = performance.now()
const concurrentResponses = await Promise.all(Array.from({ length: 10 }, () => request(
  `/api/v1/courses/${learning.course.id}/mastery`,
  { token: student.accessToken }
)))
concurrentResponses.forEach(result => data(result, 'concurrent mastery read'))

const evidence = {
  measuredAt: new Date().toISOString(),
  mode: 'local release data discovered at runtime; fixed 10 sequential samples after one warm-up per endpoint',
  dataScale: {
    studentAccessibleCourses: studentCourses.length,
    learningCourseCode: learning.course.courseCode,
    learningExerciseSourceType: learning.exercise.sourceType,
    learningKnowledgePoints: learning.points.length,
    learningExerciseUnits: learning.exercises.length
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
await writeFile('artifacts/performance-v1-preview.json', `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
console.log(JSON.stringify(evidence))
