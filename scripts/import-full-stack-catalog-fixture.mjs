import { readFile } from 'node:fs/promises'
import path from 'node:path'

const apiBase = (process.env.RELEASE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const fixtureDirectory = path.join(
  process.cwd(),
  'backend',
  'src',
  'test',
  'resources',
  'real-data-v1',
  'business_export_fixture'
)
const courseCode = 'CI-REAL-DATA-FIXTURE'
const courseName = 'CI 真实目录导入样例'
const adminUsername = process.env.E2E_ADMIN_USERNAME || 'demo-admin'
const demoPassword = process.env.E2E_PASSWORD || 'LocalDemoOnly!2026'

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function readJson(fileName) {
  return JSON.parse(await readFile(path.join(fixtureDirectory, fileName), 'utf8'))
}

async function readJsonLines(fileName) {
  const content = await readFile(path.join(fixtureDirectory, fileName), 'utf8')
  return content
    .split(/\r?\n/)
    .filter(line => line.trim())
    .map(line => JSON.parse(line))
}

function textOrFallback(value, fallback) {
  return value === undefined || value === null || String(value).trim() === '' ? fallback : String(value)
}

function decimalOrNull(value) {
  if (value === undefined || value === null || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

async function request(pathname, { method = 'GET', token, body } = {}) {
  const headers = { 'X-Request-Id': `release-fixture-${Date.now()}` }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${apiBase}${pathname}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : await response.text()
  assert(response.status === 200, `${method} ${pathname} expected 200, received ${response.status}`)
  assert(payload?.code === 'OK', `${method} ${pathname} did not return the standard success envelope`)
  return payload.data
}

async function loginAdministrator() {
  for (let attempt = 1; attempt <= 30; attempt += 1) {
    const response = await fetch(`${apiBase}/api/v1/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `release-fixture-login-${Date.now()}`
      },
      body: JSON.stringify({ username: adminUsername, password: demoPassword })
    })
    const contentType = response.headers.get('content-type') || ''
    const payload = contentType.includes('json') ? await response.json() : await response.text()
    if (response.status === 200 && payload?.code === 'OK' && payload.data?.accessToken) return payload.data
    if (response.status !== 401) {
      throw new Error(`administrator login expected 200 or a transient 401, received ${response.status}`)
    }
    await new Promise(resolve => setTimeout(resolve, 1_000))
  }
  throw new Error('administrator demo seed did not become available within 30 seconds')
}

const [manifest, areas, topics, exercises, mappings] = await Promise.all([
  readJson('manifest.json'),
  readJsonLines('areas.jsonl'),
  readJsonLines('topics.jsonl'),
  readJsonLines('exercises.jsonl'),
  readJsonLines('exercise_topic_mapping.jsonl')
])

const topicByExercise = new Map(mappings.map(mapping => [mapping.exercise_external_id, mapping.topic_external_id || null]))
const importRequest = {
  courseCode,
  courseName,
  areas: areas.map(area => ({
    externalId: area.area_external_id,
    areaName: area.display_name_zh,
    rawArea: area.raw_area,
    displayNameZh: area.display_name_zh,
    displayMappingStatus: area.display_mapping_status,
    businessMappingStatus: area.business_mapping_status,
    provenance: area.provenance || {}
  })),
  topics: topics.map(topic => ({
    externalId: topic.topic_external_id,
    topicName: topic.display_name_zh,
    areaExternalId: topic.area_external_id || null,
    rawTopic: topic.raw_topic,
    displayNameZh: topic.display_name_zh,
    displayMappingStatus: topic.display_mapping_status,
    businessMappingStatus: topic.business_mapping_status,
    provenance: topic.provenance || {}
  })),
  exercises: exercises.map(exercise => {
    const rawSourceFields = exercise.raw_source_fields || {}
    const rawExerciseName = textOrFallback(rawSourceFields.name, exercise.exercise_external_id)
    const displayName = textOrFallback(exercise.display_name_zh, rawExerciseName)
    return {
      externalId: exercise.exercise_external_id,
      exerciseName: displayName,
      topicExternalId: topicByExercise.get(exercise.exercise_external_id) || null,
      difficulty: decimalOrNull(rawSourceFields.difficulty),
      rawExerciseName,
      displayNameZh: displayName,
      displayMappingStatus: exercise.display_mapping_status,
      businessMappingStatus: exercise.business_mapping_status,
      sourceMetadataRowNumber: exercise.source_metadata_row_number || null,
      rawSourceFields,
      provenance: exercise.provenance || {}
    }
  }),
  metadata: {
    exportFormatVersion: manifest.export_format_version,
    inputPath: manifest.input.metadata_path,
    inputSizeBytes: manifest.input.size_bytes,
    inputEncoding: manifest.input.encoding,
    inputColumns: manifest.input.columns,
    sourceRecordCount: manifest.input.row_count
  }
}

const login = await loginAdministrator()

const dryRun = await request('/api/v1/admin/seed-imports/dry-run', {
  method: 'POST',
  token: login.accessToken,
  body: importRequest
})
assert(dryRun.createdCourses === 1 && dryRun.createdAreas === 1 && dryRun.createdKnowledgePoints === 1,
  'fixture dry-run did not predict the expected catalog projection')
assert(dryRun.createdExerciseUnits === 1 && dryRun.createdMappings === 1 && dryRun.conflictCount === 0,
  'fixture dry-run did not preserve the expected conflict-free catalog projection')

const applied = await request('/api/v1/admin/seed-imports/apply', {
  method: 'POST',
  token: login.accessToken,
  body: importRequest
})
assert(applied.createdCourses === 1 && applied.createdAreas === 1 && applied.createdKnowledgePoints === 1,
  'fixture apply did not create the expected catalog projection')
assert(applied.createdExerciseUnits === 1 && applied.createdMappings === 1 && applied.conflictCount === 0,
  'fixture apply did not create the expected conflict-free catalog projection')

const courses = await request('/api/v1/courses', { token: login.accessToken })
const importedCourse = courses.find(course => course.courseCode === courseCode)
assert(importedCourse?.id, 'fixture apply did not expose the catalog course to the administrator')

const draftVersion = await request('/api/v1/admin/graph-versions', {
  method: 'POST',
  token: login.accessToken,
  body: {
    courseId: importedCourse.id,
    description: 'Controlled CI fixture draft; no candidate or published relations.',
    copyActive: false
  }
})
assert(draftVersion.id && draftVersion.courseId === importedCourse.id,
  'fixture setup did not create a draft graph version for governance verification')

console.log('Controlled catalog fixture import completed for the full-stack smoke test.')
