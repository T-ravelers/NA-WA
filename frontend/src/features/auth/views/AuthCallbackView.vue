<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { AUTHENTICATED_HOME_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { clearAuthSession, ensureAuthSession } from '../model/authQueries'
import { consumeReturnPath } from '../model/returnPath'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()

/** 백엔드는 실패 시 `?error=AUTH-xxx`로 되돌려보낸다. */
const errorCode = computed(() => {
  const value = route.query.error

  return typeof value === 'string' ? value : null
})

const errorMessage = computed(() => {
  const code = errorCode.value

  if (code === null) {
    return null
  }

  const key = `auth.errorCode.${code}`

  return i18n.te(key) ? t(key) : t('error.unknown')
})

onMounted(async () => {
  if (errorCode.value !== null) {
    clearAuthSession()
    consumeReturnPath()
    return
  }

  // 콜백에서 쿠키가 새로 설정됐으므로 캐시된 세션을 버리고 다시 조회한다.
  clearAuthSession()

  const session = await ensureAuthSession()
  const returnPath = consumeReturnPath()

  if (session === null) {
    await router.replace(SIGN_IN_PATH)
    return
  }

  await router.replace(returnPath ?? AUTHENTICATED_HOME_PATH)
})
</script>

<template>
  <section class="flex min-h-dvh items-center justify-center px-screen">
    <StateError
      v-if="errorMessage !== null"
      :title="t('auth.callback.failed')"
      :description="errorMessage"
      :action-label="t('auth.signIn.google')"
      @retry="router.replace(SIGN_IN_PATH)"
    />
    <StateLoading
      v-else
      :label="t('auth.callback.pending')"
    />
  </section>
</template>
