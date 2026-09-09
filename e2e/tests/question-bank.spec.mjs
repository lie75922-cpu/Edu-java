import { expect, test } from '@playwright/test'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'
const teacherUsername = process.env.E2E_TEACHER_USERNAME || 'demo-teacher-a'

async function login(page) {
  await page.goto('/')
  await page.getByLabel('用户名').fill(teacherUsername)
  await page.getByLabel('密码').fill(demoPassword)
  await page.getByRole('button', { name: '登录平台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '教师工作台', exact: true })).toBeVisible()
}

async function teacherContext() {
  const loginResponse = await fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: teacherUsername, password: demoPassword })
  })
  expect(loginResponse.status).toBe(200)
  const loginPayload = await loginResponse.json()
  const token = loginPayload.data.accessToken
  const courseResponse = await fetch(`${apiBase}/api/v1/teacher/courses`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(courseResponse.status).toBe(200)
  const coursePayload = await courseResponse.json()
  const course = coursePayload.data[0]
  expect(course).toBeTruthy()
  const exerciseResponse = await fetch(`${apiBase}/api/v1/admin/exercise-units?courseId=${course.courseId}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(exerciseResponse.status).toBe(200)
  const exercisePayload = await exerciseResponse.json()
  const exercise = exercisePayload.data.find(item => item.status === 'ACTIVE')
  expect(exercise).toBeTruthy()
  return { course, exercise }
}

test('教师可维护平台自编题目且停用后状态可追踪', async ({ page }) => {
  const { course, exercise } = await teacherContext()
  await login(page)

  await page.getByRole('button', { name: '题库管理', exact: true }).click()
  await expect(page.locator('.page-intro h2')).toHaveText('题库管理')

  const selects = page.locator('.page-intro .intro-actions select')
  await Promise.all([
    page.waitForResponse(response => response.url().includes(`/api/v1/admin/exercise-units?courseId=${course.courseId}`) && response.status() === 200),
    selects.nth(0).selectOption(String(course.courseId))
  ])
  await Promise.all([
    page.waitForResponse(response => response.url().includes(`/api/v1/admin/questions?exerciseUnitId=${exercise.id}`) && response.status() === 200),
    selects.nth(1).selectOption(String(exercise.id))
  ])

  const stem = '平台题库 E2E：2 + 3 的结果是多少？'
  await page.getByLabel('题干').fill(stem)
  await page.locator('input[placeholder="选项 A"]').fill('5')
  await page.locator('input[placeholder="选项 B"]').fill('4')
  await page.locator('input[placeholder="选项 C"]').fill('6')
  await page.locator('input[placeholder="选项 D"]').fill('7')
  await page.getByLabel('答案解析').fill('2 与 3 相加得到 5。')
  await page.getByRole('button', { name: '创建题目', exact: true }).click()

  const createdCard = page.locator('.admin-list-card').filter({ hasText: stem })
  await expect(createdCard).toBeVisible()
  await expect(createdCard).toContainText('答案：A')
  await expect(createdCard).toContainText('2 与 3 相加得到 5。')

  await createdCard.getByRole('button', { name: '停用', exact: true }).click()
  const disabledCard = page.locator('.admin-list-card').filter({ hasText: stem })
  await expect(disabledCard).toContainText('已停用')
})
