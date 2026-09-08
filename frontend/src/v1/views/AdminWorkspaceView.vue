<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, run, statusText } from '../store.js'
import { researchSnapshot } from '../researchSnapshot.js'

const section = ref('课程与教学')
const sections = ['课程与教学', '数据与算法', '教师授权', '知识图谱治理', '系统状态']
const courses = ref([])
const selectedCourseId = ref('')
const points = ref([])
const exercises = ref([])
const assignments = ref([])
const versions = ref([])
const selectedVersion = ref(null)
const relations = ref([])
const validationIssues = ref([])
const teacherForm = reactive({ teacherId: '', assignmentRole: 'INSTRUCTOR' })
const courseForm = reactive({ courseCode: '', courseName: '', description: '', status: 'ACTIVE' })
const newVersionDescription = ref('')

const selectedCourse = computed(() => courses.value.find(item => String(item.id) === String(selectedCourseId.value)))
const data0 = researchSnapshot.dataset
const model3 = researchSnapshot.model

function count(value) {
  return Number(value).toLocaleString('zh-CN')
}

function pct(value, digits = 2) {
  return `${(Number(value) * 100).toFixed(digits)}%`
}

async function loadCourses() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!result.some(item => String(item.id) === String(selectedCourseId.value))) {
    selectedCourseId.value = result[0] ? String(result[0].id) : ''
  }
  if (selectedCourseId.value) await loadCourseData()
}

async function loadCourseData() {
  const id = Number(selectedCourseId.value)
  if (!id) return
  const loaded = await run(() => Promise.all([
    api(`/admin/knowledge-points?courseId=${id}`).catch(() => []),
    api(`/admin/exercise-units?courseId=${id}`).catch(() => []),
    api(`/admin/courses/${id}/teachers`).catch(() => []),
    api(`/admin/graph-versions?courseId=${id}`).catch(() => [])
  ]))
  if (!loaded) return
  points.value = loaded[0]
  exercises.value = loaded[1]
  assignments.value = loaded[2]
  versions.value = loaded[3]
  selectedVersion.value = null
  relations.value = []
  validationIssues.value = []
}

async function createCourse() {
  const result = await run(() => api('/admin/courses', { method: 'POST', body: JSON.stringify(courseForm) }), '课程已创建。')
  if (!result) return
  Object.assign(courseForm, { courseCode: '', courseName: '', description: '', status: 'ACTIVE' })
  await loadCourses()
}

async function saveTeacher() {
  const courseId = Number(selectedCourseId.value)
  const teacherId = Number(teacherForm.teacherId)
  if (!courseId || !teacherId) return
  const result = await run(() => api(`/admin/courses/${courseId}/teachers`, {
    method: 'POST', body: JSON.stringify({ teacherId, assignmentRole: teacherForm.assignmentRole })
  }), '教师课程授权已保存。')
  if (result) {
    teacherForm.teacherId = ''
    assignments.value = await api(`/admin/courses/${courseId}/teachers`)
  }
}

async function disableTeacher(item) {
  const result = await run(() => api(`/admin/courses/${selectedCourseId.value}/teachers/${item.teacherId}`, { method: 'DELETE' }), '教师课程授权已停用。')
  if (result !== null) assignments.value = await api(`/admin/courses/${selectedCourseId.value}/teachers`)
}

async function createVersion() {
  const id = Number(selectedCourseId.value)
  if (!id) return
  const result = await run(() => api('/admin/graph-versions', {
    method: 'POST', body: JSON.stringify({ courseId: id, description: newVersionDescription.value || null, copyActive: true })
  }), '新的知识图谱草稿已创建。')
  if (result) {
    newVersionDescription.value = ''
    versions.value = await api(`/admin/graph-versions?courseId=${id}`)
    await openVersion(result.id)
  }
}

async function openVersion(id) {
  const loaded = await run(() => Promise.all([
    api(`/admin/graph-versions/${id}`),
    api(`/admin/graph-versions/${id}/relations`),
    api(`/admin/graph-versions/${id}/validation-issues`)
  ]))
  if (!loaded) return
  selectedVersion.value = loaded[0]
  relations.value = loaded[1]
  validationIssues.value = loaded[2]
}

async function validateVersion() {
  if (!selectedVersion.value) return
  const result = await run(() => api(`/admin/graph-versions/${selectedVersion.value.id}/validate`, { method: 'POST' }), '知识关系校验已完成。')
  if (result) await openVersion(selectedVersion.value.id)
}

async function publishVersion() {
  if (!selectedVersion.value) return
  const result = await run(() => api(`/admin/graph-versions/${selectedVersion.value.id}/publish`, { method: 'POST' }), '图谱发布请求已提交。')
  if (result) await openVersion(selectedVersion.value.id)
}

onMounted(loadCourses)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro"><div><p class="eyebrow">管理工作台</p><h2>教学平台管理</h2><p>课程、科研数据、算法结果、教师授权和知识图谱治理按业务模块分开展示。</p></div><select v-model="selectedCourseId" @change="loadCourseData"><option v-for="course in courses" :key="course.id" :value="String(course.id)">{{ course.courseName }}</option></select></div>

    <div class="admin-section-tabs">
      <button v-for="item in sections" :key="item" :class="{ active: section === item }" @click="section = item">{{ item }}</button>
    </div>

    <template v-if="section === '课程与教学'">
      <div class="metric-grid three">
        <article class="metric-card accent-blue"><span>平台课程</span><strong>{{ courses.length }}</strong><small>门</small></article>
        <article class="metric-card accent-green"><span>当前课程知识点</span><strong>{{ points.length }}</strong><small>个</small></article>
        <article class="metric-card accent-purple"><span>当前课程练习单元</span><strong>{{ exercises.length }}</strong><small>组</small></article>
      </div>
      <div class="two-column admin-layout">
        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">课程列表</p><h3>课程与教学内容</h3></div></div>
          <article v-for="course in courses" :key="course.id" class="admin-list-card" :class="{ selected: course.id === Number(selectedCourseId) }" @click="selectedCourseId = String(course.id); loadCourseData()"><div><strong>{{ course.courseName }}</strong><span>{{ course.courseCode }}</span><p>{{ course.description }}</p></div><span class="soft-badge">{{ statusText(course.status) }}</span></article>
        </section>
        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">新建课程</p><h3>课程基础信息</h3></div></div>
          <form class="modern-form" @submit.prevent="createCourse"><label>课程编码<input v-model="courseForm.courseCode" required placeholder="例如 MATH-202"></label><label>课程名称<input v-model="courseForm.courseName" required placeholder="请输入中文课程名称"></label><label>课程说明<textarea v-model="courseForm.description" placeholder="课程定位、适用对象和主要内容"></textarea></label><button class="primary-button">创建课程</button></form>
        </section>
      </div>
      <section class="panel"><div class="panel-head"><div><p class="eyebrow">知识目录</p><h3>{{ selectedCourse?.courseName }} · 知识点</h3></div><span class="soft-badge">{{ points.length }} 项</span></div><div class="catalog-grid"><article v-for="point in points" :key="point.id"><strong>{{ point.knowledgeName }}</strong><span>{{ point.knowledgeCode }}</span></article></div></section>
    </template>

    <template v-else-if="section === '数据与算法'">
      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">DATA-0 已审计快照</p><h3>{{ data0.displayName }}</h3><p class="muted">{{ data0.provenance }}</p></div><span class="soft-badge">{{ data0.id }}</span></div>
        <div class="metric-grid four">
          <article class="metric-card accent-blue"><span>匿名学生</span><strong>{{ count(data0.students) }}</strong><small>人</small></article>
          <article class="metric-card accent-green"><span>学习行为</span><strong>{{ count(data0.interactions) }}</strong><small>条</small></article>
          <article class="metric-card accent-purple"><span>练习元数据</span><strong>{{ count(data0.metadataRows) }}</strong><small>{{ count(data0.distinctExerciseIds) }} 个不同ID</small></article>
          <article class="metric-card accent-orange"><span>整体正确率</span><strong>{{ pct(data0.correctRate) }}</strong><small>研究数据统计</small></article>
        </div>
        <p class="muted">{{ data0.boundary }}</p>
      </section>

      <div class="two-column admin-layout">
        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">数据处理链</p><h3>从原始资料到模型输入</h3></div></div>
          <article v-for="(item, index) in researchSnapshot.pipeline" :key="item" class="simple-row"><span>{{ index + 1 }}. {{ item }}</span><b>已记录</b></article>
        </section>
        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">数据质量</p><h3>审计发现</h3></div></div>
          <article class="simple-row"><span>知识领域 / 主题</span><b>{{ data0.areas }} / {{ data0.topics }}</b></article>
          <article class="simple-row"><span>原始先修关系</span><b>{{ count(data0.rawPrerequisiteEdges) }} 条</b></article>
          <article class="simple-row"><span>重复练习ID记录</span><b>{{ data0.duplicateExerciseIdRecords }}</b></article>
          <article class="simple-row"><span>缺失主题 / 领域记录</span><b>{{ data0.missingTopicRows }} / {{ data0.missingAreaRows }}</b></article>
          <article class="simple-row"><span>原始关系自环 / 有环SCC</span><b>{{ data0.selfLoops }} / {{ data0.cyclicSccs }}</b></article>
        </section>
      </div>

      <section class="panel">
        <div class="panel-head"><div><p class="eyebrow">{{ model3.experiment }} 最终验证</p><h3>算法比较与当前集成边界</h3></div><span class="soft-badge">{{ model3.gate }}</span></div>
        <div class="metric-grid four">
          <article class="metric-card"><span>{{ model3.baselineName }}</span><strong>{{ model3.baselineAuc.toFixed(6) }}</strong><small>AUC</small></article>
          <article class="metric-card accent-blue"><span>{{ model3.raschName }}</span><strong>{{ model3.raschAuc.toFixed(6) }}</strong><small>AUC · ACC {{ model3.raschAcc.toFixed(6) }}</small></article>
          <article class="metric-card accent-green"><span>Rasch 相对基线</span><strong>+{{ model3.aucDelta.toFixed(6) }}</strong><small>95%CI [{{ model3.aucCiLow.toFixed(6) }}, {{ model3.aucCiHigh.toFixed(6) }}]</small></article>
          <article class="metric-card accent-orange"><span>{{ model3.hierarchicalName }}</span><strong>{{ model3.hierarchicalAuc.toFixed(6) }}</strong><small>AUC，未优于 Rasch</small></article>
        </div>
        <div class="warning-box"><strong>不能混淆研究结果与线上算法</strong><p>{{ model3.productionBoundary }}</p></div>
      </section>
    </template>

    <template v-else-if="section === '教师授权'">
      <div class="two-column admin-layout">
        <section class="panel"><div class="panel-head"><div><p class="eyebrow">课程教师</p><h3>{{ selectedCourse?.courseName }}</h3></div></div><article v-for="item in assignments" :key="item.id" class="simple-row"><div><strong>{{ item.displayName }}</strong><span>教师编号 {{ item.teacherId }} · {{ item.assignmentRole === 'OWNER' ? '课程负责人' : '授课教师' }}</span></div><div><span class="soft-badge">{{ statusText(item.status) }}</span><button v-if="item.status === 'ACTIVE'" class="text-button danger-text" @click="disableTeacher(item)">停用</button></div></article><div v-if="!assignments.length" class="empty-state compact"><strong>暂无课程教师</strong><p>为课程分配授课教师后，他们才能访问课程内容和班级学情。</p></div></section>
        <section class="panel"><div class="panel-head"><div><p class="eyebrow">新增授权</p><h3>分配教师到课程</h3></div></div><form class="modern-form" @submit.prevent="saveTeacher"><label>教师编号<input v-model="teacherForm.teacherId" type="number" required placeholder="输入平台教师编号"></label><label>教学角色<select v-model="teacherForm.assignmentRole"><option value="INSTRUCTOR">授课教师</option><option value="OWNER">课程负责人</option></select></label><button class="primary-button">保存授权</button></form></section>
      </div>
    </template>

    <template v-else-if="section === '知识图谱治理'">
      <div class="two-column admin-layout graph-admin-layout">
        <section class="panel"><div class="panel-head"><div><p class="eyebrow">{{ selectedCourse?.courseName }}</p><h3>图谱版本</h3></div></div><article v-for="version in versions" :key="version.id" class="admin-list-card" :class="{ selected: selectedVersion?.id === version.id }" @click="openVersion(version.id)"><div><strong>版本 {{ version.versionNo }}</strong><span>{{ version.description || '暂无版本说明' }}</span></div><div><span class="soft-badge">{{ statusText(version.status) }}</span><small v-if="version.active">当前在线</small></div></article><form class="inline-create" @submit.prevent="createVersion"><input v-model="newVersionDescription" placeholder="新版本说明"><button class="primary-button small">从当前图创建草稿</button></form></section>
        <section class="panel"><template v-if="selectedVersion"><div class="panel-head"><div><p class="eyebrow">版本详情</p><h3>图谱版本 {{ selectedVersion.versionNo }}</h3></div><span class="soft-badge">{{ statusText(selectedVersion.status) }}</span></div><div class="metric-grid two compact-metrics"><article class="metric-card"><span>知识关系</span><strong>{{ relations.length }}</strong></article><article class="metric-card"><span>校验问题</span><strong>{{ validationIssues.length }}</strong></article></div><div class="button-row"><button class="secondary-button" @click="validateVersion">运行关系校验</button><button class="primary-button" :disabled="selectedVersion.status !== 'READY'" @click="publishVersion">发布知识图谱</button></div><h4>已维护知识关系</h4><article v-for="relation in relations.slice(0, 12)" :key="relation.id" class="simple-row"><span>{{ relation.sourceKnowledgeName }} → {{ relation.targetKnowledgeName }}</span><b>{{ statusText(relation.reviewStatus) }}</b></article><div v-if="validationIssues.length" class="warning-box"><strong>需要处理的校验问题</strong><p v-for="item in validationIssues" :key="item.id">{{ item.issueCode }}：{{ item.detailJson }}</p></div></template><div v-else class="empty-state"><strong>选择一个图谱版本</strong><p>版本化治理保证新知识关系通过校验和投影验证后才会替换当前在线图。</p></div></section>
      </div>
    </template>

    <template v-else>
      <section class="panel"><div class="panel-head"><div><p class="eyebrow">系统状态</p><h3>Java 工程底座</h3></div><span class="soft-badge">发布候选</span></div><div class="system-cap-grid"><article><strong>MySQL 8.4</strong><span>业务权威数据与图谱版本事实</span></article><article><strong>Neo4j</strong><span>已发布知识图查询投影，可从 MySQL 重建</span></article><article><strong>Redis</strong><span>可选缓存，故障时系统进入降级状态</span></article><article><strong>Spring Boot</strong><span>认证、课程、答题、推荐、图谱与学情服务</span></article><article><strong>Playwright</strong><span>学生、教师、管理员真实浏览器回归</span></article><article><strong>Docker Compose</strong><span>完整本地评审环境一键启动</span></article></div></section>
    </template>
  </section>
</template>
