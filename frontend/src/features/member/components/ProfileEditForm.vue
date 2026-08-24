<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import { nationalityOptions } from '../model/nationalities'

/**
 * 프로필을 채우는 폼. 편집과 온보딩이 같은 폼을 쓴다.
 *
 * 두 화면의 차이는 **무엇을 요구하느냐**뿐이다. 온보딩은 서버가 부분 저장을 허용하지 않아
 * 이름·국적이 모두 있어야 하고(`MEMBER-008`), 편집은 바꾸고 싶은 것만 바꾼다. 그래서 모양이
 * 아니라 `mode` 하나로 가른다 — 폼을 두 벌 만들면 입력 규칙이 갈라진다.
 *
 * **사진은 여기서 바꾸지 않는다.** 소셜 로그인이 가입 시점에 Google·LINE의 사진을 넣어 주고
 * (`OAuthMemberTransactionImpl`), 직접 올리려면 첨부가 필요한데 S3 업로드 경로가 영수증
 * 전용이다(`ReceiptStorageService`의 `receipts/` 접두사, IAM 정책도 그 접두사로 좁혀져 있다).
 * 모바일에서 이미지 주소를 붙여넣는 칸은 소셜 기본값도 첨부도 아닌 중간물이라 두지 않는다.
 */
interface Props {
  mode: 'edit' | 'onboarding'
  displayName: string
  /** 소셜에서 온 사진. 보여 주기만 하고 이 폼은 바꾸지 않는다. */
  profileImageUrl: string | null
  nationalityCode: string | null
  /** 서버가 돌려준 실패 문구. 폼 자체 검사와 자리를 나눠 쓴다. */
  submitError?: string
  submitting?: boolean
}

const {
  mode,
  displayName,
  profileImageUrl,
  nationalityCode,
  submitError = undefined,
  submitting = false,
} = defineProps<Props>()

const emit = defineEmits<{
  submit: [value: { displayName: string; nationalityCode: string }]
  cancel: []
}>()

const { t, locale } = useI18n()

const name = ref(displayName)
const country = ref(nationalityCode ?? '')

// 프로필이 늦게 도착하면 폼이 이미 비어 있는 값으로 그려져 있다.
watch(
  () => [displayName, nationalityCode] as const,
  ([nextName, nextCountry]) => {
    name.value = nextName
    country.value = nextCountry ?? ''
  },
)

const countries = computed(() => nationalityOptions(locale.value))

/**
 * 오류를 언제 보여 줄지.
 *
 * 사용자가 그 칸을 만졌거나 한 번 보내려고 했을 때만 띄운다. 온보딩이라고 해서 먼저
 * 띄우지 않는다 — 서비스에서 처음 만나는 「Welcome」 화면이 아무것도 하지 않았는데
 * 빨간 글씨부터 보여 주게 된다.
 */
const touched = ref({ name: false, country: false })

/**
 * 이름 길이는 백엔드와 같은 code point로 센다.
 *
 * UTF-16 단위로 세면 이모지가 든 이름이 여기서는 통과하고 서버에서 `MEMBER-006`으로
 * 막힌다 — 백엔드도 같은 이유로 code point를 쓴다.
 */
const DISPLAY_NAME_MAX_LENGTH = 50

const nameError = computed(() => {
  const value = name.value.trim()

  if (value === '') {
    return touched.value.name ? t('member.form.error.nameRequired') : undefined
  }

  return [...value].length > DISPLAY_NAME_MAX_LENGTH
    ? t('member.form.error.nameTooLong', { max: DISPLAY_NAME_MAX_LENGTH })
    : undefined
})

const countryError = computed(() =>
  country.value === '' && touched.value.country
    ? t('member.form.error.countryRequired')
    : undefined,
)

/**
 * 보낼 수 있는 상태인가. **버튼을 잠그는 데 쓰지 않는다.**
 *
 * 비활성 버튼과 「만지기 전에는 조용히」를 같이 두면 무엇이 빠졌는지 말해 줄 계기가
 * 사라진다 — 사용자는 눌리지 않는 버튼만 보고 이유를 모른다. 그래서 버튼은 열어 두고,
 * 누르는 순간 빠진 칸을 짚어 준다.
 */
const canSubmit = computed(
  () => name.value.trim() !== '' && country.value !== '' && nameError.value === undefined,
)

function handleSubmit(): void {
  touched.value = { name: true, country: true }

  // 전송 중 방어는 여기에도 둔다. 버튼의 `:disabled` 하나에만 기대면, 나중에 그 속성을
  // 손대는 순간 이중 전송이 조용히 생긴다.
  if (submitting || !canSubmit.value) {
    return
  }

  emit('submit', { displayName: name.value.trim(), nationalityCode: country.value })
}
</script>

<template>
  <form
    class="mt-6 flex flex-col gap-4"
    novalidate
    @submit.prevent="handleSubmit"
  >
    <div class="flex items-center gap-3.5">
      <span class="size-14 shrink-0 overflow-hidden rounded-pill">
        <AppImage
          :src="profileImageUrl"
          alt=""
          class="size-full object-cover"
        />
      </span>
      <p class="text-body-sm text-ink-3">{{ t('member.form.photoHint') }}</p>
    </div>

    <TextInput
      v-model="name"
      :label="t('member.form.name')"
      :placeholder="t('member.form.namePlaceholder')"
      :error="nameError"
      @update:model-value="touched.name = true"
    />

    <div class="flex flex-col gap-1.5">
      <label
        for="profile-nationality"
        class="text-caption text-ink-2"
        >{{ t('member.form.nationality') }}</label
      >
      <select
        id="profile-nationality"
        v-model="country"
        data-testid="profile-nationality"
        class="h-13 w-full rounded-sm border-2 bg-surface-2 px-4 text-input text-ink outline-none"
        :class="
          countryError === undefined
            ? 'border-transparent focus-visible:border-ink'
            : 'border-danger'
        "
        :aria-invalid="countryError !== undefined"
        @change="touched.country = true"
      >
        <option
          value=""
          disabled
        >
          {{ t('member.form.nationalityPlaceholder') }}
        </option>
        <option
          v-for="option in countries"
          :key="option.code"
          :value="option.code"
        >
          {{ option.name }}
        </option>
      </select>
      <p
        v-if="countryError !== undefined"
        class="text-caption text-danger"
      >
        {{ countryError }}
      </p>
    </div>

    <p
      v-if="submitError !== undefined"
      role="alert"
      class="rounded-sm bg-surface-3 px-3.5 py-3 text-body-sm text-ink-2"
    >
      {{ submitError }}
    </p>

    <AppButton
      type="submit"
      block
      :disabled="submitting"
      :loading="submitting"
      >{{ mode === 'onboarding' ? t('member.form.start') : t('member.form.save') }}</AppButton
    >

    <AppButton
      v-if="mode === 'edit'"
      variant="tertiary"
      block
      @click="emit('cancel')"
      >{{ t('member.form.cancel') }}</AppButton
    >
  </form>
</template>
