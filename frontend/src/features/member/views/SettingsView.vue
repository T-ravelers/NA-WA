<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { IconChevronRight, IconLogout } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { applyLocale } from '@/app/i18n/applyLocale'
import { NormalizedApiError } from '@/shared/api/apiError'
import { requestSignOut } from '@/shared/api/sessionSignOut'
import type { AppLocale } from '@/shared/i18n/locales'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import LocaleSheet from '@/shared/ui/LocaleSheet.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { updateMemberProfile } from '../api/memberApi'
import { setMemberProfile, useMemberProfile } from '../model/memberQueries'

const i18n = useI18n()
const { t, locale } = i18n

const profileQuery = useMemberProfile()
const { data: profile, isPending, isError } = profileQuery

const isLocaleSheetOpen = ref(false)

/**
 * 화면에 표시하는 언어는 서버 값이 아니라 실제로 적용된 로케일이다.
 *
 * `members.preferred_language`는 `NOT NULL DEFAULT 'en'`이라 서버 값만으로는 "en을 골랐다"와
 * "고른 적 없다"가 구분되지 않는다. 저장 실패로 둘이 어긋났을 때 서버 값을 보여주면
 * 눈앞의 화면과 다른 언어를 가리키게 된다.
 */
const currentLocale = computed(() => locale.value as AppLocale)

const NATIVE_LABEL: Record<AppLocale, string> = {
  en: 'English',
  ja: '日本語',
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
  vi: 'Tiếng Việt',
}

const saveLanguage = useMutation({
  mutationFn: (next: AppLocale) => updateMemberProfile({ preferredLanguage: next }),
  onSuccess: setMemberProfile,
})

const signOut = useMutation({ mutationFn: requestSignOut })

/** 저장 실패의 구체적 사유. 번역된 코드가 있을 때만 덧붙인다. */
const saveFailureReason = computed(() => {
  const error = saveLanguage.error.value

  // 문구가 없는 코드는 key 문자열이 그대로 화면에 찍히므로 있을 때만 덧붙인다.
  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return null
  }

  return t(error.messageKey)
})

/**
 * 사용자가 직접 고른 선택이므로 저장 실패를 삼키지 않는다.
 *
 * 다만 화면 언어는 되돌리지 않는다. 이미 고른 언어로 그려진 화면을 되돌리면 방금 누른
 * 선택이 이유 없이 튕겨 보인다. 기기에는 남았고 계정에는 못 남았다는 사실을 알린다.
 */
function chooseLocale(next: AppLocale): void {
  isLocaleSheetOpen.value = false

  if (next === currentLocale.value) {
    return
  }

  applyLocale(next, { persist: true })
  saveLanguage.mutate(next)
}
</script>

<template>
  <section class="px-screen pt-14 pb-8">
    <h1 class="font-display text-screen-title font-bold text-ink-display">
      {{ t('member.settings.title') }}
    </h1>

    <StateLoading v-if="isPending" />

    <StateError
      v-else-if="isError || profile === undefined"
      @retry="profileQuery.refetch()"
    />

    <template v-else>
      <h2 class="mt-8 font-display text-section-header text-ink-display uppercase">
        {{ t('member.settings.account') }}
      </h2>

      <div class="mt-2 flex items-center gap-3 rounded-sm bg-surface-2 px-3.5 py-3">
        <span class="size-11 shrink-0 overflow-hidden rounded-pill">
          <img
            v-if="profile.profileImageUrl !== null"
            :src="profile.profileImageUrl"
            alt=""
            class="size-full object-cover"
          />
          <ImagePlaceholder v-else />
        </span>
        <span class="text-title-sm text-ink-display">{{ profile.displayName }}</span>
      </div>

      <button
        type="button"
        class="mt-2 flex min-h-14 w-full items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left disabled:opacity-60"
        :aria-label="t('auth.signOut')"
        :disabled="signOut.isPending.value"
        @click="signOut.mutate()"
      >
        <span class="flex-1 text-body text-ink">{{ t('auth.signOut') }}</span>
        <IconLogout
          :size="18"
          :stroke-width="1.75"
          class="text-icon-muted"
          aria-hidden="true"
        />
      </button>

      <div
        v-if="signOut.isError.value"
        role="alert"
        class="mt-2 flex flex-col items-start gap-2 rounded-sm bg-surface-3 px-3.5 py-3"
      >
        <p class="text-body-sm text-ink-2">{{ t('auth.signOutFailed') }}</p>
        <button
          type="button"
          class="text-caption text-ink-display underline"
          @click="signOut.mutate()"
        >
          {{ t('action.retry') }}
        </button>
      </div>

      <h2 class="mt-8 font-display text-section-header text-ink-display uppercase">
        {{ t('member.settings.preferences') }}
      </h2>

      <button
        type="button"
        class="mt-2 flex min-h-14 w-full items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left"
        :aria-label="t('member.settings.language.change')"
        @click="isLocaleSheetOpen = true"
      >
        <span class="flex-1 text-body text-ink">{{ t('member.settings.language.label') }}</span>
        <span class="text-body text-ink-2">{{ NATIVE_LABEL[currentLocale] }}</span>
        <IconChevronRight
          :size="18"
          :stroke-width="1.75"
          class="text-icon-muted"
          aria-hidden="true"
        />
      </button>

      <div
        v-if="saveLanguage.isError.value"
        role="alert"
        class="mt-2 flex flex-col gap-1 rounded-sm bg-surface-3 px-3.5 py-3"
      >
        <p class="text-body-sm text-ink-2">{{ t('member.settings.language.saveFailed') }}</p>
        <p
          v-if="saveFailureReason !== null"
          class="text-caption text-ink-3"
        >
          {{ saveFailureReason }}
        </p>
      </div>
    </template>

    <LocaleSheet
      v-if="isLocaleSheetOpen"
      :model-value="currentLocale"
      :title="t('member.settings.language.sheetTitle')"
      :hint="t('member.settings.language.hint')"
      @update:model-value="chooseLocale"
      @close="isLocaleSheetOpen = false"
    />
  </section>
</template>
