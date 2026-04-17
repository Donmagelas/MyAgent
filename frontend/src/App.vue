<template>
  <div class="app-shell">
    <AuthToolbar
      :auth-state="authState"
      :loading="authState.loggingIn"
      @login="handleLogin"
      @logout="logout"
    />

    <main class="workspace">
      <SessionSidebar
        :authenticated="Boolean(authState.accessToken)"
        :loading="conversationState.loading"
        :conversations="conversationState.items"
        :selected-conversation-id="conversationState.selectedConversationId"
        @new-session="createNewSession"
        @select="openConversation"
      />

      <section class="chat-panel">
        <div class="chat-panel-head">
          <div>
            <p class="panel-title">{{ uiText.panelTitle }}</p>
            <p class="panel-subtitle">{{ uiText.panelSubtitle }}</p>
          </div>
          <div class="session-chip">
            <span>{{ uiText.sessionLabel }}</span>
            <code>{{ sessionId }}</code>
          </div>
        </div>

        <ChatTranscript :messages="messages" />

        <ComposerPanel
          v-model:message="draftMessage"
          :selected-file-name="selectedFileName"
          :upload-status="streamState.documentStatus"
          :sending="streamState.streaming || streamState.documentUploading"
          @file-change="handleFileSelected"
          @send="sendMessage"
        />
      </section>

      <ExecutionPanel
        :session-id="sessionId"
        :stream-state="streamState"
        :selected-skill="selectedSkill"
        :route-events="routeEvents"
        :latest-usage="latestUsage"
        :workflow-view="workflowView"
      />
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AuthToolbar from './components/AuthToolbar.vue'
import ChatTranscript from './components/ChatTranscript.vue'
import ComposerPanel from './components/ComposerPanel.vue'
import ExecutionPanel from './components/ExecutionPanel.vue'
import SessionSidebar from './components/SessionSidebar.vue'
import { apiClient, buildAuthHeaders, extractHttpError, postSseStream } from './lib/api'

const STORAGE_KEY = 'agent-platform-frontend'

const uiText = {
  panelTitle: '\u5bf9\u8bdd\u7a97\u53e3',
  panelSubtitle: '\u9875\u9762\u52a0\u8f7d\u540e\u4f1a\u81ea\u52a8\u751f\u6210\u4f1a\u8bdd ID\uff0c\u76f4\u5230\u4f60\u624b\u52a8\u65b0\u5efa\u4f1a\u8bdd\u524d\u90fd\u4f1a\u6301\u7eed\u590d\u7528\u3002',
  sessionLabel: '\u4f1a\u8bdd',
  idle: '\u7a7a\u95f2',
  emptyCredential: '\u7528\u6237\u540d\u548c\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a',
  loginFirst: '\u8bf7\u5148\u767b\u5f55',
  emptyMessage: '\u8bf7\u8f93\u5165\u6d88\u606f\u5185\u5bb9',
  switchedSession: '\u5df2\u5207\u6362\u5230\u65b0\u4f1a\u8bdd',
  enteredHistory: '\u5df2\u8fdb\u5165\u5386\u53f2\u4f1a\u8bdd',
  uploadFailedMessage: '\u77e5\u8bc6\u6587\u6863\u4e0a\u4f20\u5931\u8d25\uff0c\u672c\u6b21\u6d88\u606f\u6ca1\u6709\u53d1\u9001\u3002',
  openingStream: '\u6b63\u5728\u5efa\u7acb\u6d41\u5f0f\u8fde\u63a5',
  streamFailed: '\u6d41\u5f0f\u8bf7\u6c42\u5931\u8d25',
  executionFailed: '\u6267\u884c\u5931\u8d25',
  executionDone: '\u6267\u884c\u5b8c\u6210',
  importingDocument: '\u6b63\u5728\u5bfc\u5165\u77e5\u8bc6\u6587\u6863\uff1a',
  documentImported: '\u77e5\u8bc6\u6587\u6863\u5bfc\u5165\u6210\u529f\u3002',
  documentImportFailed: '\u77e5\u8bc6\u6587\u6863\u5bfc\u5165\u5931\u8d25',
  enteredRoute: '\u5df2\u8fdb\u5165\u6267\u884c\u94fe\u8def\uff1a',
  loadedSources: '\u5df2\u52a0\u8f7d',
  sourceSuffix: '\u6761\u6765\u6e90',
  streaming: '\u6b63\u5728\u5b9e\u65f6\u8f93\u51fa',
  usageRecorded: '\u5df2\u8bb0\u5f55\u7528\u91cf\uff1a',
  skillSelected: '\u5df2\u9009\u62e9 skill\uff1a',
  streamDone: '\u6d41\u5f0f\u8f93\u51fa\u5b8c\u6210',
  serverError: '\u670d\u52a1\u7aef\u8fd4\u56de\u9519\u8bef',
  currentStep: '\u5f53\u524d\u6b65\u9aa4\uff1a',
  workflow: '\u5de5\u4f5c\u6d41',
  stepName: '\u6b65\u9aa4',
  tool: '\u5de5\u5177'
}

const draftMessage = ref('')
const selectedFileName = ref('')
const uploadedKnowledgeDocument = ref(null)
const sessionId = ref(generateSessionId())
const messages = ref([])
const workflowView = ref(null)
const routeEvents = ref([])
const pendingUsageByStep = new Map()
const activeAbortController = ref(null)
const workflowPollTimer = ref(null)
const hasSyncedConversationDuringStream = ref(false)
const latestUsage = reactive({
  requestId: '',
  modelName: '',
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  latencyMs: 0
})

const selectedSkill = reactive({
  skillId: '',
  skillName: '',
  skillDescription: '',
  routeStrategy: '',
  reason: '',
  toolChoiceMode: '',
  allowedTools: [],
  availableTools: []
})

const conversationState = reactive({
  loading: false,
  selectedConversationId: null,
  items: []
})

const authState = reactive({
  userId: null,
  username: '',
  accessToken: '',
  expiresAt: '',
  roles: [],
  loggingIn: false
})

const streamState = reactive({
  streaming: false,
  mode: '',
  statusText: uiText.idle,
  documentUploading: false,
  documentStatus: '',
  error: '',
  conversationId: null,
  workflowId: null
})

onMounted(() => {
  restoreFromLocalStorage()
  if (authState.accessToken) {
    refreshConversations().catch(() => {})
  }
})

onBeforeUnmount(() => {
  stopWorkflowPolling()
  activeAbortController.value?.abort()
})

async function handleLogin(payload) {
  if (!payload.username || !payload.password) {
    streamState.error = uiText.emptyCredential
    return
  }

  authState.loggingIn = true
  streamState.error = ''
  try {
    const response = await apiClient.post('/api/auth/login', payload)
    authState.userId = response.data.userId
    authState.username = response.data.username
    authState.accessToken = response.data.accessToken
    authState.expiresAt = response.data.expiresAt
    authState.roles = response.data.roles || []
    persistToLocalStorage()
    await refreshConversations()
  } catch (error) {
    streamState.error = await extractHttpError(error)
  } finally {
    authState.loggingIn = false
  }
}

function logout() {
  authState.userId = null
  authState.username = ''
  authState.accessToken = ''
  authState.expiresAt = ''
  authState.roles = []
  conversationState.loading = false
  conversationState.selectedConversationId = null
  conversationState.items = []
  stopWorkflowPolling()
  streamState.workflowId = null
  streamState.conversationId = null
  streamState.documentStatus = ''
  resetSelectedSkill()
  selectedFileName.value = ''
  uploadedKnowledgeDocument.value = null
  persistToLocalStorage()
}

async function handleFileSelected(file) {
  if (!file) {
    return
  }
  if (!authState.accessToken) {
    streamState.error = uiText.loginFirst
    return
  }
  if (streamState.streaming || streamState.documentUploading) {
    return
  }

  streamState.error = ''
  selectedFileName.value = file.name
  await uploadKnowledgeDocument(file)
}

function createNewSession() {
  stopWorkflowPolling()
  pendingUsageByStep.clear()
  sessionId.value = generateSessionId()
  messages.value = []
  conversationState.selectedConversationId = null
  routeEvents.value = []
  workflowView.value = null
  resetSelectedSkill()
  streamState.mode = ''
  streamState.statusText = uiText.switchedSession
  streamState.workflowId = null
  streamState.conversationId = null
  streamState.error = ''
  streamState.documentStatus = ''
  selectedFileName.value = ''
  uploadedKnowledgeDocument.value = null
  resetUsage()
  persistToLocalStorage()
}

async function openConversation(conversation) {
  if (!authState.accessToken || !conversation?.conversationId || streamState.streaming) {
    return
  }
  streamState.error = ''
  conversationState.loading = true
  try {
    const response = await apiClient.get(`/api/chat/conversations/${conversation.conversationId}`, {
      headers: buildAuthHeaders(authState.accessToken)
    })
    const detail = response.data
    conversationState.selectedConversationId = detail.conversationId
    sessionId.value = detail.sessionId
    messages.value = (detail.messages || []).map((message) => ({
      id: message.messageId,
      role: message.role,
      content: message.content,
      createdAt: message.createdAt,
      pending: false,
      sources: []
    }))
    selectedFileName.value = ''
    routeEvents.value = []
    workflowView.value = null
    resetSelectedSkill()
    streamState.workflowId = null
    streamState.conversationId = detail.conversationId
    streamState.mode = ''
    streamState.statusText = uiText.enteredHistory
    streamState.documentStatus = ''
    uploadedKnowledgeDocument.value = null
    resetUsage()
    persistToLocalStorage()
  } catch (error) {
    streamState.error = await extractHttpError(error)
  } finally {
    conversationState.loading = false
  }
}

async function sendMessage() {
  if (!authState.accessToken) {
    streamState.error = uiText.loginFirst
    return
  }
  if (!draftMessage.value.trim()) {
    streamState.error = uiText.emptyMessage
    return
  }
  if (streamState.streaming || streamState.documentUploading) {
    return
  }

  const userText = draftMessage.value.trim()
  const assistantMessage = reactive(createAssistantMessage())
  // 先做会话列表的乐观更新，避免必须等到流式结束后左侧才出现新会话。
  upsertConversationPreview({
    conversationId: streamState.conversationId,
    sessionId: sessionId.value,
    title: resolveConversationTitle(userText),
    lastMessagePreview: userText,
    lastActivityAt: new Date().toISOString()
  })
  conversationState.selectedConversationId = streamState.conversationId ?? sessionId.value
  messages.value.push(createUserMessage(userText))
  messages.value.push(assistantMessage)

  draftMessage.value = ''
  streamState.error = ''
  stopWorkflowPolling()
  pendingUsageByStep.clear()
  routeEvents.value = []
  workflowView.value = null
  resetSelectedSkill()
  streamState.workflowId = null
  streamState.conversationId = null
  streamState.mode = ''
  resetUsage()

  streamState.streaming = true
  streamState.statusText = uiText.openingStream
  hasSyncedConversationDuringStream.value = false
  activeAbortController.value = new AbortController()

  try {
    await postSseStream({
      url: '/api/chat/stream',
      token: authState.accessToken,
      signal: activeAbortController.value.signal,
      body: {
        sessionId: sessionId.value,
        message: userText,
        preferKnowledgeRetrieval: Boolean(uploadedKnowledgeDocument.value),
        knowledgeDocumentHint: uploadedKnowledgeDocument.value?.title || null
      },
      onEvent: ({ data }) => handleStreamEvent(data, assistantMessage)
    })
  } catch (error) {
    streamState.error = error instanceof Error ? error.message : uiText.streamFailed
    assistantMessage.pending = false
    if (!assistantMessage.content) {
      assistantMessage.content = streamState.error
    }
  } finally {
    streamState.streaming = false
    streamState.statusText = streamState.error ? uiText.executionFailed : uiText.executionDone
    activeAbortController.value = null
    persistToLocalStorage()
    await refreshConversations()
    if (streamState.workflowId) {
      await refreshWorkflowView()
    }
  }
}

async function uploadKnowledgeDocument(file) {
  streamState.documentUploading = true
  streamState.documentStatus = `${uiText.importingDocument}${file.name}`
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', file.name)
    formData.append('sourceUri', `web://upload/${sessionId.value}/${file.name}`)
    const response = await apiClient.post('/api/documents/import-file', formData, {
      headers: buildAuthHeaders(authState.accessToken)
    })
    uploadedKnowledgeDocument.value = {
      documentId: response.data.documentId,
      title: file.name
    }
    streamState.documentStatus = `${uiText.documentImported} documentId=${response.data.documentId}\uff0cchunkCount=${response.data.chunkCount}`
    return true
  } catch (error) {
    streamState.error = await extractHttpError(error)
    streamState.documentStatus = uiText.documentImportFailed
    uploadedKnowledgeDocument.value = null
    return false
  } finally {
    streamState.documentUploading = false
  }
}

function handleStreamEvent(event, assistantMessage) {
  streamState.mode = event.mode || streamState.mode
  streamState.conversationId = event.conversationId ?? streamState.conversationId
  if (event.conversationId) {
    conversationState.selectedConversationId = event.conversationId
    // 一旦拿到真实 conversationId，立即把乐观会话项升级成后端持久化会话。
    upsertConversationPreview({
      conversationId: event.conversationId,
      sessionId: sessionId.value,
      title: resolveConversationTitle(findLatestUserMessageContent()),
      lastActivityAt: new Date().toISOString()
    })
    if (!hasSyncedConversationDuringStream.value) {
      hasSyncedConversationDuringStream.value = true
      refreshConversations().catch(() => {})
    }
  }

  if (event.metadata?.workflowId) {
    streamState.workflowId = event.metadata.workflowId
    startWorkflowPolling()
  }

  switch (event.type) {
    case 'start':
      streamState.statusText = `${uiText.enteredRoute}${event.mode || 'chat'}`
      break
    case 'sources':
      assistantMessage.sources = event.sources || []
      streamState.statusText = `${uiText.loadedSources} ${assistantMessage.sources.length} ${uiText.sourceSuffix}`
      break
    case 'delta':
      assistantMessage.content += event.content || ''
      streamState.statusText = uiText.streaming
      return
    case 'usage':
      applyUsage(event.usage)
      streamState.statusText = `${uiText.usageRecorded}${event.metadata?.stepName || 'step'}`
      attachUsageToRouteEvent(event)
      return
    case 'skill':
      applySelectedSkill(event.metadata)
      streamState.statusText = `${uiText.skillSelected}${selectedSkill.skillName || event.metadata?.skillId || ''}`
      break
    case 'done':
      if (event.content != null) {
        assistantMessage.content = event.content
      }
      assistantMessage.pending = false
      applyUsage(event.usage)
      streamState.statusText = uiText.streamDone
      upsertConversationPreview({
        conversationId: streamState.conversationId,
        sessionId: sessionId.value,
        title: resolveConversationTitle(findLatestUserMessageContent()),
        lastMessagePreview: summarizePreview(event.content || assistantMessage.content),
        lastActivityAt: new Date().toISOString()
      })
      stopWorkflowPolling()
      break
    case 'error':
      assistantMessage.pending = false
      streamState.error = event.error || uiText.serverError
      streamState.statusText = uiText.executionFailed
      upsertConversationPreview({
        conversationId: streamState.conversationId,
        sessionId: sessionId.value,
        title: resolveConversationTitle(findLatestUserMessageContent()),
        lastActivityAt: new Date().toISOString()
      })
      stopWorkflowPolling()
      break
    default:
      streamState.statusText = `${uiText.currentStep}${event.type}`
      break
  }

  const routeEvent = {
    id: `${Date.now()}-${Math.random()}`,
    type: event.type,
    content: event.content || '',
    metadata: event.metadata || {},
    metaText: formatRouteMeta(event),
    usageText: '',
    at: new Date().toISOString()
  }
  attachBufferedUsage(routeEvent)
  routeEvents.value.unshift(routeEvent)
}

function applyUsage(usage) {
  if (!usage) {
    return
  }
  latestUsage.requestId = usage.requestId || latestUsage.requestId
  latestUsage.modelName = usage.modelName || latestUsage.modelName
  latestUsage.promptTokens = usage.promptTokens ?? latestUsage.promptTokens
  latestUsage.completionTokens = usage.completionTokens ?? latestUsage.completionTokens
  latestUsage.totalTokens = usage.totalTokens ?? latestUsage.totalTokens
  latestUsage.latencyMs = usage.latencyMs ?? latestUsage.latencyMs
}

function applySelectedSkill(metadata) {
  if (!metadata) {
    return
  }
  selectedSkill.skillId = metadata.skillId || ''
  selectedSkill.skillName = metadata.skillName || metadata.skillId || ''
  selectedSkill.skillDescription = metadata.skillDescription || ''
  selectedSkill.routeStrategy = metadata.routeStrategy || ''
  selectedSkill.reason = metadata.reason || ''
  selectedSkill.toolChoiceMode = metadata.toolChoiceMode || ''
  selectedSkill.allowedTools = Array.isArray(metadata.allowedTools) ? metadata.allowedTools : []
  selectedSkill.availableTools = Array.isArray(metadata.availableTools) ? metadata.availableTools : []
}

function startWorkflowPolling() {
  if (!streamState.workflowId || !authState.accessToken) {
    return
  }
  stopWorkflowPolling()
  workflowPollTimer.value = window.setInterval(() => {
    refreshWorkflowView().catch(() => {})
  }, 2000)
}

function stopWorkflowPolling() {
  if (workflowPollTimer.value) {
    clearInterval(workflowPollTimer.value)
    workflowPollTimer.value = null
  }
}

async function refreshWorkflowView() {
  if (!streamState.workflowId || !authState.accessToken) {
    return
  }
  const response = await apiClient.get(`/api/observability/workflows/${streamState.workflowId}`, {
    headers: buildAuthHeaders(authState.accessToken)
  })
  workflowView.value = response.data
}

function createUserMessage(content) {
  return {
    id: cryptoId(),
    role: 'user',
    content,
    createdAt: new Date().toISOString(),
    pending: false,
    sources: []
  }
}

function createAssistantMessage() {
  return {
    id: cryptoId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    pending: true,
    sources: []
  }
}

function upsertConversationPreview(conversation) {
  const items = [...conversationState.items]
  const targetIndex = items.findIndex((item) => matchesConversation(item, conversation))
  const nextConversation = normalizeConversationItem(
    targetIndex >= 0 ? items[targetIndex] : null,
    conversation
  )

  if (targetIndex >= 0) {
    items.splice(targetIndex, 1)
  }
  items.unshift(nextConversation)
  conversationState.items = items
}

function matchesConversation(left, right) {
  if (!left || !right) {
    return false
  }
  if (left.conversationId != null && right.conversationId != null) {
    return left.conversationId === right.conversationId
  }
  return Boolean(left.sessionId) && Boolean(right.sessionId) && left.sessionId === right.sessionId
}

function normalizeConversationItem(existing, incoming) {
  return {
    conversationId: incoming.conversationId ?? existing?.conversationId ?? null,
    sessionId: incoming.sessionId ?? existing?.sessionId ?? sessionId.value,
    title: incoming.title ?? existing?.title ?? '',
    lastMessagePreview: incoming.lastMessagePreview ?? existing?.lastMessagePreview ?? '',
    lastActivityAt: incoming.lastActivityAt ?? existing?.lastActivityAt ?? new Date().toISOString()
  }
}

function resolveConversationTitle(message) {
  const normalized = summarizePreview(message)
  return normalized || '\u65b0\u4f1a\u8bdd'
}

function summarizePreview(content) {
  const normalized = (content || '').replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return ''
  }
  return normalized.length > 36 ? `${normalized.slice(0, 36)}...` : normalized
}

function findLatestUserMessageContent() {
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (messages.value[index].role === 'user') {
      return messages.value[index].content || ''
    }
  }
  return ''
}

function formatRouteMeta(event) {
  const parts = []
  if (event.metadata?.workflowId) {
    parts.push(`${uiText.workflow}=${event.metadata.workflowId}`)
  }
  if (event.metadata?.skillName) {
    parts.push(`skill=${event.metadata.skillName}`)
  }
  if (event.metadata?.routeStrategy) {
    parts.push(`route=${event.metadata.routeStrategy}`)
  }
  if (event.metadata?.routeSource) {
    parts.push(`source=${event.metadata.routeSource}`)
  }
  if (event.metadata?.classifierDecision) {
    parts.push(`classifier=${event.metadata.classifierDecision}`)
  }
  if (event.metadata?.forceRag != null) {
    parts.push(`forceRag=${event.metadata.forceRag}`)
  }
  if (event.metadata?.probeExecuted) {
    parts.push(`probe=${event.metadata.probeHitCount || 0}/${event.metadata.probeTopScore ?? 0}`)
  }
  if (event.metadata?.retrievalQuery) {
    parts.push(`query=${event.metadata.retrievalQuery}`)
  }
  if (event.metadata?.stepName) {
    parts.push(`${uiText.stepName}=${event.metadata.stepName}`)
  }
  if (event.metadata?.toolName) {
    parts.push(`${uiText.tool}=${event.metadata.toolName}`)
  }
  return parts.join(' | ')
}

function attachUsageToRouteEvent(event) {
  const usageKey = event.metadata?.stepName
  if (!usageKey) {
    return
  }
  const usageText = formatUsageText(event.usage)
  if (!usageText) {
    return
  }

  for (const routeEvent of routeEvents.value) {
    const candidateKeys = resolveRouteEventUsageKeys(routeEvent)
    if (candidateKeys.includes(usageKey)) {
      applyUsageText(routeEvent, usageText)
      return
    }
  }

  pendingUsageByStep.set(usageKey, usageText)
}

function attachBufferedUsage(routeEvent) {
  const candidateKeys = resolveRouteEventUsageKeys(routeEvent)
  for (const usageKey of candidateKeys) {
    if (!pendingUsageByStep.has(usageKey)) {
      continue
    }
    applyUsageText(routeEvent, pendingUsageByStep.get(usageKey))
    pendingUsageByStep.delete(usageKey)
    return
  }
}

function resolveRouteEventUsageKeys(routeEvent) {
  const keys = []
  if (routeEvent.metadata?.stepName) {
    keys.push(routeEvent.metadata.stepName)
  }
  if (routeEvent.type === 'task-plan') {
    keys.push('agent-task-plan')
  }
  if (routeEvent.type === 'rag-route') {
    keys.push('agent-rag-route-classifier')
  }
  if (routeEvent.type === 'plan') {
    const step = routeEvent.metadata?.step
    if (step) {
      keys.push(`agent-loop-plan-${step}`)
    }
    keys.push('agent-cot-plan')
  }
  if (routeEvent.type === 'done') {
    keys.push('agent-loop-final', 'agent-loop-direct-return', 'agent-cot-final')
  }
  return keys
}

function applyUsageText(routeEvent, usageText) {
  routeEvent.usageText = usageText
  routeEvent.metaText = routeEvent.metaText ? `${routeEvent.metaText} | ${usageText}` : usageText
}

function formatUsageText(usage) {
  if (!usage) {
    return ''
  }
  const parts = []
  if (usage.totalTokens != null) {
    parts.push(`tokens=${usage.totalTokens}`)
  }
  if (usage.promptTokens != null) {
    parts.push(`prompt=${usage.promptTokens}`)
  }
  if (usage.completionTokens != null) {
    parts.push(`completion=${usage.completionTokens}`)
  }
  return parts.join(' | ')
}

function generateSessionId() {
  return `web-${cryptoId()}`
}

function cryptoId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function resetUsage() {
  latestUsage.requestId = ''
  latestUsage.modelName = ''
  latestUsage.promptTokens = 0
  latestUsage.completionTokens = 0
  latestUsage.totalTokens = 0
  latestUsage.latencyMs = 0
}

function resetSelectedSkill() {
  selectedSkill.skillId = ''
  selectedSkill.skillName = ''
  selectedSkill.skillDescription = ''
  selectedSkill.routeStrategy = ''
  selectedSkill.reason = ''
  selectedSkill.toolChoiceMode = ''
  selectedSkill.allowedTools = []
  selectedSkill.availableTools = []
}

async function refreshConversations() {
  if (!authState.accessToken) {
    conversationState.items = []
    conversationState.selectedConversationId = null
    return
  }
  conversationState.loading = true
  try {
    const response = await apiClient.get('/api/chat/conversations', {
      headers: buildAuthHeaders(authState.accessToken)
    })
    conversationState.items = response.data || []
    if (conversationState.selectedConversationId != null) {
      const exists = conversationState.items.some(
        (item) => resolveConversationKey(item) === conversationState.selectedConversationId
      )
      if (!exists) {
        conversationState.selectedConversationId = null
      }
    }
    if (streamState.conversationId != null) {
      const matched = conversationState.items.find((item) => item.conversationId === streamState.conversationId)
      if (matched) {
        conversationState.selectedConversationId = matched.conversationId
      }
    } else if (typeof conversationState.selectedConversationId === 'string') {
      const matched = conversationState.items.find((item) => item.sessionId === conversationState.selectedConversationId)
      if (matched) {
        conversationState.selectedConversationId = matched.conversationId ?? matched.sessionId
      }
    }
  } catch (error) {
    streamState.error = await extractHttpError(error)
  } finally {
    conversationState.loading = false
  }
}

function resolveConversationKey(conversation) {
  return conversation?.conversationId ?? conversation?.sessionId ?? null
}

function restoreFromLocalStorage() {
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    persistToLocalStorage()
    return
  }
  try {
    const saved = JSON.parse(raw)
    authState.userId = saved.auth?.userId ?? null
    authState.username = saved.auth?.username ?? ''
    authState.accessToken = saved.auth?.accessToken ?? ''
    authState.expiresAt = saved.auth?.expiresAt ?? ''
    authState.roles = saved.auth?.roles ?? []
    sessionId.value = saved.sessionId || generateSessionId()
  } catch {
    persistToLocalStorage()
  }
}

function persistToLocalStorage() {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      sessionId: sessionId.value,
      auth: {
        userId: authState.userId,
        username: authState.username,
        accessToken: authState.accessToken,
        expiresAt: authState.expiresAt,
        roles: authState.roles
      }
    })
  )
}
</script>
