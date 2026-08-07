<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import type { CompanionPreference, JourneyCreateInput } from '../api/journeyApi'
import {
  hasStepOneErrors,
  toJourneyCreateInput,
  validateJourneyForm,
  type JourneyFormDraft,
  type JourneyFormErrors,
} from '../model/journeyForm'

interface Props {
  pending?: boolean
  errorMessage?: string
}

const { pending = false, errorMessage = undefined } = defineProps<Props>()

const emit = defineEmits<{ submit: [input: JourneyCreateInput] }>()

const { t } = useI18n()

const step = ref<1 | 2>(1)
const errors = ref<JourneyFormErrors>({})
const draft = reactive<JourneyFormDraft>({
  title: '',
  startDate: '',
  endDate: '',
  budgetAmount: null,
  companionPreference: null,
})

const companionOptions: Array<{
  value: CompanionPreference
  labelKey: string
  descriptionKey: string
}> = [
  {
    value: '1',
    labelKey: 'journey.create.companionOne',
    descriptionKey: 'journey.create.companionOneDescription',
  },
  {
    value: '2-4',
    labelKey: 'journey.create.companionSmall',
    descriptionKey: 'journey.create.companionSmallDescription',
  },
  {
    value: '5+',
    labelKey: 'journey.create.companionLarge',
    descriptionKey: 'journey.create.companionLargeDescription',
  },
]

function translatedError(key?: string): string | undefined {
  return key === undefined ? undefined : t(key)
}

function goToPreferences(): void {
  const nextErrors = validateJourneyForm(draft)
  errors.value = nextErrors

  if (!hasStepOneErrors(nextErrors)) {
    step.value = 2
  }
}

function goBack(): void {
  step.value = 1
}

function toggleCompanion(value: CompanionPreference): void {
  draft.companionPreference = draft.companionPreference === value ? null : value
}

function submit(): void {
  if (pending) {
    return
  }

  const nextErrors = validateJourneyForm(draft)
  errors.value = nextErrors

  if (Object.keys(nextErrors).length > 0) {
    if (hasStepOneErrors(nextErrors)) {
      step.value = 1
    }
    return
  }

  emit('submit', toJourneyCreateInput(draft))
}
</script>

<template>
  <form
    class="flex flex-col gap-6"
    :aria-busy="pending"
    novalidate
    @submit.prevent="submit"
  >
    <ol
      class="flex items-center gap-3 text-caption text-ink-3"
      :aria-label="t('journey.create.stepLabel')"
    >
      <li :aria-current="step === 1 ? 'step' : undefined">
        {{ t('journey.create.stepOne') }}
      </li>
      <li aria-hidden="true">/</li>
      <li :aria-current="step === 2 ? 'step' : undefined">
        {{ t('journey.create.stepTwo') }}
      </li>
    </ol>

    <p
      class="text-caption text-ink-3"
      aria-live="polite"
    >
      {{ t('journey.create.stepCount', { current: step }) }}
    </p>

    <p
      v-if="errorMessage !== undefined"
      class="text-body-sm text-danger"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <fieldset
      class="contents"
      :disabled="pending"
    >
      <legend class="sr-only">{{ t('journey.create.title') }}</legend>

      <template v-if="step === 1">
        <TextInput
          v-model="draft.title"
          :label="t('journey.create.name')"
          :error="translatedError(errors.title)"
        />

        <div class="grid grid-cols-2 gap-3">
          <div class="min-w-0">
            <TextInput
              v-model="draft.startDate"
              type="date"
              :label="t('journey.create.startDate')"
              :error="translatedError(errors.startDate)"
            />
          </div>

          <div class="min-w-0">
            <TextInput
              v-model="draft.endDate"
              type="date"
              :min="draft.startDate || undefined"
              :label="t('journey.create.endDate')"
              :error="translatedError(errors.endDate)"
            />
          </div>
        </div>

        <AppButton
          block
          @click="goToPreferences"
        >
          {{ t('journey.create.next') }}
        </AppButton>
      </template>

      <template v-else>
        <AmountInput
          v-model="draft.budgetAmount"
          :label="t('journey.create.budget')"
          :helper="t('journey.create.budgetHelper')"
          :error="translatedError(errors.budgetAmount)"
        />

        <fieldset class="flex flex-col gap-3">
          <legend class="text-title-sm text-ink">
            {{ t('journey.create.companions') }}
            <span class="text-caption text-ink-3">
              · {{ t('journey.create.companionsOptional') }}
            </span>
          </legend>

          <button
            v-for="option in companionOptions"
            :key="option.value"
            type="button"
            class="flex min-h-16 items-center justify-between rounded-sm border px-4 text-left"
            :class="
              draft.companionPreference === option.value
                ? 'border-ink bg-surface-2'
                : 'border-hairline-strong bg-surface-1'
            "
            :aria-pressed="draft.companionPreference === option.value"
            @click="toggleCompanion(option.value)"
          >
            <span class="flex flex-col gap-1">
              <span class="text-title-sm text-ink">{{ t(option.labelKey) }}</span>
              <span class="text-body-sm text-ink-3">{{ t(option.descriptionKey) }}</span>
            </span>
            <span aria-hidden="true">
              {{ draft.companionPreference === option.value ? '✓' : '' }}
            </span>
          </button>
        </fieldset>

        <div class="grid grid-cols-2 gap-3">
          <AppButton
            block
            variant="secondary"
            @click="goBack"
          >
            {{ t('journey.create.back') }}
          </AppButton>
          <AppButton
            block
            type="submit"
            :loading="pending"
          >
            {{ t('journey.create.submit') }}
          </AppButton>
        </div>
      </template>
    </fieldset>
  </form>
</template>
