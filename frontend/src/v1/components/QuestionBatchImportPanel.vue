<script setup>
import { computed, ref } from 'vue'
import { api, run } from '../store.js'

const props = defineProps({
  exercises: { type: Array, default: () => [] }
})
const emit = defineEmits(['imported'])

const rawJson = ref('')
const fileName = ref('')
const preview = ref(null)
const result = ref(null)

const exerciseByCode = computed(() => new Map(
  props.exercises.map(exercise => [String(exercise.exerciseCode || '').trim(), exercise])
))

function template() {
  const exercise = props.exercises[0]
  rawJson.value = JSON.stringify([
    {
      exerciseCode: exercise?.exerciseCode || '请填写练习单元编码',
      questionType: 'SINGLE_CHOICE',
      stem: '',
      answerOptionKeys: ['A'],
      explanation: '',
      difficulty: 50,
      status: 'ACTIVE',
      options: [
        { optionKey: 'A', optionText: '', sortOrder: 1 },
        { optionKey: 'B', optionText: '', sortOrder: 2 },
        { optionKey: 'C', optionText: '', sortOrder: 3 },
        { optionKey: 'D', optionText: '', sortOrder: 4 }
      ]
    }
  ], null, 2)
  preview.value = null
  result.value = null
}

async function readFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  fileName.value = file.name
  rawJson.value = await file.text()
  preview.value = null
  result.value = null
}

function normalize() {
  let rows
  try {
    rows = JSON.parse(rawJson.value)
  } catch (error) {
    throw new Error(`JSON 解析失败：${error.message}`)
  }
  if (!Array.isArray(rows)) throw new Error('批量题库文件必须是 JSON 数组。')
  if (!rows.length) throw new Error('批量题库不能为空。')
  if (rows.length > 200) throw new Error('单次最多导入 200 道题，请拆分批次。')

  const errors = []
  const questions = rows.map((row, index) => {
    const line = index + 1
    const exerciseCode = String(row?.exerciseCode || '').trim()
    const exercise = exerciseByCode.value.get(exerciseCode)
    if (!exercise) errors.push(`第 ${line} 条：找不到当前课程中的练习编码 ${exerciseCode || '（空）'}`)
    if (!String(row?.stem || '').trim()) errors.push(`第 ${line} 条：题干不能为空`)
    if (!Array.isArray(row?.options) || row.options.length < 2) errors.push(`第 ${line} 条：至少需要 2 个选项`)
    if (!Array.isArray(row?.answerOptionKeys) || !row.answerOptionKeys.length) errors.push(`第 ${line} 条：正确答案不能为空`)
    return {
      exerciseUnitId: exercise?.id || null,
      questionType: row?.questionType || 'SINGLE_CHOICE',
      stem: String(row?.stem || '').trim(),
      answerOptionKeys: row?.answerOptionKeys || [],
      explanation: String(row?.explanation || '').trim() || null,
      difficulty: row?.difficulty ?? 50,
      status: row?.status || 'ACTIVE',
      options: (row?.options || []).map((option, optionIndex) => ({
        optionKey: String(option?.optionKey || '').trim(),
        optionText: String(option?.optionText || '').trim(),
        sortOrder: option?.sortOrder ?? optionIndex + 1
      }))
    }
  })
  return { rows, questions, errors }
}

function validatePreview() {
  result.value = null
  try {
    const parsed = normalize()
    preview.value = {
      total: parsed.rows.length,
      valid: parsed.errors.length === 0,
      errors: parsed.errors,
      exerciseCount: new Set(parsed.questions.map(item => item.exerciseUnitId).filter(Boolean)).size
    }
  } catch (error) {
    preview.value = { total: 0, valid: false, errors: [error.message], exerciseCount: 0 }
  }
}

async function importBatch() {
  let parsed
  try {
    parsed = normalize()
  } catch (error) {
    preview.value = { total: 0, valid: false, errors: [error.message], exerciseCount: 0 }
    return
  }
  preview.value = {
    total: parsed.rows.length,
    valid: parsed.errors.length === 0,
    errors: parsed.errors,
    exerciseCount: new Set(parsed.questions.map(item => item.exerciseUnitId).filter(Boolean)).size
  }
  if (parsed.errors.length) return

  const response = await run(() => api('/admin/questions/batch', {
    method: 'POST',
    body: JSON.stringify({ questions: parsed.questions })
  }), `已批量导入 ${parsed.questions.length} 道题。`)
  if (!response) return
  result.value = response
  emit('imported', response)
}
</script>

<template>
  <section class="panel">
    <div class="panel-head">
      <div>
        <p class="eyebrow">批量维护</p>
        <h3>JSON 批量导入题目</h3>
      </div>
      <button type="button" class="secondary-button" @click="template">填入安全模板</button>
    </div>

    <div class="warning-box">
      <strong>仅导入有权使用的题目内容</strong>
      <p>JSON 中使用当前课程的 exerciseCode 关联练习单元。系统只负责校验和保存，不会根据 Junyi metadata 或网络内容自动生成题干、答案和解析。任一道题校验或权限失败，整批事务回滚。</p>
    </div>

    <div class="modern-form">
      <label>选择 JSON 文件
        <input type="file" accept="application/json,.json" @change="readFile">
        <small v-if="fileName" class="muted">已读取：{{ fileName }}</small>
      </label>
      <label>或直接粘贴 JSON 数组
        <textarea v-model="rawJson" rows="14" placeholder="先点击“填入安全模板”，再替换为空白题干和选项内容。"></textarea>
      </label>
      <div class="intro-actions">
        <button type="button" class="secondary-button" @click="validatePreview">校验数据</button>
        <button type="button" class="primary-button" :disabled="!rawJson.trim()" @click="importBatch">批量导入</button>
      </div>
    </div>

    <div v-if="preview" class="detail-block">
      <strong>导入预检</strong>
      <p class="muted">题目 {{ preview.total }} 道；涉及练习单元 {{ preview.exerciseCount }} 个；状态：{{ preview.valid ? '可提交' : '需要修正' }}。</p>
      <ul v-if="preview.errors.length" class="muted">
        <li v-for="item in preview.errors.slice(0, 12)" :key="item">{{ item }}</li>
      </ul>
    </div>

    <div v-if="result" class="detail-block">
      <strong>最近一次结果</strong>
      <p class="muted">成功创建 {{ result.createdCount }} 道题。可以切换到对应练习单元检查题目内容。</p>
    </div>
  </section>
</template>
