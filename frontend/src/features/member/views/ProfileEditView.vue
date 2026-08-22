<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { updateMemberProfile, type UpdateMemberProfilePayload } from '../api/memberApi'
import ProfileEditForm from '../components/ProfileEditForm.vue'
import { setMemberProfile, useMemberProfile } from '../model/memberQueries'

const i18n = useI18n()
const { t } = i18n
const router = useRouter()

const profileQuery = useMemberProfile()
const { data: profile, isPending, isError } = profileQuery

const save = useMutation({
  mutationFn: (payload: UpdateMemberProfilePayload) => updateMemberProfile(payload),
  onSuccess: (next) => {
    setMemberProfile(next)
    void router.push('/profile')
  },
})

/**
 * 실패 사유는 번역된 코드가 있을 때만 그대로 쓴다.
 *
 * 문구가 없는 코드는 key 문자열이 화면에 찍히므로 일반 안내로 되돌린다.
 */
const submitError = computed(() => {
  const error = save.error.value

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
  // 사진을 지웠을 때 빈 문자열을 보내면 `MEMBER-007`이다. 그 칸은 빼고 보낸다.
  const payload: UpdateMemberProfilePayload = {
    displayName: value.displayName,
    nationalityCode: value.nationalityCode,
  }

  if (value.profileImageUrl !== '') {
    payload.profileImageUrl = value.profileImageUrl
  }

  save.mutate(payload)
}
</script>

<template>
  <section class="px-screen pt-14 pb-8">
    <h1 class="font-display text-screen-title font-bold text-ink-display uppercase">
      {{ t('member.form.editTitle') }}
    </h1>

    <StateLoading v-if="isPending" />

    <StateError
      v-else-if="isError || profile === undefined"
      @retry="profileQuery.refetch()"
    />

    <ProfileEditForm
      v-else
      mode="edit"
      :display-name="profile.displayName"
      :profile-image-url="profile.profileImageUrl"
      :nationality-code="profile.nationalityCode"
      :submit-error="submitError"
      :submitting="save.isPending.value"
      @submit="submit"
      @cancel="router.back()"
    />
  </section>
</template>
