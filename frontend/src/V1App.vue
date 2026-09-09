<script setup>
import { computed, watchEffect } from 'vue'
import LoginView from './v1/views/LoginView.vue'
import StudentHomeView from './v1/views/StudentHomeView.vue'
import StudentCourseView from './v1/views/StudentCourseView.vue'
import StudentGraphView from './v1/views/StudentGraphView.vue'
import StudentPersonalView from './v1/views/StudentPersonalView.vue'
import StudentReportView from './v1/views/StudentReportView.vue'
import TeacherWorkspaceView from './v1/views/TeacherWorkspaceView.vue'
import QuestionBankView from './v1/views/QuestionBankView.vue'
import AdminWorkspaceView from './v1/views/AdminWorkspaceView.vue'
import {
  authenticated,
  canManage,
  canTeach,
  currentView,
  error,
  isStudent,
  logout,
  notice,
  roleName,
  session
} from './v1/store.js'
import './v1/styles.css'
import './v1/polish.css'
import './v1/realData.css'

const studentMenus = ['首页', '课程学习', '知识图谱', '个性化学习', '学习报告']
const teacherMenus = ['教师工作台', '题库管理']
const adminMenus = ['管理工作台']

const menus = computed(() => {
  if (canManage.value) return [...adminMenus, ...teacherMenus]
  if (canTeach.value) return teacherMenus
  return studentMenus
})

watchEffect(() => {
  if (authenticated.value && !menus.value.includes(currentView.value)) {
    currentView.value = menus.value[0] || '首页'
  }
})

function choose(view) {
  currentView.value = view
}
</script>

<template>
  <LoginView v-if="!authenticated" />

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-mark">数</div>
        <div>
          <strong>数学智慧学习平台</strong>
          <small>知识图谱 · 学习分析 · 个性化推荐</small>
        </div>
      </div>

      <nav class="side-nav">
        <button
          v-for="item in menus"
          :key="item"
          :class="{ active: currentView === item }"
          @click="choose(item)"
        >
          <span class="nav-dot"></span>{{ item }}
        </button>
      </nav>

      <div class="sidebar-note">
        <strong>平台主线</strong>
        <p>学习数据 → 知识结构 → 学习诊断 → 个性化推荐 → 学习反馈</p>
      </div>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <div>
          <p class="eyebrow">基于知识图谱与学习分析的中文智能学习平台</p>
          <h1>{{ currentView }}</h1>
        </div>
        <div class="user-area">
          <div class="avatar">{{ session?.user?.nickname?.slice(0, 1) || session?.user?.username?.slice(0, 1) || '用' }}</div>
          <div class="user-meta">
            <strong>{{ session?.user?.nickname || session?.user?.username }}</strong>
            <small>{{ roleName() }}</small>
          </div>
          <button class="text-button" @click="logout">退出登录</button>
        </div>
      </header>

      <main class="content-area">
        <div v-if="notice" class="toast success">{{ notice }}</div>
        <div v-if="error" class="toast danger">{{ error }}</div>

        <StudentHomeView v-if="isStudent && currentView === '首页'" />
        <StudentCourseView v-else-if="isStudent && currentView === '课程学习'" />
        <StudentGraphView v-else-if="isStudent && currentView === '知识图谱'" />
        <StudentPersonalView v-else-if="isStudent && currentView === '个性化学习'" />
        <StudentReportView v-else-if="isStudent && currentView === '学习报告'" />
        <TeacherWorkspaceView v-else-if="canTeach && currentView === '教师工作台'" />
        <QuestionBankView v-else-if="canTeach && currentView === '题库管理'" />
        <AdminWorkspaceView v-else-if="canManage && currentView === '管理工作台'" />
        <StudentHomeView v-else-if="isStudent" />
        <AdminWorkspaceView v-else-if="canManage" />
        <TeacherWorkspaceView v-else-if="canTeach" />
      </main>
    </div>
  </div>
</template>
