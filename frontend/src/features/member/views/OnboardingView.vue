<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { requestSignOut } from '@/shared/api/sessionSignOut'
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

/**
 * 온보딩을 마치기 전에는 계정을 빠져나갈 곳이 없다.
 *
 * `/profile`은 게이트가 막고, `/sign-in`은 `guestOnly`라 인증된 사용자를 `/explore`로
 * 되돌린 뒤 다시 여기로 온다. 잘못된 계정으로 로그인했거나 마음을 바꾼 사용자를 가두지
 * 않도록 이 화면에도 출구를 둔다.
 */
const signOut = useMutation({ mutationFn: requestSignOut })

const submitError = computed(() => {
  const error = finish.error.value

  if (error === null) {
    return undefined
  }

  return error instanceof NormalizedApiError && i18n.te(error.messageKey)
    ? t(error.messageKey)
    : t('member.form.error.saveFailed')
})

function submit(value: { displayName: string; nationalityCode: string }): void {
  // 온보딩 요청에는 사진 칸이 없다. 사진은 소셜 로그인이 가입 시점에 넣어 준다.
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

    <!--
      프로필을 못 불러온 상태에서도 남는다. 로그아웃은 프로필이 필요 없는데 로딩 성공에
      묶어 두면, 가장 막막한 화면에서 출구만 사라진다.
    -->
    <button
      type="button"
      data-testid="onboarding-sign-out"
      class="mt-6 min-h-11 w-full text-body-sm text-ink-3 underline disabled:opacity-60"
      :disabled="signOut.isPending.value"
      @click="signOut.mutate()"
    >
      {{ t('auth.signOut') }}
    </button>
  </section>
</template>
