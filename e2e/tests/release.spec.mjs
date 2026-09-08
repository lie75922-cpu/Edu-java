import { expect, test } from '@playwright/test'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = 'LocalDemoOnly!2026'

async function login(page, username) {
  await page.goto('/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(demoPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '已授权课程' })).toBeVisible()
}

async function courseFor(username) {
  const response = await fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: demoPassword })
  })
  expect(response.status).toBe(200)
  const loginResult = await response.json()
  const token = loginResult.data.accessToken
  const coursesResponse = await fetch(`${apiBase}/api/v1/courses`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(coursesResponse.status).toBe(200)
  return (await coursesResponse.json()).data[0]
}

test('student, teacher, authorization, and admin release journeys use the real full stack', async ({ page }) => {
  const courseA = await courseFor('demo-student-alice')
  const courseB = await courseFor('demo-student-dave')

  await login(page, 'demo-student-alice')
  await page.getByRole('button', { name: '进入课程', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demo Algebra Foundations' })).toBeVisible()
  await page.getByRole('button', { name: '开始答题', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: '题目' })).toBeVisible()
  await page.locator('input[type="radio"]').first().check()
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await expect(page.getByRole('heading', { name: /回答正确|回答不正确/ })).toBeVisible()
  await page.getByRole('button', { name: '我的学习', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的知识掌握与学习建议' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '我的知识掌握情况' })).toBeVisible()
  await page.getByRole('button', { name: '生成推荐快照', exact: true }).click()
  await expect(page.getByRole('heading', { name: '推荐练习与原因' })).toBeVisible()
  const target = await page.evaluate(async courseId => {
    const session = JSON.parse(localStorage.getItem('edu-session'))
    const response = await fetch(`/api/v1/courses/${courseId}/knowledge-points`, {
      headers: { Authorization: `Bearer ${session.accessToken}` }
    })
    const payload = await response.json()
    return payload.data.find(point => point.knowledgeCode === 'DEMO-ALG-APPLICATION')?.id
  }, courseA.id)
  expect(target).toBeTruthy()
  await page.getByLabel('目标 KnowledgePoint ID').fill(String(target))
  await page.getByRole('button', { name: '查询学习路径', exact: true }).click()
  await expect(page.getByRole('heading', { name: '目标知识点学习路径' })).toBeVisible()

  await page.getByRole('button', { name: '退出', exact: true }).click()
  await login(page, 'demo-teacher-a')
  await page.getByRole('button', { name: '教师工作台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '教师课程工作台' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'KnowledgePoint 学情' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Student × KnowledgePoint 掌握度热力表' })).toBeVisible()
  await page.getByRole('button', { name: /Demo Student/ }).first().click()
  await expect(page.getByText('课程学情')).toBeVisible()
  const denial = await page.evaluate(async courseId => {
    const session = JSON.parse(localStorage.getItem('edu-session'))
    const response = await fetch(`/api/v1/teacher/courses/${courseId}/analytics/overview`, {
      headers: { Authorization: `Bearer ${session.accessToken}` }
    })
    const payload = await response.json()
    return { status: response.status, code: payload.code }
  }, courseB.id)
  expect(denial).toEqual({ status: 403, code: 'FORBIDDEN' })

  await page.getByRole('button', { name: '退出', exact: true }).click()
  await login(page, 'demo-admin')
  await page.getByRole('button', { name: '管理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '教师课程分配' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'V0.3 Knowledge Relation Governance' })).toBeVisible()
  const courseCard = page.locator('section.card').filter({ has: page.getByRole('heading', { name: '课程管理' }) })
  await courseCard.getByLabel('课程 ID').fill(String(courseA.id))
  await courseCard.getByRole('button', { name: '加载分配', exact: true }).click()
  await expect(courseCard.getByText('Teacher A')).toBeVisible()
  const graphCard = page.locator('section.card').filter({ has: page.getByRole('heading', { name: 'V0.3 Knowledge Relation Governance' }) })
  await graphCard.getByLabel('课程 ID').fill(String(courseA.id))
  await graphCard.getByRole('button', { name: '加载 GraphVersion 与 Evidence', exact: true }).click()
  await expect(graphCard.getByText('GraphVersion')).toBeVisible()
})
