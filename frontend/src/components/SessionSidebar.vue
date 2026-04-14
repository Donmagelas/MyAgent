<template>
  <aside class="session-sidebar">
    <div class="session-sidebar-head">
      <div>
        <p class="side-title">{{ uiText.title }}</p>
        <p class="sidebar-subtitle">{{ uiText.subtitle }}</p>
      </div>
      <button class="secondary-button" type="button" @click="$emit('new-session')">
        {{ uiText.newSession }}
      </button>
    </div>

    <div v-if="!authenticated" class="empty-side">
      {{ uiText.loginHint }}
    </div>
    <div v-else-if="loading" class="empty-side">
      {{ uiText.loading }}
    </div>
    <div v-else-if="!conversations.length" class="empty-side">
      {{ uiText.empty }}
    </div>
    <div v-else class="session-list">
      <button
        v-for="conversation in conversations"
        :key="resolveConversationKey(conversation)"
        type="button"
        class="session-item"
        :class="{ 'session-item-active': resolveConversationKey(conversation) === selectedConversationId }"
        @click="$emit('select', conversation)"
      >
        <div class="session-item-head">
          <strong>{{ conversation.title || uiText.untitled }}</strong>
          <span>{{ formatTime(conversation.lastActivityAt) }}</span>
        </div>
        <p class="session-item-preview">{{ buildPreview(conversation) }}</p>
      </button>
    </div>
  </aside>
</template>

<script setup>
const uiText = {
  title: '\u4f1a\u8bdd\u8bb0\u5f55',
  subtitle: '\u4f1a\u8bdd\u4e0e\u5f53\u524d\u767b\u5f55\u7528\u6237\u7ed1\u5b9a\uff0c\u70b9\u51fb\u540e\u53ef\u76f4\u63a5\u8fdb\u5165\u5386\u53f2\u4f1a\u8bdd\u3002',
  newSession: '\u65b0\u5efa',
  loginHint: '\u767b\u5f55\u540e\u53ef\u67e5\u770b\u4f60\u7684\u5386\u53f2\u4f1a\u8bdd\u3002',
  loading: '\u6b63\u5728\u52a0\u8f7d\u4f1a\u8bdd\u5217\u8868...',
  empty: '\u5f53\u524d\u8fd8\u6ca1\u6709\u5386\u53f2\u4f1a\u8bdd\uff0c\u53d1\u9001\u7b2c\u4e00\u6761\u6d88\u606f\u540e\u4f1a\u81ea\u52a8\u51fa\u73b0\u5728\u8fd9\u91cc\u3002',
  untitled: '\u672a\u547d\u540d\u4f1a\u8bdd',
  emptyPreview: '\u8fd8\u6ca1\u6709\u6d88\u606f\u5185\u5bb9'
}

defineProps({
  authenticated: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  conversations: {
    type: Array,
    default: () => []
  },
  selectedConversationId: {
    type: [Number, String],
    default: null
  }
})

defineEmits(['select', 'new-session'])

function formatTime(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function buildPreview(conversation) {
  if (!conversation.lastMessagePreview) {
    return uiText.emptyPreview
  }
  return conversation.lastMessagePreview
}

function resolveConversationKey(conversation) {
  return conversation.conversationId ?? conversation.sessionId
}
</script>
