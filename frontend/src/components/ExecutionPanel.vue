<template>
  <aside class="execution-panel">
    <section class="side-card">
      <h2 class="side-title">{{ TEXT.sessionInfo }}</h2>
      <div class="kv-list">
        <div class="kv-row">
          <span>{{ TEXT.session }}</span>
          <code>{{ sessionId }}</code>
        </div>
        <div class="kv-row">
          <span>{{ TEXT.status }}</span>
          <span :class="statusClass">{{ statusText }}</span>
        </div>
        <div class="kv-row" v-if="selectedSkill.skillName">
          <span>{{ TEXT.selectedSkill }}</span>
          <strong class="skill-name">{{ selectedSkill.skillName }}</strong>
        </div>
        <div class="kv-row" v-if="selectedSkill.routeStrategy">
          <span>{{ TEXT.routeStrategy }}</span>
          <span>{{ selectedSkill.routeStrategy }}</span>
        </div>
        <div class="kv-row" v-if="skillToolsText">
          <span>{{ TEXT.availableTools }}</span>
          <span class="tool-list-text">{{ skillToolsText }}</span>
        </div>
        <div class="kv-row" v-if="streamState.workflowId">
          <span>{{ TEXT.workflow }}</span>
          <code>{{ streamState.workflowId }}</code>
        </div>
        <div class="kv-row" v-if="workflowElapsedText">
          <span>{{ TEXT.workflowElapsed }}</span>
          <span>{{ workflowElapsedText }}</span>
        </div>
      </div>
      <p v-if="streamState.error" class="error-text">{{ streamState.error }}</p>
      <p v-if="streamState.documentStatus" class="hint-text">{{ streamState.documentStatus }}</p>
    </section>

    <section class="side-card">
      <h2 class="side-title">{{ TEXT.modelUsage }}</h2>
      <div class="model-usage-list">
        <article v-if="mainModelUsage" :key="mainModelUsage.modelName" class="model-usage-card">
          <div class="model-usage-head">
            <strong>{{ mainModelUsage.displayName }}</strong>
            <span>{{ mainModelUsage.callCount }} {{ TEXT.callUnit }}</span>
          </div>
          <div class="model-usage-stats model-usage-stats-triple">
            <div>
              <span>{{ TEXT.totalToken }}</span>
              <strong>{{ mainModelUsage.totalTokens }}</strong>
            </div>
            <div>
              <span>{{ TEXT.promptToken }}</span>
              <strong>{{ mainModelUsage.promptTokens }}</strong>
            </div>
            <div>
              <span>{{ TEXT.completionToken }}</span>
              <strong>{{ mainModelUsage.completionTokens }}</strong>
            </div>
          </div>
        </article>
        <div v-if="secondaryModelUsageCards.length" class="model-usage-compact-row">
          <article
            v-for="item in secondaryModelUsageCards"
            :key="item.modelName"
            class="model-usage-card model-usage-card-compact"
          >
            <div class="model-usage-head">
              <strong>{{ item.displayName }}</strong>
              <span>{{ item.callCount }} {{ TEXT.callUnit }}</span>
            </div>
            <div class="model-usage-stats model-usage-stats-single">
              <div>
                <span>{{ TEXT.totalToken }}</span>
                <strong>{{ item.totalTokens }}</strong>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="side-card">
      <h2 class="side-title">{{ TEXT.routeTimeline }}</h2>
      <div v-if="!routeEvents.length" class="empty-side">{{ TEXT.noRouteEvents }}</div>
      <ol v-else class="route-list">
        <li v-for="event in routeEvents" :key="event.id" class="route-item">
          <div class="route-head">
            <span class="route-type">{{ routeTypeText(event.type) }}</span>
            <span class="route-time">{{ formatTime(event.at) }}</span>
          </div>
          <p v-if="event.content" class="route-content">{{ event.content }}</p>
          <p v-if="event.metaText" class="route-meta">{{ event.metaText }}</p>
        </li>
      </ol>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

// 编码保护：该组件的展示文案统一使用 Unicode 转义，避免 PowerShell 或控制台编码污染。
const TEXT = {
  sessionInfo: '\u4f1a\u8bdd\u4fe1\u606f',
  session: '\u4f1a\u8bdd',
  status: '\u72b6\u6001',
  selectedSkill: '\u5f53\u524d Skill',
  routeStrategy: '\u8def\u7531\u65b9\u5f0f',
  availableTools: '\u66b4\u9732\u5de5\u5177',
  workflow: '\u5de5\u4f5c\u6d41',
  workflowElapsed: '\u672c\u6b21\u5bf9\u8bdd\u8017\u65f6',
  modelUsage: '\u6a21\u578b\u7528\u91cf',
  callUnit: '\u6b21',
  totalToken: '\u603b Token',
  promptToken: '\u8f93\u5165 Token',
  completionToken: '\u8f93\u51fa Token',
  routeTimeline: '\u94fe\u8def\u65f6\u95f4\u7ebf',
  noRouteEvents: '\u5f53\u524d\u8fd8\u6ca1\u6709\u94fe\u8def\u4e8b\u4ef6'
}

const MODEL_ORDER = ['qwen3.5-flash', 'qwen3-vl-embedding', 'qwen3-vl-rerank']
const MODEL_LABELS = {
  'qwen3.5-flash': '\u4e3b\u5bf9\u8bdd\u6a21\u578b',
  'qwen3-vl-embedding': '\u5411\u91cf\u6a21\u578b',
  'qwen3-vl-rerank': '\u91cd\u6392\u6a21\u578b'
}
const ROUTE_LABELS = {
  start: '\u5f00\u59cb',
  skill: 'Skill \u8def\u7531',
  'rag-route': 'RAG \u8def\u7531',
  info: '\u4fe1\u606f',
  'task-plan': '\u4efb\u52a1\u89c4\u5212',
  plan: '\u6b65\u9aa4\u51b3\u7b56',
  observation: '\u89c2\u5bdf',
  rag: 'RAG',
  'evidence-gate': '\u8bc1\u636e\u62e6\u622a',
  judge: '\u56de\u7b54\u6821\u9a8c',
  sources: '\u68c0\u7d22\u6765\u6e90',
  done: '\u5b8c\u6210',
  error: '\u9519\u8bef'
}

const props = defineProps({
  sessionId: {
    type: String,
    required: true
  },
  streamState: {
    type: Object,
    required: true
  },
  selectedSkill: {
    type: Object,
    default: () => ({})
  },
  routeEvents: {
    type: Array,
    default: () => []
  },
  latestUsage: {
    type: Object,
    default: () => ({})
  },
  workflowView: {
    type: Object,
    default: null
  }
})

const statusClass = computed(() => {
  if (props.streamState.error) {
    return 'status-error'
  }
  if (props.streamState.streaming) {
    return 'status-running'
  }
  return 'status-idle'
})

const statusText = computed(() => {
  if (props.streamState.error) {
    return '\u6267\u884c\u5931\u8d25'
  }
  if (props.streamState.streaming) {
    return '\u6267\u884c\u4e2d'
  }
  return props.streamState.statusText || '\u7a7a\u95f2'
})

const workflowElapsedText = computed(() => {
  const startedAt = props.workflowView?.startedAt || props.workflowView?.createdAt
  const completedAt = props.workflowView?.completedAt
  if (!startedAt || !completedAt) {
    return ''
  }
  const elapsedMs = Math.max(new Date(completedAt).getTime() - new Date(startedAt).getTime(), 0)
  return formatLatency(elapsedMs)
})

const skillToolsText = computed(() => {
  const tools = props.selectedSkill?.availableTools?.length
    ? props.selectedSkill.availableTools
    : props.selectedSkill?.allowedTools
  if (!tools?.length) {
    return ''
  }
  return tools.join(', ')
})

const modelUsageCards = computed(() => {
  const byStep = props.workflowView?.usage?.byStep || []
  const grouped = new Map(
    MODEL_ORDER.map((modelName) => [
      modelName,
      {
        modelName,
        displayName: MODEL_LABELS[modelName] || modelName,
        callCount: 0,
        promptTokens: 0,
        completionTokens: 0,
        totalTokens: 0
      }
    ])
  )

  for (const item of byStep) {
    const modelName = item.modelName || 'unknown-model'
    const current = grouped.get(modelName) || {
      modelName,
      displayName: MODEL_LABELS[modelName] || modelName,
      callCount: 0,
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0
    }
    current.callCount += item.callCount || 0
    current.promptTokens += item.promptTokens || 0
    current.completionTokens += item.completionTokens || 0
    current.totalTokens += item.totalTokens || 0
    grouped.set(modelName, current)
  }

  const cards = Array.from(grouped.values())
  cards.sort((left, right) => {
    const leftIndex = MODEL_ORDER.indexOf(left.modelName)
    const rightIndex = MODEL_ORDER.indexOf(right.modelName)
    if (leftIndex === -1 && rightIndex === -1) {
      return left.modelName.localeCompare(right.modelName)
    }
    if (leftIndex === -1) {
      return 1
    }
    if (rightIndex === -1) {
      return -1
    }
    return leftIndex - rightIndex
  })
  return cards
})

// 主对话模型保留完整输入/输出/总量展示。
const mainModelUsage = computed(() => {
  return modelUsageCards.value.find((item) => item.modelName === 'qwen3.5-flash') || modelUsageCards.value[0] || null
})

// 向量模型和重排模型只展示总 Token，并压缩到一行。
const secondaryModelUsageCards = computed(() => {
  return modelUsageCards.value.filter((item) => item.modelName !== (mainModelUsage.value?.modelName || ''))
})

function routeTypeText(type) {
  return ROUTE_LABELS[type] || type
}

function formatTime(value) {
  if (!value) {
    return ''
  }
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function formatLatency(value) {
  if (!value) {
    return '0 ms'
  }
  return `${value} ms`
}
</script>
