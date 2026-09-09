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
      <div class="hero-badge">数学学习 · 知识图谱 · 学习分析</div>
      <h1>让数据、知识结构和学习路径真正连接起来</h1>
      <p>
        平台以课程知识结构和学习行为为基础，将知识图谱、薄弱知识诊断、
        个性化推荐、学习路径和教师学情分析连接为一套可解释的学习闭环。
      </p>
      <div class="hero-features">
        <article><strong>知识图谱</strong><span>查看知识领域、先修关系与学习脉络</span></article>
        <article><strong>个性化学习</strong><span>根据真实学习记录生成学习建议</span></article>
        <article><strong>教学分析</strong><span>教师从班级到学生逐层查看学情</span></article>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-brand">
        <div class="brand-mark large">数</div>
        <div><strong>数学智慧学习平台</strong><small>数据、知识图谱与个性化学习一体化</small></div>
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
      <p class="login-footnote">演示账号均为合成平台数据，不使用科研数据中的匿名学生身份。</p>
    </section>
  </div>
</template>
