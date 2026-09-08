const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = 'LocalDemoOnly!2026'

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

const liveness = await request('/actuator/health/liveness')
const readiness = await request('/actuator/health/readiness')
const version = await request('/api/v1/system/version')
assert(liveness.status === 'UP', 'liveness is not UP')
assert(readiness.status === 'UP' || readiness.status === 'DEGRADED', 'readiness is neither UP nor documented DEGRADED')
assert(requireData(version, 'version').version, 'version response is empty')

const openapi = await fetch(`${apiBase}/openapi.yaml`)
assert(openapi.status === 200, 'OpenAPI document is unavailable')
assert((await openapi.text()).includes('bearerAuth'), 'OpenAPI document lacks JWT bearer security')

const student = await login('demo-student-alice')
const missingRoute = await request('/api/v1/release-check/missing-route', {
  token: student.accessToken,
  expected: 404
})
assert(missingRoute.code === 'NOT_FOUND', 'missing API route does not return the standard 404 envelope')
const studentCourses = requireData(await request('/api/v1/courses', { token: student.accessToken }), 'student courses')
const courseA = studentCourses.find(course => course.courseCode === 'DEMO-ALG-101')
assert(courseA, 'synthetic algebra course is not accessible to demo student')
const points = requireData(await request(`/api/v1/courses/${courseA.id}/knowledge-points`, { token: student.accessToken }), 'knowledge points')
const target = points.find(point => point.knowledgeCode === 'DEMO-ALG-APPLICATION')
assert(target, 'learning-path target is missing')
const exercises = requireData(await request(`/api/v1/exercise-units?courseId=${courseA.id}`, { token: student.accessToken }), 'exercise units')
assert(exercises.length > 0, 'synthetic course has no exercise units')
const question = requireData(await request(`/api/v1/exercise-units/${exercises[0].id}/questions/next`, { token: student.accessToken }), 'next question')
const answer = requireData(await request(`/api/v1/questions/${question.id}/answers`, {
  method: 'POST',
  token: student.accessToken,
  body: { selectedOptionKeys: ['A'], durationMs: 250, clientRequestId: `release-smoke-answer-${Date.now()}` }
}), 'answer submission')
assert(typeof answer.correct === 'boolean', 'answer response does not describe judgement')

let mastery
for (let attempt = 0; attempt < 20; attempt += 1) {
  mastery = requireData(await request(`/api/v1/courses/${courseA.id}/mastery`, { token: student.accessToken }), 'mastery')
  if (mastery.items.some(item => item.status === 'OBSERVED')) break
  await new Promise(resolve => setTimeout(resolve, 500))
}
assert(mastery.items.some(item => item.status === 'OBSERVED'), 'mastery worker did not process a real answer')
const recommendation = requireData(await request(`/api/v1/courses/${courseA.id}/recommendations`, {
  method: 'POST', token: student.accessToken
}), 'recommendation generation')
assert(recommendation.graphVersionId, 'recommendation is not bound to a Published GraphVersion')
const latestRecommendation = requireData(await request(`/api/v1/courses/${courseA.id}/recommendations/latest`, {
  token: student.accessToken
}), 'latest recommendation')
assert(latestRecommendation.id, 'latest recommendation snapshot is unavailable')
const learningPath = requireData(await request(`/api/v1/knowledge-points/${target.id}/learning-path`, {
  token: student.accessToken
}), 'learning path')
assert(learningPath.graphVersionId, 'learning path is not bound to a Published GraphVersion')
const graph = requireData(await request(`/api/v1/courses/${courseA.id}/graph`, { token: student.accessToken }), 'published graph')
assert(graph.nodes.length >= 2 && graph.edges.length >= 1, 'real Published Graph query is incomplete')

const teacher = await login('demo-teacher-a')
const teacherCourses = requireData(await request('/api/v1/teacher/courses', { token: teacher.accessToken }), 'teacher courses')
assert(teacherCourses.length === 1 && teacherCourses[0].courseId === courseA.id, 'Teacher A can see an unassigned course')
for (const path of [
  `/api/v1/teacher/courses/${courseA.id}/analytics/overview`,
  `/api/v1/teacher/courses/${courseA.id}/analytics/knowledge-points`,
  `/api/v1/teacher/courses/${courseA.id}/analytics/mastery-heatmap?page=0&size=10`
]) {
  const endpoint = path.replace('/api/v1', '')
  requireData(await request(`/api/v1${endpoint}`, { token: teacher.accessToken }), `teacher analytics ${endpoint}`)
}
const otherStudent = await login('demo-student-dave')
const otherCourses = requireData(await request('/api/v1/courses', { token: otherStudent.accessToken }), 'second student courses')
const courseB = otherCourses.find(course => course.courseCode === 'DEMO-GEO-201')
assert(courseB, 'synthetic second course is missing')
const forbidden = await request(`/api/v1/teacher/courses/${courseB.id}/analytics/overview`, {
  token: teacher.accessToken,
  expected: 403
})
assert(forbidden.code === 'FORBIDDEN', 'cross-course teacher denial is not a consistent 403 response')

const admin = await login('demo-admin')
const assignments = requireData(await request(`/api/v1/admin/courses/${courseA.id}/teachers`, { token: admin.accessToken }), 'teacher assignments')
assert(assignments.some(assignment => assignment.username === 'demo-teacher-a'), 'admin assignment view misses Teacher A')
const rebuild = requireData(await request('/api/v1/admin/graph-projections/rebuild-published', {
  method: 'POST', token: admin.accessToken
}), 'Published Graph reprojection')
assert(rebuild.some(item => item.courseId === courseA.id && item.edgeCount >= 1), 'MySQL-to-Neo4j reprojection did not rebuild course A')

const unauthenticated = await request(`/api/v1/teacher/courses/${courseA.id}/analytics/overview`, { expected: 401 })
assert(unauthenticated.code === 'UNAUTHENTICATED', 'missing JWT does not have a consistent failure envelope')
console.log('release API smoke passed')
