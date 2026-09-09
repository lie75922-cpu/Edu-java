import { expect, test } from '@playwright/test'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'
const studentUsername = process.env.E2E_STUDENT_USERNAME || 'demo-student-alice'
const secondStudentUsername = process.env.E2E_SECOND_STUDENT_USERNAME || 'demo-student-dave'
const teacherUsername = process.env.E2E_TEACHER_USERNAME || 'demo-teacher-a'
const adminUsername = process.env.E2E_ADMIN_USERNAME || 'demo-admin'

async function shot(page, name) {
  await page.screenshot({ path: `artifacts/${name}.png`, fullPage: true })
}

async function login(page, username, heading) {
  await page.goto('/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(demoPassword)
  await page.getByRole('button', { name: '登录平台', exact: true }).click()
  await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
}

async function rawApi(path, { token } = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  return { response, payload: await response.json() }
}

async function apiData(path, token) {
  const { response, payload } = await rawApi(path, { token })
  expect(response.status).toBe(200)
  expect(payload.code).toBe('OK')
  return payload.data
}

async function optionalApiData(path, token, allowUnpublishedGraph = false) {
  const { response, payload } = await rawApi(path, { token })
  if (response.status === 404) return null
  if (allowUnpublishedGraph
    && response.status === 409
    && payload?.code === 'CONFLICT'
    && payload?.message === 'course does not have a published graph version') return null
  expect(response.status).toBe(200)
  expect(payload.code).toBe('OK')
  return payload.data
}

async function sessionFor(username) {
  const response = await fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: demoPassword })
  })
  expect(response.status).toBe(200)
  const loginResult = await response.json()
  const token = loginResult.data.accessToken
  const courses = await apiData('/api/v1/courses', token)
  return { token, courses }
}

function containsChinese(value) {
  return /[\u3400-\u9fff]/.test(String(value || ''))
}

async function loadCourseContext(course, token) {
  const [areas, points, exercises, graph] = await Promise.all([
    apiData(`/api/v1/courses/${course.id}/knowledge-areas`, token),
    apiData(`/api/v1/courses/${course.id}/knowledge-points`, token),
    apiData(`/api/v1/exercise-units?courseId=${course.id}`, token),
    optionalApiData(`/api/v1/courses/${course.id}/graph`, token, true)
  ])
  return { course, areas, points, exercises, graph }
}

async function findAnswerablePublishedCourse(contexts, token) {
  for (const context of contexts) {
    if (!context.graph?.graphVersionId || !context.graph.nodes?.length) continue
    for (const exercise of context.exercises) {
      if (exercise.status !== 'ACTIVE') continue
      const question = await optionalApiData(`/api/v1/exercise-units/${exercise.id}/questions/next`, token)
      if (question?.id && question.options?.length) return { ...context, exercise, question }
    }
  }
  throw new Error('No student-accessible course has both a published graph and an answerable independently authored question')
}

async function selectCourse(page, courseId) {
  const selector = page.locator('.page-intro > select')
  if (await selector.count()) await selector.selectOption(String(courseId))
}

test('真实目录、独立题库、角色权限与图谱治理全栈流程', async ({ page }) => {
  const alice = await sessionFor(studentUsername)
  const dave = await sessionFor(secondStudentUsername)
  const teacher = await sessionFor(teacherUsername)
  const admin = await sessionFor(adminUsername)
  const adminContexts = []
  for (const course of admin.courses) adminContexts.push(await loadCourseContext(course, admin.token))
  const importedCatalog = adminContexts.find(context =>
    context.exercises.some(exercise => exercise.sourceType === 'JUNYI_CATALOG') &&
    context.areas.length > 0 &&
    context.points.length > 0 &&
    [context.course.courseName, ...context.areas.map(area => area.areaName), ...context.points.map(point => point.knowledgeName)]
      .some(containsChinese)
  )
  expect(importedCatalog).toBeTruthy()
  const enrollmentResponse = await fetch(`${apiBase}/api/v1/courses/${importedCatalog.course.id}/enroll`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${alice.token}` }
  })
  expect(enrollmentResponse.status).toBe(200)
  const enrollment = await enrollmentResponse.json()
  expect(enrollment.code).toBe('OK')
  expect(enrollment.data.courseId).toBe(importedCatalog.course.id)
  alice.courses = await apiData('/api/v1/courses', alice.token)
  const teacherAssignments = await apiData('/api/v1/teacher/courses', teacher.token)
  const contexts = []
  for (const course of alice.courses) contexts.push(await loadCourseContext(course, alice.token))

  const catalog = contexts.find(context =>
    context.exercises.some(exercise => exercise.sourceType === 'JUNYI_CATALOG') &&
    context.areas.length > 0 &&
    context.points.length > 0 &&
    [context.course.courseName, ...context.areas.map(area => area.areaName), ...context.points.map(point => point.knowledgeName)]
      .some(containsChinese)
  )
  expect(catalog).toBeTruthy()
  const learning = await findAnswerablePublishedCourse(contexts, alice.token)
  const teacherCourse = teacherAssignments[0]
  const teacherCourseIds = new Set(teacherAssignments.map(course => String(course.courseId)))
  const forbiddenCourse = dave.courses.find(course => !teacherCourseIds.has(String(course.id)))

  expect(teacherCourse).toBeTruthy()
  expect(forbiddenCourse).toBeTruthy()

  // Student: runtime-discovered Chinese catalog, then an independently authored question.
  await login(page, studentUsername, '首页')
  await expect(page.getByText('数学智慧学习平台')).toBeVisible()
  await expect(page.locator('.course-card').first()).toBeVisible()
  await shot(page, '01-student-home')

  await page.getByRole('button', { name: '课程学习', exact: true }).click()
  await selectCourse(page, catalog.course.id)
  await expect(page.getByRole('heading', { name: catalog.course.courseName, exact: true })).toBeVisible()
  await expect(page.locator('.knowledge-row').first()).toBeVisible()
  await shot(page, '02-chinese-catalog')

  await selectCourse(page, learning.course.id)
  const practiceButton = page.locator(`button[data-exercise-id="${learning.exercise.id}"]`)
  await expect(practiceButton).toBeVisible()
  await practiceButton.click()
  await expect(page.locator('.question-content')).toBeVisible()
  await expect(page.locator('.question-content h4')).toHaveText(learning.question.stem)
  await expect(page.locator('.question-content')).toContainText('独立题库')
  await page.locator('.answer-option').first().click()
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await expect(page.getByText(/回答正确|还需要再巩固/)).toBeVisible()
  await shot(page, '03-independent-question-answer')
  await page.locator('.question-modal .close-button').click()

  await page.getByRole('button', { name: '个性化学习', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的学习建议与路径', exact: true })).toBeVisible()
  await page.locator('.page-intro .intro-actions select').selectOption(String(learning.course.id))
  await page.getByRole('button', { name: '更新学习建议', exact: true }).click()
  await expect(page.getByRole('heading', { name: '下一步学什么', exact: true })).toBeVisible()
  const targetSelect = page.locator('.path-controls select')
  await expect.poll(async () => targetSelect.locator('option').count()).toBeGreaterThan(1)
  await targetSelect.selectOption(String(learning.graph.nodes.at(-1).id))
  await page.getByRole('button', { name: '生成学习路径', exact: true }).click()
  await expect(page.locator('.learning-path .path-step').first()).toBeVisible()
  await shot(page, '04-personalization-and-path')

  await page.getByRole('button', { name: '知识图谱', exact: true }).click()
  await expect(page.getByRole('heading', { name: `${learning.course.courseName}知识地图`, exact: true })).toBeVisible()
  const graphNodes = page.locator('.svg-node')
  await expect(graphNodes.first()).toBeVisible()
  await graphNodes.first().click()
  await expect(page.getByText('前置知识', { exact: true })).toBeVisible()
  await shot(page, '05-published-graph')

  // Teacher: authorized course analytics and a runtime-discovered cross-course denial.
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login(page, teacherUsername, '教师工作台')
  await expect(page.getByRole('heading', { name: teacherCourse.courseName, exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '班级知识掌握概览', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '学生 × 知识点掌握情况', exact: true })).toBeVisible()
  await expect(page.locator('.teacher-point-list article').first()).toBeVisible()
  const firstStudent = page.locator('.heatmap-wrap tbody .text-button.strong').first()
  await expect(firstStudent).toBeVisible()
  await firstStudent.click()
  await expect(page.getByRole('heading', { name: '学生学情详情', exact: true })).toBeVisible()
  await shot(page, '06-teacher-analytics')

  const denial = await page.evaluate(async courseId => {
    const session = JSON.parse(localStorage.getItem('edu-session'))
    const response = await fetch(`/api/v1/teacher/courses/${courseId}/analytics/overview`, {
      headers: { Authorization: `Bearer ${session.accessToken}` }
    })
    const payload = await response.json()
    return { status: response.status, code: payload.code }
  }, forbiddenCourse.id)
  expect(denial).toEqual({ status: 403, code: 'FORBIDDEN' })

  // Administrator: persisted governance facts, evidence, import runs, graph versions, and system state.
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login(page, adminUsername, '管理工作台')
  const adminCourseSelect = page.locator('.page-intro select')
  await adminCourseSelect.selectOption(String(catalog.course.id))
  await page.getByRole('button', { name: '数据治理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '目录导入与知识关系状态', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '真实导入审计记录', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: `${catalog.course.courseName}的 Evidence 审计`, exact: true })).toBeVisible()
  await shot(page, '07-data-governance')

  await page.getByRole('button', { name: '知识图谱治理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '图谱版本', exact: true })).toBeVisible()
  await expect(page.locator('.graph-admin-layout .admin-list-card').first()).toBeVisible()
  await shot(page, '08-graph-governance')

  await page.getByRole('button', { name: '系统状态', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Java 工程底座', exact: true })).toBeVisible()
  await shot(page, '09-system-status')
})
