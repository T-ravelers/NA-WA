<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { AUTHENTICATED_HOME_PATH } from '@/shared/config/routePaths'
import type { AppLocale } from '@/shared/i18n/locales'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { completeOnboarding, type CompleteOnboardingPayload } from '../api/memberApi'
import ProfileEditForm from '../components/ProfileEditForm.vue'
import { setMemberProfile, useMemberProfile } from '../model/memberQueries'

/**
 * 서비스가 쓰는 유일한 통화.
 *
 * 온보딩은 통화까지 있어야 끝나는데(`MEMBER-008`) 고를 것이 하나뿐이라 묻지 않는다.
 * `currencies` 테이블에 `KRW` 한 줄만 있고 통화 목록 API도 없다 — 2026-08-22 결정.
 */
const ONLY_CURRENCY = 'KRW'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()

const profileQuery = useMemberProfile()
const { data: profile, isPending, isError } = profileQuery

const finish = useMutation({
  mutationFn: (payload: CompleteOnboardingPayload) => completeOnboarding(payload),
  onSuccess: (next) => {
    setMemberProfile(next)
    void router.replace(AUTHENTICATED_HOME_PATH)
  },
})

const submitError = computed(() => {
  const error = finish.error.value

  if (error === null) {
    return undefined
  }

  return error instanceof NormalizedApiError && i18n.te(error.messageKey)
    ? t(error.messageKey)
    : t('member.form.error.saveFailed')
})

function submit(value: {
  displayName: string
  profileImageUrl: string
  nationalityCode: string
}): void {
  // 온보딩 요청에는 사진 칸이 없다. 사진은 마친 뒤 프로필 편집에서 붙인다.
  finish.mutate({
    displayName: value.displayName,
    nationalityCode: value.nationalityCode,
    preferredLanguage: locale.value as AppLocale,
    preferredCurrencyCode: ONLY_CURRENCY,
  })
}
</script>

<template>
  <section class="px-screen pt-14 pb-8">
    <h1 class="font-display text-screen-title font-bold text-ink-display uppercase">
      {{ t('member.form.onboardingTitle') }}
    </h1>
    <p class="mt-2 text-body text-ink-2">{{ t('member.form.onboardingLead') }}</p>

    <StateLoading v-if="isPending" />

    <StateError
      v-else-if="isError || profile === undefined"
      @retry="profileQuery.refetch()"
    />

    <ProfileEditForm
      v-else
      mode="onboarding"
      :display-name="profile.displayName"
      :profile-image-url="profile.profileImageUrl"
      :nationality-code="profile.nationalityCode"
      :submit-error="submitError"
      :submitting="finish.isPending.value"
      @submit="submit"
    />
  </section>
</template>
