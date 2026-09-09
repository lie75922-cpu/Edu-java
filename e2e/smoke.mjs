const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'
const studentUsername = process.env.E2E_STUDENT_USERNAME || 'demo-student-alice'
const secondStudentUsername = process.env.E2E_SECOND_STUDENT_USERNAME || 'demo-student-dave'
const teacherUsername = process.env.E2E_TEACHER_USERNAME || 'demo-teacher-a'
const adminUsername = process.env.E2E_ADMIN_USERNAME || 'demo-admin'

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function request(path, { method = 'GET', token, body, expected = 200 } = {}) {
  const headers = { 'X-Request-Id': `release-smoke-${Date.now()}` }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : await response.text()
  assert(response.status === expected, `${method} ${path} expected ${expected}, received ${response.status}`)
  return payload
}

async function optionalData(path, { token } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const response = await fetch(`${apiBase}${path}`, { headers })
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : await response.text()
  if (response.status === 404) return null
  assert(response.status === 200, `GET ${path} expected 200 or 404, received ${response.status}`)
  return requireData(payload, path)
}

async function login(username) {
  const payload = await request('/api/v1/auth/login', {
    method: 'POST',
    body: { username, password: demoPassword }
  })
  assert(payload.code === 'OK' && payload.data?.accessToken, `login did not return an access token for ${username}`)
  return payload.data
}

function requireData(payload, operation) {
  assert(payload?.code === 'OK', `${operation} did not return the standard success envelope`)
  return payload.data
}

function containsChinese(value) {
  return /[\u3400-\u9fff]/.test(String(value || ''))
}

async function loadCourseContext(course, token) {
  const [areas, points, exercises, graph] = await Promise.all([
    requireData(await request(`/api/v1/courses/${course.id}/knowledge-areas`, { token }), 'knowledge areas'),
    requireData(await request(`/api/v1/courses/${course.id}/knowledge-points`, { token }), 'knowledge points'),
    requireData(await request(`/api/v1/exercise-units?courseId=${course.id}`, { token }), 'exercise units'),
    optionalData(`/api/v1/courses/${course.id}/graph`, { token })
  ])
  return { course, areas, points, exercises, graph }
}

async function findAnswerablePublishedCourse(contexts, token) {
  for (const context of contexts) {
    if (!context.graph?.graphVersionId || !context.graph.nodes?.length) continue
    for (const exercise of context.exercises) {
      if (exercise.status !== 'ACTIVE') continue
      const question = await optionalData(`/api/v1/exercise-units/${exercise.id}/questions/next`, { token })
      if (question?.id && question.options?.length) return { ...context, exercise, question }
    }
  }
  throw new Error('No student-accessible course has both a published graph and an answerable independently authored question')
}

const liveness = await request('/actuator/health/liveness')
const readiness = await request('/actuator/health/readiness')
const version = await request('/api/v1/system/version')
assert(liveness.status === 'UP', 'liveness is not UP')
assert(readiness.status === 'UP' || readiness.status === 'DEGRADED', 'readiness is neither UP nor documented DEGRADED')
assert(requireData(version, 'version').version, 'version response is empty')

const openapi = await fetch(`${apiBase}/openapi.yaml`)
assert(openapi.status === 200, 'OpenAPI document is unavailable')
assert((await openapi.text()).includes('bearerAuth'), 'OpenAPI document lacks JWT bearer security')

const student = await login(studentUsername)
const missingRoute = await request('/api/v1/release-check/missing-route', {
  token: student.accessToken,
  expected: 404
})
assert(missingRoute.code === 'NOT_FOUND', 'missing API route does not return the standard 404 envelope')
const studentCourses = requireData(await request('/api/v1/courses', { token: student.accessToken }), 'student courses')
assert(studentCourses.length > 0, 'student has no accessible courses')
const contexts = []
for (const course of studentCourses) contexts.push(await loadCourseContext(course, student.accessToken))

const catalogContext = contexts.find(context =>
  context.exercises.some(exercise => exercise.sourceType === 'JUNYI_METADATA') &&
  context.areas.length > 0 &&
  context.points.length > 0 &&
  [context.course.courseName, ...context.areas.map(area => area.areaName), ...context.points.map(point => point.knowledgeName)]
    .some(containsChinese)
)
assert(catalogContext, 'a Chinese import-backed catalog course is not available to the student')

const learning = await findAnswerablePublishedCourse(contexts, student.accessToken)
const selectedOptionKey = learning.question.options[0]?.optionKey
assert(selectedOptionKey, 'the independently authored question does not expose an answerable option')
const answer = requireData(await request(`/api/v1/questions/${learning.question.id}/answers`, {
  method: 'POST',
  token: student.accessToken,
  body: { selectedOptionKeys: [selectedOptionKey], durationMs: 250, clientRequestId: `release-smoke-answer-${Date.now()}` }
}), 'answer submission')
assert(typeof answer.correct === 'boolean', 'answer response does not describe judgement')

let mastery
for (let attempt = 0; attempt < 20; attempt += 1) {
  mastery = requireData(await request(`/api/v1/courses/${learning.course.id}/mastery`, { token: student.accessToken }), 'mastery')
  if (mastery.items.some(item => item.status === 'OBSERVED')) break
  await new Promise(resolve => setTimeout(resolve, 500))
}
assert(mastery.items.some(item => item.status === 'OBSERVED'), 'mastery worker did not process a real answer')
const recommendation = requireData(await request(`/api/v1/courses/${learning.course.id}/recommendations`, {
  method: 'POST', token: student.accessToken
}), 'recommendation generation')
assert(recommendation.graphVersionId, 'recommendation is not bound to a Published GraphVersion')
const latestRecommendation = requireData(await request(`/api/v1/courses/${learning.course.id}/recommendations/latest`, {
  token: student.accessToken
}), 'latest recommendation')
assert(latestRecommendation.id, 'latest recommendation snapshot is unavailable')
const pathTarget = learning.graph.nodes.at(-1)
const learningPath = requireData(await request(`/api/v1/knowledge-points/${pathTarget.id}/learning-path`, {
  token: student.accessToken
}), 'learning path')
assert(learningPath.graphVersionId, 'learning path is not bound to a Published GraphVersion')
assert(learning.graph.nodes.length > 0, 'published graph has no nodes')

const teacher = await login(teacherUsername)
const teacherCourses = requireData(await request('/api/v1/teacher/courses', { token: teacher.accessToken }), 'teacher courses')
assert(teacherCourses.length > 0, 'teacher has no authorized course')
const teacherCourse = teacherCourses[0]
for (const path of [
  `/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/overview`,
  `/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/knowledge-points`,
  `/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/mastery-heatmap?page=0&size=10`
]) {
  requireData(await request(path, { token: teacher.accessToken }), `teacher analytics ${path}`)
}
const otherStudent = await login(secondStudentUsername)
const otherCourses = requireData(await request('/api/v1/courses', { token: otherStudent.accessToken }), 'second student courses')
const teacherCourseIds = new Set(teacherCourses.map(course => String(course.courseId)))
const forbiddenCourse = otherCourses.find(course => !teacherCourseIds.has(String(course.id)))
assert(forbiddenCourse, 'no student-accessible course is outside the teacher authorization boundary')
const forbidden = await request(`/api/v1/teacher/courses/${forbiddenCourse.id}/analytics/overview`, {
  token: teacher.accessToken,
  expected: 403
})
assert(forbidden.code === 'FORBIDDEN', 'cross-course teacher denial is not a consistent 403 response')

const admin = await login(adminUsername)
const governanceOverview = requireData(await request('/api/v1/admin/data-governance/overview', { token: admin.accessToken }), 'data governance overview')
assert(Number.isInteger(governanceOverview.catalogImportRunCount), 'data governance overview omits catalog ImportRun count')
const importRuns = requireData(await request('/api/v1/admin/data-governance/import-runs', { token: admin.accessToken }), 'data governance import runs')
assert(Array.isArray(importRuns.importRuns), 'data governance import runs are not an array')
const evidence = requireData(await request(`/api/v1/admin/evidence?courseId=${catalogContext.course.id}`, { token: admin.accessToken }), 'catalog evidence')
assert(Array.isArray(evidence), 'catalog evidence response is not an array')
const graphVersions = requireData(await request(`/api/v1/admin/graph-versions?courseId=${catalogContext.course.id}`, { token: admin.accessToken }), 'catalog graph versions')
assert(Array.isArray(graphVersions), 'catalog graph version response is not an array')

const unauthenticated = await request(`/api/v1/teacher/courses/${teacherCourse.courseId}/analytics/overview`, { expected: 401 })
assert(unauthenticated.code === 'UNAUTHENTICATED', 'missing JWT does not have a consistent failure envelope')
console.log('release API smoke passed')
