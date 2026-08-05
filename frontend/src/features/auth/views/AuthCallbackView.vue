<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { AUTHENTICATED_HOME_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { clearAuthSession, ensureAuthSession } from '../model/authQueries'
import { consumeReturnPath, peekReturnPath } from '../model/returnPath'

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

/**
 * 로그인을 다시 시도할 위치.
 *
 * 저장된 복귀 경로를 소비하지 않고 query로 넘긴다. `SignInView`가 이 query를 다시
 * 저장하므로 저장소의 기록자는 여전히 하나다. 여기서 소비해 버리면 로그인에 실패한
 * 사용자가 재시도한 뒤 원래 가려던 화면으로 돌아가지 못한다.
 */
const signInLocation = computed<RouteLocationRaw>(() => {
  const returnPath = peekReturnPath()

  return returnPath === null
    ? { path: SIGN_IN_PATH }
    : { path: SIGN_IN_PATH, query: { returnPath } }
})

onMounted(async () => {
  if (errorCode.value !== null) {
    clearAuthSession()
    return
  }

  // 콜백에서 쿠키가 새로 설정됐으므로 캐시된 세션을 버리고 다시 조회한다.
  clearAuthSession()

  const session = await ensureAuthSession()

  if (session === null) {
    // 복귀 경로는 소비하지 않는다. 재시도 후에도 원래 목적지로 돌아가야 한다.
    await router.replace(signInLocation.value)
    return
  }

  await router.replace(consumeReturnPath() ?? AUTHENTICATED_HOME_PATH)
})
</script>

<template>
  <section class="flex min-h-dvh items-center justify-center px-screen">
    <StateError
      v-if="errorMessage !== null"
      :title="t('auth.callback.failed')"
      :description="errorMessage"
      :action-label="t('auth.signIn.google')"
      @retry="router.replace(signInLocation)"
    />
    <StateLoading
      v-else
      :label="t('auth.callback.pending')"
    />
  </section>
</template>
