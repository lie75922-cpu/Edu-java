<script setup>
const modules = [
  '课程与题库',
  '学习与答题',
  '知识图谱',
  '认知诊断',
  '个性化推荐',
  '教师分析'
]
</script>

<template>
  <main>
    <h1>Edu-java</h1>
    <p>基于知识图谱与图认知诊断的个性化学习平台</p>
    <p><strong>当前阶段：</strong>DATA-0 + V0.1 工程骨架</p>
    <section>
      <h2>规划模块</h2>
      <ul>
        <li v-for="module in modules" :key="module">{{ module }}</li>
      </ul>
    </section>
    <p class="note">V0.1 不展示伪造模型分数或虚构学生数据。</p>
  </main>
</template>

<style scoped>
main {
  max-width: 760px;
  margin: 64px auto;
  padding: 0 24px;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.7;
}
h1 { margin-bottom: 8px; }
section { margin-top: 32px; }
.note { margin-top: 32px; color: #666; }
</style>
