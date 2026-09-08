<script setup>
import { reactive, ref } from 'vue'
import { error, loading, login, register } from '../store.js'

const mode = ref('login')
const form = reactive({ username: '', password: '', nickname: '' })

async function submit() {
  if (mode.value === 'login') await login(form.username, form.password)
  else await register(form.username, form.password, form.nickname)
}

function fillDemo(username) {
  form.username = username
  form.password = 'LocalDemoOnly!2026'
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-badge">离散数学数字教材 · 知识图谱教学应用</div>
      <h1>让知识结构真正参与学习</h1>
      <p>
        围绕数理逻辑、集合论与关系、图论、代数结构构建课程知识图谱，
        将学习记录、薄弱知识、个性化推荐与教师评价连接起来。
      </p>
      <div class="hero-features">
        <article><strong>知识图谱</strong><span>查看先修关系与知识脉络</span></article>
        <article><strong>个性化学习</strong><span>根据学习记录生成学习建议</span></article>
        <article><strong>教学评价</strong><span>教师从班级到学生逐层分析</span></article>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-brand">
        <div class="brand-mark large">离</div>
        <div><strong>离散数学智慧教学平台</strong><small>教学、学习与知识图谱一体化</small></div>
      </div>

      <div class="login-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">账号登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">学生注册</button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>用户名<input v-model="form.username" autocomplete="username" required maxlength="64" placeholder="请输入用户名"></label>
        <label v-if="mode === 'register'">姓名或昵称<input v-model="form.nickname" maxlength="64" placeholder="请输入姓名或昵称"></label>
        <label>密码<input v-model="form.password" type="password" autocomplete="current-password" required :minlength="mode === 'register' ? 12 : 1" maxlength="128" placeholder="请输入密码"></label>
        <p v-if="error" class="inline-error">{{ error }}</p>
        <button class="primary-button full" :disabled="loading">{{ mode === 'login' ? '登录平台' : '注册并进入平台' }}</button>
      </form>

      <div v-if="mode === 'login'" class="demo-accounts">
        <span>本地演示账号</span>
        <button @click="fillDemo('demo-student-alice')">学生</button>
        <button @click="fillDemo('demo-teacher-a')">教师</button>
        <button @click="fillDemo('demo-admin')">管理员</button>
      </div>
      <p class="login-footnote">演示账号均为合成平台数据，不使用 Junyi 匿名科研学生身份。</p>
    </section>
  </div>
</template>
