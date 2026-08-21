<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import type {
  AppointmentCreateRequest,
  AppointmentItemType,
  AppointmentLanguage,
} from '../api/appointmentApi'
import {
  MAX_APPOINTMENT_DEPOSIT,
  MAX_APPOINTMENT_MEMBERS,
  MIN_APPOINTMENT_DEPOSIT,
  MIN_APPOINTMENT_MEMBERS,
  toAppointmentCreateRequest,
  validateAppointmentBasics,
  validateAppointmentSettings,
  type AppointmentFormDraft,
  type AppointmentFormErrors,
} from '../model/appointmentForm'
import { useAppointmentItemLocation } from '../model/exploreIntegration'

interface Props {
  itemId?: number
  itemType?: AppointmentItemType
  /** 약속을 확정할 여정. 여정·날짜 선택 시트를 마친 뒤에만 이 폼이 렌더링된다. */
  tripId?: number
  visitDate?: string
  pending?: boolean
  errorMessage?: string
}

const {
  itemId = undefined,
  itemType = undefined,
  tripId = undefined,
  visitDate = '',
  pending = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{ submit: [request: AppointmentCreateRequest] }>()
const { t } = useI18n()

const step = ref<1 | 2>(1)
const errors = ref<AppointmentFormErrors>({})
const confirmationOpen = ref(false)
const draft = reactive<AppointmentFormDraft>({
  itemId,
  itemType,
  tripId,
  visitDate,
  appointmentName: '',
  maxMembers: 4,
  languageCode: 'en',
  depositAmount: null,
  meetingPlaceMode: 'ITEM',
  meetingPlace: '',
  activityStartTime: '',
  activityEndTime: '',
})

// "이 자리에서 그대로 만난다"를 고르면 항목 위치가 그대로 meetingPlace가 된다.
// 사용자가 같은 주소를 손으로 옮겨 적을 이유가 없다.
const itemLocationQuery = useAppointmentItemLocation(
  computed(() => draft.itemId ?? null),
  computed(() => draft.itemType ?? null),
)
const itemPlaceName = computed(() => itemLocationQuery.data.value?.placeName ?? null)

// 조회 중과 조회 실패는 둘 다 meetingPlace가 비어 있어 같은 오류로 보인다. 아직
// 읽는 중일 뿐인데 "못 읽었다"고 말하지 않도록 상태를 갈라 안내한다.
const waitingForItemPlace = computed(
  () => draft.meetingPlaceMode === 'ITEM' && itemLocationQuery.isLoading.value,
)
const itemPlaceFailed = computed(
  () => draft.meetingPlaceMode === 'ITEM' && itemLocationQuery.isError.value,
)

// 직접 적은 장소는 모드를 오갔다 돌아와도 남는다 — 잘못 눌러 지워지면 다시 적어야 한다.
const customMeetingPlace = ref('')

watch(
  [() => draft.meetingPlaceMode, itemPlaceName],
  ([mode, placeName]) => {
    draft.meetingPlace = mode === 'ITEM' ? (placeName ?? '') : customMeetingPlace.value
  },
  { immediate: true },
)

watch(
  () => draft.meetingPlace,
  (value) => {
    if (draft.meetingPlaceMode === 'CUSTOM') customMeetingPlace.value = value
  },
)

// tripId·itemId·itemType는 이 폼이 렌더링된 뒤 바뀌지 않지만, visitDate는 날짜
// 충돌로 제출이 실패했을 때 폼을 유지한 채 부모가 다시 고르게 할 수 있다 — 그
// 경우에만 draft에 반영되도록 prop 변화를 지켜본다.
watch(
  () => visitDate,
  (value) => {
    draft.visitDate = value
  },
)

// 같은 스텝 안의 필드들은 서로 맞물려 검증되는 경우가 있다(예: 종료가 시작보다
// 늦어야 함, 마감이 시작보다 빨라야 함). 제출 실패로 에러가 뜬 뒤 관련 필드
// 중 하나라도 고치기 시작하면, 다음 제출 전까지 오래된 에러 문구가 그대로
// 남아있지 않게 그 스텝의 에러를 지운다 — 다시 제출하면 해당 validate가 새로
// 채운다.
function clearErrorsOnEdit(
  source: () => unknown[],
  keys: readonly (keyof AppointmentFormErrors)[],
): void {
  watch(source, () => {
    if (keys.every((key) => errors.value[key] === undefined)) return

    const cleared = { ...errors.value }
    for (const key of keys) cleared[key] = undefined
    errors.value = cleared
  })
}

clearErrorsOnEdit(
  () => [draft.appointmentName, draft.maxMembers, draft.languageCode, draft.meetingPlace],
  ['appointmentName', 'maxMembers', 'languageCode', 'meetingPlace'],
)
clearErrorsOnEdit(
  () => [draft.activityStartTime, draft.activityEndTime, draft.depositAmount],
  ['activityStartTime', 'activityEndTime', 'depositAmount'],
)

const languageOptions: AppointmentLanguage[] = ['en', 'ja', 'zh-TW', 'vi']
const memberOptions = Array.from(
  { length: MAX_APPOINTMENT_MEMBERS - MIN_APPOINTMENT_MEMBERS + 1 },
  (_, index) => index + MIN_APPOINTMENT_MEMBERS,
)

function translatedError(key?: string): string | undefined {
  return key === undefined ? undefined : t(key)
}

function continueToSettings(): void {
  const nextErrors = validateAppointmentBasics(draft)
  errors.value = nextErrors

  if (Object.keys(nextErrors).length === 0) step.value = 2
}

function backToBasics(): void {
  step.value = 1
  errors.value = {}
}

function goToPreviousStep(): boolean {
  if (step.value === 2) {
    backToBasics()
    return true
  }
  return false
}

function submitSettings(): void {
  if (pending) return

  const nextErrors = validateAppointmentSettings(draft)
  errors.value = nextErrors

  if (Object.keys(nextErrors).length > 0) return

  confirmationOpen.value = true
}

function handleSubmit(): void {
  if (step.value === 1) continueToSettings()
  else submitSettings()
}

function cancelConfirmation(): void {
  confirmationOpen.value = false
}

function confirmCreation(): void {
  confirmationOpen.value = false
  emit('submit', toAppointmentCreateRequest(draft))
}

defineExpose({ goToPreviousStep })
</script>

<template>
  <form
    class="flex min-h-[calc(100dvh-8rem)] flex-col gap-8 pb-28"
    :aria-busy="pending"
    novalidate
    @submit.prevent="handleSubmit"
  >
    <ol
      class="flex items-center gap-2"
      :aria-label="t('appointment.create.stepLabel')"
    >
      <li
        class="flex items-center gap-2 text-micro uppercase tracking-[0.12em]"
        :class="step === 1 ? 'text-ink' : 'text-ink-3'"
      >
        <span
          class="flex size-6 items-center justify-center rounded-pill"
          :class="step === 1 ? 'bg-paper-fill text-on-paper' : 'bg-surface-2 text-ink-3'"
        >
          1
        </span>
        {{ t('appointment.create.basicsStep') }}
      </li>
      <li
        class="h-px flex-1 bg-hairline"
        aria-hidden="true"
      />
      <li
        class="flex items-center gap-2 text-micro uppercase tracking-[0.12em]"
        :class="step === 2 ? 'text-ink' : 'text-ink-3'"
      >
        <span
          class="flex size-6 items-center justify-center rounded-pill"
          :class="step === 2 ? 'bg-paper-fill text-on-paper' : 'bg-surface-2 text-ink-3'"
        >
          2
        </span>
        {{ t('appointment.create.settingsStep') }}
      </li>
    </ol>

    <p
      v-if="errorMessage !== undefined"
      class="text-body-sm text-danger"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <p
      v-if="errors.itemContext !== undefined"
      class="text-body-sm text-danger"
      role="alert"
    >
      {{ translatedError(errors.itemContext) }}
    </p>

    <fieldset
      class="flex flex-col gap-6"
      :disabled="pending"
    >
      <legend class="sr-only">
        {{
          step === 1
            ? t('appointment.create.basicsHeading')
            : t('appointment.create.settingsHeading')
        }}
      </legend>

      <template v-if="step === 1">
        <h2 class="font-display text-section-header text-ink-display">
          {{ t('appointment.create.basicsHeading') }}
        </h2>
        <TextInput
          v-model="draft.appointmentName"
          :label="t('appointment.create.name')"
          :placeholder="t('appointment.create.namePlaceholder')"
          :error="translatedError(errors.appointmentName)"
        />
        <div class="flex flex-col gap-2">
          <label
            for="appointment-max-members"
            class="text-caption text-ink-2"
          >
            {{ t('appointment.create.maxMembers') }}
          </label>
          <select
            id="appointment-max-members"
            v-model.number="draft.maxMembers"
            class="h-13 w-full rounded-sm border-2 border-transparent bg-surface-2 px-4 text-body text-ink outline-none focus-visible:border-ink"
            :aria-invalid="errors.maxMembers !== undefined"
          >
            <option
              v-for="memberCount in memberOptions"
              :key="memberCount"
              :value="memberCount"
            >
              {{ memberCount }}
            </option>
          </select>
          <p
            v-if="errors.maxMembers !== undefined"
            class="text-caption text-danger"
          >
            {{ translatedError(errors.maxMembers) }}
          </p>
        </div>
        <div class="flex flex-col gap-2">
          <label
            for="appointment-meeting-mode"
            class="text-caption text-ink-2"
          >
            {{ t('appointment.create.meetingPlace') }}
          </label>
          <select
            id="appointment-meeting-mode"
            v-model="draft.meetingPlaceMode"
            class="h-13 w-full rounded-sm border-2 border-transparent bg-surface-2 px-4 text-body text-ink outline-none focus-visible:border-ink"
          >
            <option value="ITEM">
              {{
                itemPlaceName === null
                  ? t('appointment.create.meetingAtItem')
                  : t('appointment.create.meetingAtItemNamed', { place: itemPlaceName })
              }}
            </option>
            <option value="CUSTOM">{{ t('appointment.create.meetingElsewhere') }}</option>
          </select>
          <TextInput
            v-if="draft.meetingPlaceMode === 'CUSTOM'"
            v-model="draft.meetingPlace"
            :label="t('appointment.create.meetingPlaceLabel')"
            :placeholder="t('appointment.create.meetingPlacePlaceholder')"
            :error="translatedError(errors.meetingPlace)"
          />
          <p
            v-else-if="errors.meetingPlace !== undefined"
            class="text-caption text-danger"
            role="alert"
          >
            {{ translatedError(errors.meetingPlace) }}
          </p>
          <p
            v-else-if="waitingForItemPlace"
            class="text-caption text-ink-3"
          >
            {{ t('appointment.create.meetingPlaceLoading') }}
          </p>
          <p
            v-else-if="itemPlaceFailed"
            class="text-caption text-danger"
            role="alert"
          >
            {{ t('appointment.create.validation.itemPlaceUnavailable') }}
          </p>
          <p
            v-else-if="itemLocationQuery.data.value?.addressRoad"
            class="text-caption text-ink-3"
          >
            {{ itemLocationQuery.data.value.addressRoad }}
          </p>
        </div>

        <fieldset class="flex flex-col gap-5">
          <legend class="text-caption text-ink-2">{{ t('appointment.create.language') }}</legend>
          <div class="flex flex-wrap gap-3 pt-4">
            <button
              v-for="language in languageOptions"
              :key="language"
              type="button"
              class="rounded-pill border px-4 py-2 text-caption"
              :class="
                draft.languageCode === language
                  ? 'border-paper-fill bg-paper-fill text-on-paper'
                  : 'border-hairline-strong text-ink-2'
              "
              :aria-pressed="draft.languageCode === language"
              @click="draft.languageCode = language"
            >
              {{ t(`appointment.languages.${language}`) }}
            </button>
          </div>
        </fieldset>
      </template>

      <template v-else>
        <h2 class="font-display text-section-header text-ink-display">
          {{ t('appointment.create.settingsHeading') }}
        </h2>
        <p class="text-body-sm text-ink-3">
          {{ t('appointment.create.visitDateNote', { date: draft.visitDate }) }}
        </p>
        <div class="grid gap-4">
          <TextInput
            v-model="draft.activityStartTime"
            type="time"
            :label="t('appointment.create.startAt')"
            :error="translatedError(errors.activityStartTime)"
          />
          <TextInput
            v-model="draft.activityEndTime"
            type="time"
            :label="t('appointment.create.endAt')"
            :error="translatedError(errors.activityEndTime)"
          />
        </div>
        <AmountInput
          v-model="draft.depositAmount"
          currency-symbol="P"
          symbol-position="suffix"
          :label="t('appointment.create.deposit')"
          :helper="
            t('appointment.create.depositHelper', {
              min: MIN_APPOINTMENT_DEPOSIT.toLocaleString('en-US'),
              max: MAX_APPOINTMENT_DEPOSIT.toLocaleString('en-US'),
            })
          "
          :error="translatedError(errors.depositAmount)"
        />
      </template>
    </fieldset>

    <div
      class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
    >
      <div class="grid grid-cols-2 gap-3">
        <AppButton
          v-if="step > 1"
          block
          type="button"
          variant="secondary"
          :disabled="pending"
          @click="backToBasics()"
        >
          {{ t('appointment.create.back') }}
        </AppButton>
        <AppButton
          block
          type="submit"
          :loading="pending"
          :disabled="step === 1 && waitingForItemPlace"
          :class="step === 1 ? 'col-span-2' : ''"
        >
          {{ step === 2 ? t('appointment.create.submit') : t('appointment.create.continue') }}
        </AppButton>
      </div>
    </div>
  </form>

  <div
    v-if="confirmationOpen"
    class="fixed inset-0 z-40 flex items-end justify-center bg-scrim/70 px-screen pb-6"
  >
    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('appointment.create.confirmTitle')"
      class="w-full max-w-[390px] rounded-card bg-paper p-5 text-on-paper shadow-sheet"
    >
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-title text-on-paper">{{ t('appointment.create.confirmTitle') }}</h2>
        <button
          type="button"
          class="text-title text-on-paper"
          :aria-label="t('appointment.create.close')"
          @click="cancelConfirmation"
        >
          ×
        </button>
      </div>
      <p class="mt-3 text-body-sm text-on-paper/70">
        {{ t('appointment.create.confirmDescription') }}
      </p>
      <div class="mt-4 rounded-sm bg-surface-1 p-4">
        <p class="text-caption text-settlement">{{ t('appointment.create.deposit') }}</p>
        <p class="mt-1 text-data-lg text-ink-display">
          {{
            t('appointment.points', {
              amount: new Intl.NumberFormat('en-US').format(draft.depositAmount ?? 0),
            })
          }}
        </p>
      </div>
      <div class="mt-5 grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary-on-paper"
          @click="cancelConfirmation"
        >
          {{ t('appointment.create.cancel') }}
        </AppButton>
        <AppButton
          block
          variant="settle"
          :loading="pending"
          @click="confirmCreation"
        >
          {{ t('appointment.create.confirm') }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
