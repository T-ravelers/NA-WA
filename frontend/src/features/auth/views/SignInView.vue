<script setup lang="ts">
import { IconLanguage } from '@tabler/icons-vue'
import { useMutation } from '@tanstack/vue-query'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { applyLocale } from '@/app/i18n/applyLocale'
import IconOrb from '@/shared/ui/IconOrb.vue'
import LocaleSheet from '@/shared/ui/LocaleSheet.vue'
import { resolveInitialLocale } from '@/shared/i18n/localePreference'
import type { AppLocale } from '@/shared/i18n/locales'
import { requestSignOut } from '@/shared/api/sessionSignOut'
import { isSignOutBarrierActive, subscribeSignOutBarrier } from '@/shared/api/signOutBarrier'

import { buildAuthorizationUrl } from '../api/authApi'
import { storeReturnPath } from '../model/returnPath'

const { t } = useI18n()
const route = useRoute()

const isLocaleSheetOpen = ref(false)
const selectedLocale = ref<AppLocale>(resolveInitialLocale())
const signOutBarrierActive = ref(isSignOutBarrierActive())
const signOutRetry = useMutation({ mutationFn: requestSignOut })

let unsubscribeSignOutBarrier: (() => void) | undefined

onMounted(() => {
  unsubscribeSignOutBarrier = subscribeSignOutBarrier((active) => {
    signOutBarrierActive.value = active
  })
})

onUnmounted(() => unsubscribeSignOutBarrier?.())

const NATIVE_LABEL: Record<AppLocale, string> = {
  en: 'English',
  ja: '日本語',
  'zh-TW': '繁體中文',
  vi: 'Tiếng Việt',
}

const currentLocaleLabel = computed(() => NATIVE_LABEL[selectedLocale.value])

/**
 * 로그인 이전이라 서버에 저장할 곳이 없다. 브라우저에만 남기고, 로그인 이후 서버 반영은
 * 세션이 생긴 뒤에 처리한다.
 */
function chooseLocale(next: AppLocale): void {
  selectedLocale.value = next
  applyLocale(next, { persist: true })
  isLocaleSheetOpen.value = false
}

function signInWith(provider: 'google' | 'line'): void {
  // 전체 페이지가 이동하므로 복귀 위치를 브라우저에 맡긴다. 백엔드에는 넘기지 않는다.
  storeReturnPath(route.query.returnPath)

  window.location.assign(buildAuthorizationUrl(provider))
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-14 pb-8">
    <div class="flex items-start justify-end">
      <IconOrb
        variant="surface"
        :label="t('auth.locale.open')"
        @click="isLocaleSheetOpen = true"
      >
        <IconLanguage
          :size="22"
          :stroke-width="1.75"
        />
      </IconOrb>
    </div>

    <div class="flex flex-1 flex-col justify-center gap-3">
      <h1 class="font-display text-screen-title font-bold text-ink-display">
        {{ t('auth.signIn.title') }}
      </h1>
      <p class="max-w-[313px] text-body text-ink-2">{{ t('auth.signIn.description') }}</p>
      <p class="text-caption text-ink-3">
        {{ t('auth.locale.current', { language: currentLocaleLabel }) }}
      </p>

      <div
        v-if="signOutBarrierActive"
        role="alert"
        class="mt-3 flex flex-col gap-3 rounded-sm border border-hairline-strong p-4"
      >
        <div class="flex flex-col gap-1">
          <p class="text-title-sm text-ink-display">{{ t('auth.signOutBarrier.title') }}</p>
          <p class="text-body-sm text-ink-2">{{ t('auth.signOutBarrier.description') }}</p>
        </div>
        <button
          type="button"
          class="h-11 rounded-sm bg-paper-fill text-title-sm text-on-paper disabled:opacity-50"
          :disabled="signOutRetry.isPending.value"
          @click="signOutRetry.mutate()"
        >
          {{ t('auth.signOutBarrier.retry') }}
        </button>
      </div>
    </div>

    <div class="flex flex-col gap-2.5">
      <button
        type="button"
        class="flex h-13 items-center justify-center gap-2 rounded-sm bg-paper-fill text-title-sm text-on-paper"
        :disabled="signOutRetry.isPending.value"
        @click="signInWith('google')"
      >
        <span
          aria-hidden="true"
          class="flex size-5 items-center justify-center rounded-pill bg-surface-1 text-micro text-ink-display"
        >
          G
        </span>
        {{ t('auth.signIn.google') }}
      </button>

      <button
        type="button"
        class="flex h-13 items-center justify-center gap-2 rounded-sm border border-hairline-strong text-title-sm text-ink-display"
        :disabled="signOutRetry.isPending.value"
        @click="signInWith('line')"
      >
        <span
          aria-hidden="true"
          class="flex size-5 items-center justify-center rounded-xs border-[1.5px] border-paper-fill text-micro"
        >
          L
        </span>
        {{ t('auth.signIn.line') }}
      </button>

      <p class="text-center text-micro font-normal text-ink-3">{{ t('auth.signIn.lineNotice') }}</p>

      <p class="mt-1.5 text-center text-micro leading-relaxed font-normal text-ink-2">
        {{ t('auth.signIn.consent') }}
        <a
          href="#"
          class="underline"
          >{{ t('auth.signIn.terms') }}</a
        >
        ·
        <a
          href="#"
          class="underline"
          >{{ t('auth.signIn.privacy') }}</a
        >
      </p>
    </div>

    <LocaleSheet
      v-if="isLocaleSheetOpen"
      :model-value="selectedLocale"
      :title="t('auth.locale.title')"
      :hint="t('auth.locale.hint')"
      @update:model-value="chooseLocale"
      @close="isLocaleSheetOpen = false"
    />
  </section>
</template>
