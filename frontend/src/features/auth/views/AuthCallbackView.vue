<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { AUTHENTICATED_HOME_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { queryClient } from '@/app/query/client'

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
    return
  }

  /*
   * 쿠키가 새로 설정됐으므로 인증 전에 캐시된 응답을 전부 버린다.
   *
   * 세션이 실제로 유효한지는 여기서 확인하지 않는다. 인증 정책은 라우터 guard 한 곳이
   * 소유하고, 화면 컴포넌트는 개별적으로 인증을 확인하지 않는다. 쿠키 설정이 실패했다면
   * 목적지의 guard가 로그인 화면으로 되돌리면서 복귀 경로를 query에 실어 준다.
   */
  queryClient.clear()

  await router.replace(consumeReturnPath() ?? AUTHENTICATED_HOME_PATH)
})
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-14 pb-8">
    <template v-if="errorMessage !== null">
      <div class="flex flex-1 flex-col justify-center gap-3">
        <h1 class="font-display text-screen-title font-bold text-ink-display">
          {{ t('auth.callback.failed') }}
        </h1>
        <p class="max-w-[313px] text-body text-ink-2">{{ errorMessage }}</p>
      </div>

      <!--
        실패 화면에서 로그인 수단을 다시 노출한다. 오류만 보여주고 되돌려보내면
        사용자가 어디로 가야 할지 알 수 없다. 복귀 경로는 query로 옮겨 유지한다.
      -->
      <button
        type="button"
        class="h-13 w-full rounded-sm bg-paper-fill text-title-sm text-on-paper"
        @click="router.replace(signInLocation)"
      >
        {{ t('auth.callback.retry') }}
      </button>
    </template>

    <div
      v-else
      class="flex flex-1 flex-col items-center justify-center gap-3 text-center"
    >
      <StateLoading :label="t('auth.callback.pending')" />
      <p class="max-w-[280px] text-body-sm text-ink-3">{{ t('auth.callback.pendingBody') }}</p>
    </div>
  </section>
</template>
