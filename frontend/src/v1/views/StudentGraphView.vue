<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, run, selectedCourse } from '../store.js'

const courses = ref([])
const graph = ref(null)
const query = ref('')
const selectedNode = ref(null)
const relationView = ref(null)

const chapterOrder = ['LOGIC', 'SETREL', 'GRAPH', 'ALG', 'OTHER']
const chapterNames = { LOGIC: '数理逻辑', SETREL: '集合论与关系', GRAPH: '图论', ALG: '代数结构', OTHER: '其他' }

function chapterOf(node) {
  const code = node.knowledgeCode || ''
  if (code.includes('LOGIC')) return 'LOGIC'
  if (code.includes('SET') || code.includes('REL')) return 'SETREL'
  if (code.includes('GRAPH')) return 'GRAPH'
  if (code.includes('ALG')) return 'ALG'
  return 'OTHER'
}

const filteredNodes = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return graph.value?.nodes || []
  return (graph.value?.nodes || []).filter(node => `${node.knowledgeName} ${node.knowledgeCode}`.toLowerCase().includes(keyword))
})

const layoutNodes = computed(() => {
  const groups = new Map()
  for (const node of graph.value?.nodes || []) {
    const chapter = chapterOf(node)
    if (!groups.has(chapter)) groups.set(chapter, [])
    groups.get(chapter).push(node)
  }
  const result = []
  chapterOrder.forEach((chapter, column) => {
    const nodes = groups.get(chapter) || []
    nodes.forEach((node, row) => {
      result.push({ ...node, chapter, x: 82 + column * 152, y: 88 + row * 118 })
    })
  })
  return result
})

const nodeMap = computed(() => new Map(layoutNodes.value.map(node => [node.id, node])))
const layoutEdges = computed(() => (graph.value?.edges || []).map(edge => ({
  ...edge,
  source: nodeMap.value.get(edge.sourceKnowledgePointId),
  target: nodeMap.value.get(edge.targetKnowledgePointId)
})).filter(edge => edge.source && edge.target))
const canvasHeight = computed(() => Math.max(520, ...layoutNodes.value.map(node => node.y + 78)))

async function load(course = null) {
  if (course) selectedCourse.value = course
  if (!selectedCourse.value) return
  graph.value = await run(() => api(`/courses/${selectedCourse.value.id}/graph`))
  selectedNode.value = null
  relationView.value = null
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!selectedCourse.value) selectedCourse.value = result.find(item => item.courseCode === 'DM-101') || result[0] || null
  if (selectedCourse.value) await load()
}

async function inspect(node) {
  selectedNode.value = node
  const [prerequisites, successors] = await Promise.all([
    api(`/knowledge-points/${node.id}/prerequisites`).catch(() => null),
    api(`/knowledge-points/${node.id}/successors`).catch(() => null)
  ])
  relationView.value = { prerequisites, successors }
}

onMounted(boot)
</script>

<template>
  <section class="dashboard-page">
    <div class="page-intro">
      <div><p class="eyebrow">知识图谱可视化</p><h2>离散数学知识地图</h2><p>按先修关系浏览知识脉络，点击节点查看前置知识、后续知识与学习入口。</p></div>
      <div class="intro-actions"><input v-model="query" class="search-input" placeholder="搜索知识点"><select :value="selectedCourse?.id" @change="load(courses.find(item => item.id === Number($event.target.value)))"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option></select></div>
    </div>

    <div class="graph-page-grid">
      <section class="panel graph-canvas-panel">
        <div class="graph-legend">
          <span v-for="chapter in chapterOrder.slice(0, 4)" :key="chapter"><i :class="`legend-${chapter.toLowerCase()}`"></i>{{ chapterNames[chapter] }}</span>
          <b v-if="graph">{{ graph.nodes?.length || 0 }} 个知识点 · {{ graph.edges?.length || 0 }} 条先修关系</b>
        </div>
        <div v-if="!graph" class="empty-state"><strong>当前课程暂无已发布知识图谱</strong><p>需要先完成知识关系审核与图谱发布。</p></div>
        <div v-else class="graph-scroll">
          <svg class="knowledge-svg" :viewBox="`0 0 640 ${canvasHeight}`" :style="{ minHeight: `${canvasHeight}px` }">
            <defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#8294ab" /></marker></defs>
            <g class="graph-lines">
              <line v-for="edge in layoutEdges" :key="edge.relationId" :x1="edge.source.x" :y1="edge.source.y + 31" :x2="edge.target.x" :y2="edge.target.y - 31" marker-end="url(#arrow)" />
            </g>
            <g v-for="node in layoutNodes" :key="node.id" class="svg-node" :class="[`chapter-${node.chapter.toLowerCase()}`, { dim: query && !filteredNodes.some(item => item.id === node.id), selected: selectedNode?.id === node.id }]" :transform="`translate(${node.x},${node.y})`" @click="inspect(node)">
              <circle r="30" />
              <text text-anchor="middle" dy="4">{{ node.knowledgeName.slice(0, 4) }}</text>
              <text class="node-label" text-anchor="middle" dy="51">{{ node.knowledgeName }}</text>
            </g>
          </svg>
        </div>
      </section>

      <aside class="panel node-detail-panel">
        <template v-if="selectedNode">
          <span class="soft-badge">{{ chapterNames[chapterOf(selectedNode)] }}</span>
          <h3>{{ selectedNode.knowledgeName }}</h3>
          <p class="muted">从课程知识结构理解它与其他知识的学习顺序。</p>
          <div class="detail-block">
            <strong>前置知识</strong>
            <p v-if="!relationView?.prerequisites?.nodes?.length">当前没有直接前置知识。</p>
            <button v-for="node in relationView?.prerequisites?.nodes || []" :key="node.id" class="relation-chip" @click="inspect(node)">{{ node.knowledgeName }}</button>
          </div>
          <div class="detail-block">
            <strong>后续知识</strong>
            <p v-if="!relationView?.successors?.nodes?.length">当前没有直接后续知识。</p>
            <button v-for="node in relationView?.successors?.nodes || []" :key="node.id" class="relation-chip" @click="inspect(node)">{{ node.knowledgeName }}</button>
          </div>
          <div class="detail-block resource-placeholder">
            <strong>知识点学习内容</strong>
            <p>定义、教材、课件、视频和练习资源将在教学资源中心关联后集中展示。</p>
          </div>
        </template>
        <div v-else class="empty-state compact"><strong>选择一个知识点</strong><p>点击左侧知识图谱中的节点，查看它在课程中的前置与后继关系。</p></div>
      </aside>
    </div>
  </section>
</template>
