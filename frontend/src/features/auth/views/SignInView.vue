<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { buildAuthorizationUrl } from '../api/authApi'

const { t } = useI18n()
const route = useRoute()

function signInWith(provider: 'google' | 'line'): void {
  const returnPath = route.query.returnPath

  // 백엔드가 302로 provider 인증 페이지에 보내므로 전체 페이지를 이동시킨다.
  window.location.assign(
    buildAuthorizationUrl(provider, typeof returnPath === 'string' ? returnPath : undefined),
  )
}
</script>

<template>
  <section class="flex min-h-dvh flex-col justify-between px-screen py-12">
    <div class="mt-16">
      <h1 class="font-display text-screen-title uppercase">{{ t('app.name') }}</h1>
      <p class="mt-3 text-body text-ink-2">{{ t('app.tagline') }}</p>
    </div>

    <div class="flex flex-col gap-3">
      <p class="text-body-sm text-ink-3">{{ t('auth.signIn.description') }}</p>
      <button
        type="button"
        class="h-13 rounded-sm bg-ink text-title-sm text-on-color"
        @click="signInWith('google')"
      >
        {{ t('auth.signIn.google') }}
      </button>
      <button
        type="button"
        class="h-12 rounded-sm border border-hairline-strong text-title-sm text-ink"
        @click="signInWith('line')"
      >
        {{ t('auth.signIn.line') }}
      </button>
    </div>
  </section>
</template>
