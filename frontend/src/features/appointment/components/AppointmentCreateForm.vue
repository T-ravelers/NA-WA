<script setup lang="ts">
import { reactive, ref } from 'vue'
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
  validateAppointmentForm,
  type AppointmentFormDraft,
  type AppointmentFormErrors,
} from '../model/appointmentForm'

interface Props {
  itemId?: number
  itemType?: AppointmentItemType
  pending?: boolean
  errorMessage?: string
}

const {
  itemId = undefined,
  itemType = undefined,
  pending = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{ submit: [request: AppointmentCreateRequest] }>()
const { t } = useI18n()

const errors = ref<AppointmentFormErrors>({})
const confirmationOpen = ref(false)
const draft = reactive<AppointmentFormDraft>({
  itemId,
  itemType,
  appointmentName: '',
  maxMembers: 4,
  languageCode: 'en',
  depositAmount: null,
  meetingPlace: '',
  meetingAddress: '',
  activityStartAt: '',
  activityEndAt: '',
  joinDeadline: '',
})

const languageOptions: AppointmentLanguage[] = ['en', 'ja', 'zh-CN', 'zh-TW', 'vi']
const memberOptions = Array.from(
  { length: MAX_APPOINTMENT_MEMBERS - MIN_APPOINTMENT_MEMBERS + 1 },
  (_, index) => index + MIN_APPOINTMENT_MEMBERS,
)

function translatedError(key?: string): string | undefined {
  return key === undefined ? undefined : t(key)
}

function submit(): void {
  if (pending) return

  const nextErrors = validateAppointmentForm(draft)
  errors.value = nextErrors

  if (Object.keys(nextErrors).length > 0) return

  confirmationOpen.value = true
}

function cancelConfirmation(): void {
  confirmationOpen.value = false
}

function confirmCreation(): void {
  confirmationOpen.value = false
  emit('submit', toAppointmentCreateRequest(draft))
}
</script>

<template>
  <form
    class="flex flex-col gap-6"
    :aria-busy="pending"
    novalidate
    @submit.prevent="submit"
  >
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
      class="flex flex-col gap-5"
      :disabled="pending"
    >
      <legend class="sr-only">{{ t('appointment.create.title') }}</legend>

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

      <fieldset class="flex flex-col gap-2">
        <legend class="text-caption text-ink-2">{{ t('appointment.create.language') }}</legend>
        <div class="flex flex-wrap gap-2">
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

      <AmountInput
        v-model="draft.depositAmount"
        :label="t('appointment.create.deposit')"
        :helper="
          t('appointment.create.depositHelper', {
            min: MIN_APPOINTMENT_DEPOSIT.toLocaleString('en-US'),
            max: MAX_APPOINTMENT_DEPOSIT.toLocaleString('en-US'),
          })
        "
        :error="translatedError(errors.depositAmount)"
      />

      <TextInput
        v-model="draft.meetingPlace"
        :label="t('appointment.create.meetingPlace')"
        :placeholder="t('appointment.create.meetingPlacePlaceholder')"
        :error="translatedError(errors.meetingPlace)"
      />

      <TextInput
        v-model="draft.meetingAddress"
        :label="t('appointment.create.meetingAddress')"
        :placeholder="t('appointment.create.meetingAddressPlaceholder')"
      />

      <div class="grid gap-4">
        <TextInput
          v-model="draft.activityStartAt"
          type="datetime-local"
          :label="t('appointment.create.startAt')"
          :error="translatedError(errors.activityStartAt)"
        />
        <TextInput
          v-model="draft.activityEndAt"
          type="datetime-local"
          :label="t('appointment.create.endAt')"
          :error="translatedError(errors.activityEndAt)"
        />
        <TextInput
          v-model="draft.joinDeadline"
          type="datetime-local"
          :label="t('appointment.create.joinDeadline')"
          :error="translatedError(errors.joinDeadline)"
        />
      </div>
    </fieldset>

    <AppButton
      block
      type="submit"
      :loading="pending"
    >
      {{ t('appointment.create.submit') }}
    </AppButton>
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
          :aria-label="t('action.close')"
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
          ₩{{ new Intl.NumberFormat('en-US').format(draft.depositAmount ?? 0) }}
        </p>
      </div>
      <div class="mt-5 grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary"
          @click="cancelConfirmation"
        >
          {{ t('action.cancel') }}
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
