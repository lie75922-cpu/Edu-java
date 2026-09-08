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
const courseA = studentCourses.find(course => course.courseCode === 'DM-101')
assert(courseA, '离散数学主课程未对演示学生开放')
const points = requireData(await request(`/api/v1/courses/${courseA.id}/knowledge-points`, { token: student.accessToken }), 'knowledge points')
assert(points.length >= 16, '离散数学主课程知识点数量不足')
const target = points.find(point => point.knowledgeCode === 'DM-LOGIC-INFERENCE')
assert(target, '学习路径目标知识点缺失')
const exercises = requireData(await request(`/api/v1/exercise-units?courseId=${courseA.id}`, { token: student.accessToken }), 'exercise units')
assert(exercises.length >= 16, '离散数学主课程练习单元不足')
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
assert(graph.nodes.length >= 16 && graph.edges.length >= 12, '离散数学 Published Graph 规模不符合演示基线')

const teacher = await login('demo-teacher-a')
const teacherCourses = requireData(await request('/api/v1/teacher/courses', { token: teacher.accessToken }), 'teacher courses')
assert(teacherCourses.some(course => course.courseId === courseA.id), '张老师无法访问离散数学主课程')
for (const path of [
  `/api/v1/teacher/courses/${courseA.id}/analytics/overview`,
  `/api/v1/teacher/courses/${courseA.id}/analytics/knowledge-points`,
  `/api/v1/teacher/courses/${courseA.id}/analytics/mastery-heatmap?page=0&size=10`
]) {
  requireData(await request(path, { token: teacher.accessToken }), `teacher analytics ${path}`)
}
const otherStudent = await login('demo-student-dave')
const otherCourses = requireData(await request('/api/v1/courses', { token: otherStudent.accessToken }), 'second student courses')
const courseB = otherCourses.find(course => course.courseCode === 'DM-GRAPH-201')
assert(courseB, '图论专题训练课程缺失')
assert(!teacherCourses.some(course => course.courseId === courseB.id), 'Teacher A can see Teacher B course')
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
assert(rebuild.some(item => item.courseId === courseA.id && item.edgeCount >= 12), 'MySQL-to-Neo4j reprojection did not rebuild discrete-math graph')

const unauthenticated = await request(`/api/v1/teacher/courses/${courseA.id}/analytics/overview`, { expected: 401 })
assert(unauthenticated.code === 'UNAUTHENTICATED', 'missing JWT does not have a consistent failure envelope')
console.log('release API smoke passed')
