import { expect, test } from '@playwright/test'

const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'
const studentUsername = process.env.E2E_STUDENT_USERNAME || 'demo-student-alice'

async function login(page) {
  await page.goto('/')
  await page.getByLabel('用户名').fill(studentUsername)
  await page.getByLabel('密码').fill(demoPassword)
  await page.getByRole('button', { name: '登录平台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '首页', exact: true })).toBeVisible()
}

test('学生可查看基于真实平台记录生成的学习报告', async ({ page }) => {
  await login(page)
  await page.getByRole('button', { name: '学习报告', exact: true }).click()

  await expect(page.getByRole('heading', { name: '我的学习报告', exact: true })).toBeVisible()
  await expect(page.getByText('累计作答', { exact: true })).toBeVisible()
  await expect(page.getByText('答题正确率', { exact: true })).toBeVisible()
  await expect(page.getByText('平均掌握情况', { exact: true })).toBeVisible()
  await expect(page.getByText('薄弱知识', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '当前薄弱知识', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '最近掌握度更新', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '最近练习', exact: true })).toBeVisible()

  await expect(page.locator('.toast.danger')).toHaveCount(0)
})
