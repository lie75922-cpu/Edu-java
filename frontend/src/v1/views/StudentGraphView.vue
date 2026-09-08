<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, run, selectedCourse } from '../store.js'

const courses = ref([])
const areas = ref([])
const points = ref([])
const graph = ref(null)
const query = ref('')
const selectedNode = ref(null)
const relationView = ref(null)

const pointMap = computed(() => new Map(points.value.map(point => [Number(point.id), point])))
const areaMap = computed(() => new Map(areas.value.map(area => [String(area.id), area])))

function areaOf(node) {
  const point = pointMap.value.get(Number(node.id))
  return areaMap.value.get(String(point?.areaId)) || null
}

function areaName(node) {
  return areaOf(node)?.areaName || '其他知识'
}

const filteredNodes = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return graph.value?.nodes || []
  return (graph.value?.nodes || []).filter(node =>
    `${node.knowledgeName} ${node.knowledgeCode} ${areaName(node)}`.toLowerCase().includes(keyword)
  )
})

const groups = computed(() => {
  const nodes = graph.value?.nodes || []
  const byArea = new Map()
  for (const area of areas.value) byArea.set(String(area.id), [])
  const other = []
  for (const node of nodes) {
    const point = pointMap.value.get(Number(node.id))
    const key = point?.areaId == null ? null : String(point.areaId)
    if (key && byArea.has(key)) byArea.get(key).push(node)
    else other.push(node)
  }
  const result = areas.value
    .map(area => ({ id: String(area.id), name: area.areaName, nodes: byArea.get(String(area.id)) || [] }))
    .filter(group => group.nodes.length)
  if (other.length) result.push({ id: 'OTHER', name: '其他知识', nodes: other })
  return result
})

const layoutNodes = computed(() => {
  const result = []
  groups.value.forEach((group, column) => {
    [...group.nodes]
      .sort((left, right) => String(left.knowledgeName).localeCompare(String(right.knowledgeName), 'zh-CN'))
      .forEach((node, row) => {
        result.push({ ...node, areaKey: group.id, areaName: group.name, palette: column % 4, x: 90 + column * 170, y: 88 + row * 112 })
      })
  })
  return result
})

const nodeMap = computed(() => new Map(layoutNodes.value.map(node => [Number(node.id), node])))
const layoutEdges = computed(() => (graph.value?.edges || []).map(edge => ({
  ...edge,
  source: nodeMap.value.get(Number(edge.sourceKnowledgePointId)),
  target: nodeMap.value.get(Number(edge.targetKnowledgePointId))
})).filter(edge => edge.source && edge.target))
const canvasWidth = computed(() => Math.max(720, 190 + Math.max(0, groups.value.length - 1) * 170))
const canvasHeight = computed(() => Math.max(520, ...layoutNodes.value.map(node => node.y + 80)))

function paletteClass(index) {
  return ['chapter-logic', 'chapter-setrel', 'chapter-graph', 'chapter-alg'][Number(index || 0) % 4]
}

function legendClass(index) {
  return ['legend-logic', 'legend-setrel', 'legend-graph', 'legend-alg'][Number(index || 0) % 4]
}

async function load(course = null) {
  if (course) selectedCourse.value = course
  if (!selectedCourse.value) return
  const id = selectedCourse.value.id
  const [loadedGraph, loadedAreas, loadedPoints] = await run(() => Promise.all([
    api(`/courses/${id}/graph`),
    api(`/courses/${id}/knowledge-areas`).catch(() => []),
    api(`/courses/${id}/knowledge-points`).catch(() => [])
  ])) || []
  graph.value = loadedGraph || null
  areas.value = loadedAreas || []
  points.value = loadedPoints || []
  selectedNode.value = null
  relationView.value = null
}

async function boot() {
  const result = await run(() => api('/courses'))
  if (!result) return
  courses.value = result
  if (!selectedCourse.value || !result.some(item => item.id === selectedCourse.value.id)) {
    selectedCourse.value = result[0] || null
  }
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
      <div>
        <p class="eyebrow">知识图谱可视化</p>
        <h2>{{ selectedCourse?.courseName || '课程' }}知识地图</h2>
        <p>课程领域、知识节点与先修关系全部来自后台数据；点击节点可查看前置、后继和学习入口。</p>
      </div>
      <div class="intro-actions">
        <input v-model="query" class="search-input" placeholder="搜索知识点或知识领域">
        <select :value="selectedCourse?.id" @change="load(courses.find(item => item.id === Number($event.target.value)))">
          <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseName }}</option>
        </select>
      </div>
    </div>

    <div class="graph-page-grid">
      <section class="panel graph-canvas-panel">
        <div class="graph-legend">
          <span v-for="(group, index) in groups" :key="group.id"><i :class="legendClass(index)"></i>{{ group.name }}</span>
          <b v-if="graph">{{ graph.nodes?.length || 0 }} 个知识点 · {{ graph.edges?.length || 0 }} 条先修关系</b>
        </div>
        <div v-if="!graph" class="empty-state"><strong>当前课程暂无已发布知识图谱</strong><p>需要先完成知识关系审核与图谱发布。</p></div>
        <div v-else class="graph-scroll">
          <svg class="knowledge-svg" :viewBox="`0 0 ${canvasWidth} ${canvasHeight}`" :style="{ minHeight: `${canvasHeight}px`, minWidth: `${canvasWidth}px` }">
            <defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#8294ab" /></marker></defs>
            <g class="graph-lines">
              <line v-for="edge in layoutEdges" :key="edge.relationId" :x1="edge.source.x" :y1="edge.source.y + 31" :x2="edge.target.x" :y2="edge.target.y - 31" marker-end="url(#arrow)" />
            </g>
            <g v-for="node in layoutNodes" :key="node.id" class="svg-node" :class="[paletteClass(node.palette), { dim: query && !filteredNodes.some(item => Number(item.id) === Number(node.id)), selected: Number(selectedNode?.id) === Number(node.id) }]" :transform="`translate(${node.x},${node.y})`" @click="inspect(node)">
              <circle r="30" />
              <text text-anchor="middle" dy="4">{{ node.knowledgeName.slice(0, 4) }}</text>
              <text class="node-label" text-anchor="middle" dy="51">{{ node.knowledgeName }}</text>
            </g>
          </svg>
        </div>
      </section>

      <aside class="panel node-detail-panel">
        <template v-if="selectedNode">
          <span class="soft-badge">{{ areaName(selectedNode) }}</span>
          <h3>{{ selectedNode.knowledgeName }}</h3>
          <p class="muted">通过课程知识关系理解当前知识点在学习路径中的位置。</p>
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
            <p>当前可关联练习；教材、课件、视频等资源需要正式资源模型接入后再展示，不使用前端伪造资源。</p>
          </div>
        </template>
        <div v-else class="empty-state compact"><strong>选择一个知识点</strong><p>点击左侧知识图谱中的节点，查看它的知识领域以及前置、后继关系。</p></div>
      </aside>
    </div>
  </section>
</template>
