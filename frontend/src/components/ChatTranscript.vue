<template>
  <section ref="transcriptRef" class="chat-transcript">
    <div v-if="!messages.length" class="empty-state">
      <p class="empty-title">新会话已就绪</p>
      <p class="empty-subtitle">消息会实时流式显示，右侧会同步展示链路状态和 token 用量。</p>
    </div>

    <article
      v-for="message in messages"
      :key="message.id"
      class="message-row"
      :class="message.role === 'user' ? 'message-row-user' : 'message-row-assistant'"
    >
      <div class="message-avatar">
        {{ message.role === 'user' ? '你' : 'AI' }}
      </div>
      <div class="message-card">
        <div class="message-header">
          <span class="message-role">{{ message.role === 'user' ? '用户' : '助手' }}</span>
          <span class="message-meta">{{ formatTime(message.createdAt) }}</span>
        </div>
        <pre class="message-content">{{ message.content || (message.pending ? '思考中...' : '') }}</pre>

        <div v-if="message.sources?.length" class="source-list">
          <p class="source-title">来源</p>
          <div v-for="source in message.sources" :key="`${message.id}-${source.chunkId}`" class="source-card">
            <p class="source-name">{{ source.chunkTitle || source.documentTitle || `分块 ${source.chunkIndex}` }}</p>
            <p class="source-meta">{{ source.sectionPath || source.jsonPath || source.documentTitle }}</p>
            <p class="source-meta">{{ source.retrievalType }} | 分数 {{ formatScore(source.score) }}</p>
          </div>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

const transcriptRef = ref(null)

watch(
  () => props.messages,
  async () => {
    await nextTick()
    if (!transcriptRef.value) {
      return
    }
    transcriptRef.value.scrollTop = transcriptRef.value.scrollHeight
  },
  { deep: true }
)

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

function formatScore(value) {
  if (typeof value !== 'number') {
    return '-'
  }
  return value.toFixed(4)
}
</script>
