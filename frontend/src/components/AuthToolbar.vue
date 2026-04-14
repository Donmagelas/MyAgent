<template>
  <header class="auth-toolbar">
    <div class="brand-block">
      <div class="brand-mark">
        <img
          v-if="imageVisible"
          :src="brandImageSrc"
          :alt="uiText.brandTitle"
          class="brand-image"
          @error="imageVisible = false"
        />
        <span v-else>{{ uiText.brandFallback }}</span>
      </div>
      <div>
        <p class="brand-title">{{ uiText.brandTitle }}</p>
        <p class="brand-subtitle">{{ uiText.brandSubtitle }}</p>
      </div>
    </div>

    <div class="auth-block">
      <template v-if="authState.accessToken">
        <div class="auth-user">
          <span class="auth-name">{{ authState.username }}</span>
          <span class="auth-roles">{{ roleText }}</span>
        </div>
        <button class="secondary-button" type="button" @click="$emit('logout')">
          {{ uiText.logout }}
        </button>
      </template>

      <template v-else>
        <input
          v-model.trim="localUsername"
          class="toolbar-input"
          type="text"
          :placeholder="uiText.usernamePlaceholder"
          @keyup.enter="submitLogin"
        />
        <input
          v-model.trim="localPassword"
          class="toolbar-input"
          type="password"
          :placeholder="uiText.passwordPlaceholder"
          @keyup.enter="submitLogin"
        />
        <button class="primary-button" type="button" :disabled="loading" @click="submitLogin">
          {{ loading ? uiText.loggingIn : uiText.login }}
        </button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const brandImageSrc = '/brand-logo.jpg'
const uiText = {
  brandTitle: '\u0041\u0067\u0065\u006e\u0074 \u5e73\u53f0',
  brandSubtitle: '\u6211\u662f\u806a\u660e\u7684\u6d3e\u5927\u661f\uff0c\u4f60\u53ef\u4ee5\u548c\u6211\u804a\u5929\u5594',
  brandFallback: 'A',
  logout: '\u9000\u51fa\u767b\u5f55',
  usernamePlaceholder: '\u7528\u6237\u540d',
  passwordPlaceholder: '\u5bc6\u7801',
  loggingIn: '\u767b\u5f55\u4e2d...',
  login: '\u767b\u5f55'
}

const props = defineProps({
  authState: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['login', 'logout'])

const localUsername = ref(props.authState.username || '')
const localPassword = ref('')
const imageVisible = ref(true)

watch(
  () => props.authState.username,
  (value) => {
    if (value) {
      localUsername.value = value
    }
  }
)

const roleText = computed(() => (props.authState.roles || []).join(', '))

function submitLogin() {
  emit('login', {
    username: localUsername.value,
    password: localPassword.value
  })
}
</script>
