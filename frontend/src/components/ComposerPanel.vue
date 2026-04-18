<template>
  <section class="composer-panel">
    <div class="composer-toolbar">
      <label class="upload-trigger" :class="{ disabled: sending }">
        <input
          class="hidden-input"
          type="file"
          accept=".txt,.md,.markdown,.json,text/plain,application/json,text/markdown"
          :disabled="sending"
          @change="handleFileChange"
        />
        <span>{{ sending ? uiText.uploading : uiText.upload }}</span>
      </label>

      <span v-if="selectedFileName" class="selected-file">{{ selectedFileName }}</span>
      <span v-if="uploadStatus" class="selected-file upload-status">{{ uploadStatus }}</span>
    </div>

    <div class="composer-body">
      <textarea
        v-model.trim="localMessage"
        class="composer-input"
        rows="4"
        :disabled="sending"
        :placeholder="uiText.placeholder"
        @keydown.enter.exact.prevent="submit"
      />
      <button class="primary-button composer-send" type="button" :disabled="sending" @click="submit">
        {{ sending ? uiText.sending : uiText.send }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'

const uiText = {
  upload: '\u4e0a\u4f20\u77e5\u8bc6\u6587\u6863',
  uploading: '\u5904\u7406\u4e2d...',
  placeholder: '\u8f93\u5165\u6d88\u606f\u540e\u76f4\u63a5\u53d1\u9001\uff0c\u77e5\u8bc6\u6587\u6863\u4f1a\u5728\u9009\u4e2d\u6587\u4ef6\u540e\u7acb\u5373\u4e0a\u4f20\u3002',
  sending: '\u53d1\u9001\u4e2d...',
  send: '\u53d1\u9001'
}

const props = defineProps({
  message: {
    type: String,
    default: ''
  },
  selectedFileName: {
    type: String,
    default: ''
  },
  uploadStatus: {
    type: String,
    default: ''
  },
  sending: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send', 'file-change', 'update:message'])

const localMessage = ref(props.message)

watch(() => props.message, (value) => {
  localMessage.value = value
})

watch(localMessage, (value) => emit('update:message', value))

function handleFileChange(event) {
  const file = event.target.files?.[0] || null
  emit('file-change', file)
  event.target.value = ''
}

function submit() {
  emit('send')
}
</script>
