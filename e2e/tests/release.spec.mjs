import { expect, test } from '@playwright/test'

const apiBase = (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const demoPassword = 'LocalDemoOnly!2026'

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

async function courseFor(username, code) {
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
  const courses = (await coursesResponse.json()).data
  return courses.find(course => course.courseCode === code)
}

test('中文离散数学学生、教师、管理员真实全栈流程', async ({ page }) => {
  const courseA = await courseFor('demo-student-alice', 'DM-101')
  const courseB = await courseFor('demo-student-dave', 'DM-GRAPH-201')
  expect(courseA).toBeTruthy()
  expect(courseB).toBeTruthy()

  await page.goto('/')
  await expect(page.getByText('离散数学智慧教学平台')).toBeVisible()
  await shot(page, '01-登录页')

  // 学生：首页 -> 课程 -> 练习 -> 个性化推荐 -> 学习路径 -> 知识图谱。
  await login(page, 'demo-student-alice', '首页')
  await expect(page.getByText('离散数学智慧教学平台')).toBeVisible()
  await expect(page.locator('.course-card').filter({ hasText: '离散数学' })).toBeVisible()
  await expect(page.locator('.graph-summary')).toBeVisible()
  await shot(page, '02-学生首页')

  await page.getByRole('button', { name: '课程学习', exact: true }).click()
  await expect(page.getByRole('heading', { name: '离散数学', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /第一章 数理逻辑/ })).toBeVisible()
  const propositionRow = page.locator('.knowledge-row').filter({ hasText: '命题与逻辑联结词' })
  await expect(propositionRow).toBeVisible()
  await expect(page.locator('.knowledge-row')).toHaveCount(16)
  await shot(page, '03-离散数学课程学习')

  await propositionRow.getByRole('button', { name: '开始练习', exact: true }).click()
  await expect(page.getByText('设 p 为真、q 为假')).toBeVisible()
  await shot(page, '04-知识点练习')
  await page.locator('.answer-option').first().click()
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await expect(page.getByText(/回答正确|还需要再巩固/)).toBeVisible()
  await shot(page, '05-练习反馈')
  await page.getByRole('button', { name: '完成本次练习', exact: true }).click()

  await page.getByRole('button', { name: '个性化学习', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的学习建议与路径', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '更新学习建议', exact: true }).click()
  await expect(page.getByRole('heading', { name: '下一步学什么', exact: true })).toBeVisible()
  const targetSelect = page.locator('.path-controls select')
  await targetSelect.selectOption({ label: '命题逻辑推理理论' })
  await page.getByRole('button', { name: '生成学习路径', exact: true }).click()
  await expect(page.locator('.learning-path .path-step').first()).toBeVisible()
  await shot(page, '06-个性化学习与路径')

  await page.getByRole('button', { name: '知识图谱', exact: true }).click()
  await expect(page.getByRole('heading', { name: '离散数学知识地图', exact: true })).toBeVisible()
  await expect(page.getByText('命题与逻辑联结词', { exact: true }).first()).toBeVisible()
  await expect(page.locator('.svg-node')).toHaveCount(16)
  await page.locator('.svg-node.chapter-logic').first().click()
  await expect(page.getByText('前置知识', { exact: true })).toBeVisible()
  await shot(page, '07-离散数学知识图谱')

  // 教师：课程学情 -> 知识点 -> 热力图 -> 学生详情；跨课程仍然403。
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login(page, 'demo-teacher-a', '教师工作台')
  await expect(page.getByRole('heading', { name: '离散数学', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '班级知识掌握概览', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '学生 × 知识点掌握情况', exact: true })).toBeVisible()
  await expect(page.locator('.teacher-point-list article')).toHaveCount(16)
  await shot(page, '08-教师工作台')
  const firstStudent = page.locator('.heatmap-wrap tbody .text-button.strong').first()
  await expect(firstStudent).toBeVisible()
  await firstStudent.click()
  await expect(page.getByRole('heading', { name: '学生学情详情', exact: true })).toBeVisible()
  await shot(page, '09-学生学情详情')

  const denial = await page.evaluate(async courseId => {
    const session = JSON.parse(localStorage.getItem('edu-session'))
    const response = await fetch(`/api/v1/teacher/courses/${courseId}/analytics/overview`, {
      headers: { Authorization: `Bearer ${session.accessToken}` }
    })
    const payload = await response.json()
    return { status: response.status, code: payload.code }
  }, courseB.id)
  expect(denial).toEqual({ status: 403, code: 'FORBIDDEN' })

  // 管理员：模块化管理工作台 -> 教师授权 -> 知识图谱治理。
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login(page, 'demo-admin', '管理工作台')
  await expect(page.getByRole('heading', { name: '教学平台管理', exact: true })).toBeVisible()
  await expect(page.getByText('当前课程知识点')).toBeVisible()
  await expect(page.locator('.catalog-grid article')).toHaveCount(16)
  await shot(page, '10-管理工作台')
  await page.getByRole('button', { name: '教师授权', exact: true }).click()
  await expect(page.getByRole('heading', { name: '分配教师到课程', exact: true })).toBeVisible()
  await expect(page.getByText('张老师')).toBeVisible()
  await shot(page, '11-教师授权管理')
  await page.getByRole('button', { name: '知识图谱治理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '图谱版本', exact: true })).toBeVisible()
  await expect(page.getByText('《离散数学》核心知识先修关系演示图')).toBeVisible()
  await shot(page, '12-知识图谱治理')
})
