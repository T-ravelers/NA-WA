<script setup lang="ts">
import { IconCheck, IconUsers } from '@tabler/icons-vue'
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
  /**
   * 여정 기간의 시작 기본값. 담으려던 항목의 운영 기간에서 온다.
   *
   * 다른 화면이 "여정이 없어서" 이 화면으로 보냈다면, 그 사람은 정의상 그 항목과
   * 겹치는 여정이 하나도 없는 사람이다. 빈 폼에는 무엇과 겹쳐야 하는지가 없어서
   * 또 안 겹치는 기간으로 만들고 돌아오기 쉽다.
   */
  initialStartDate?: string
  /** 여정 기간의 종료 기본값. 상시 이벤트처럼 상한이 없으면 오지 않는다. */
  initialEndDate?: string
}

const {
  pending = false,
  errorMessage = undefined,
  initialStartDate = '',
  initialEndDate = '',
} = defineProps<Props>()

const emit = defineEmits<{ submit: [input: JourneyCreateInput] }>()

const { t } = useI18n()

const step = ref<1 | 2>(1)
const errors = ref<JourneyFormErrors>({})
const draft = reactive<JourneyFormDraft>({
  title: '',
  startDate: initialStartDate,
  endDate: initialEndDate,
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
    <!--
      시안 J3/J3b의 단계 표시는 현재 단계가 24×6 알약, 지난·남은 단계가 6px 점이다.
      지난 단계 점은 `ink-2`, 아직 오지 않은 단계 점은 `hairline`으로 밝기가 다르다.
      단계 이름은 화면에서 빠지지만 각 단계의 접근 가능한 이름으로 남겨 두고,
      `aria-current="step"`도 그대로 둔다.
    -->
    <div class="flex items-center gap-1.5">
      <ol
        class="flex items-center gap-1.5"
        :aria-label="t('journey.create.stepLabel')"
      >
        <li
          class="h-1.5 rounded-pill"
          :class="step === 1 ? 'w-6 bg-ink' : 'w-1.5 bg-ink-2'"
          :aria-current="step === 1 ? 'step' : undefined"
        >
          <span class="sr-only">{{ t('journey.create.stepOne') }}</span>
        </li>
        <li
          class="h-1.5 rounded-pill"
          :class="step === 2 ? 'w-6 bg-ink' : 'w-1.5 bg-hairline'"
          :aria-current="step === 2 ? 'step' : undefined"
        >
          <span class="sr-only">{{ t('journey.create.stepTwo') }}</span>
        </li>
      </ol>

      <p
        class="ml-1.5 text-caption tabular-nums text-ink-3"
        aria-live="polite"
        :data-testid="`journey-create-step-${step}`"
      >
        {{ t('journey.create.stepCount', { current: step }) }}
      </p>
    </div>

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
          data-testid="journey-create-next"
          @click="goToPreferences"
        >
          {{ t('journey.create.next') }}
        </AppButton>
      </template>

      <template v-else>
        <AmountInput
          v-model="draft.budgetAmount"
          currency-symbol="P"
          symbol-position="suffix"
          :label="t('journey.create.budget')"
          :helper="t('journey.create.budgetHelper')"
          :error="translatedError(errors.budgetAmount)"
        />

        <fieldset class="flex flex-col gap-3">
          <legend class="text-caption text-ink-2">
            {{ t('journey.create.companions') }}
            <span class="text-caption text-ink-3">
              · {{ t('journey.create.companionsOptional') }}
            </span>
          </legend>

          <!--
            시안 J3b의 선택 상태는 `success` 링과 같은 색 14% 틴트다. 미선택 행은 보더
            없이 `surface-1` 면만 둔다. 색만으로 선택을 말하지 않도록 `aria-pressed`와
            체크 아이콘을 함께 둔다.
          -->
          <button
            v-for="option in companionOptions"
            :key="option.value"
            type="button"
            class="flex min-h-16 items-center gap-3 rounded-sm border px-4 py-3.5 text-left"
            :class="
              draft.companionPreference === option.value
                ? 'border-success bg-success/15'
                : 'border-transparent bg-surface-1'
            "
            :aria-pressed="draft.companionPreference === option.value"
            @click="toggleCompanion(option.value)"
          >
            <!-- 시안 J3b의 좌측 원형 아이콘. 장식이므로 접근성 트리에서 감춘다. -->
            <span
              aria-hidden="true"
              class="flex size-11 shrink-0 items-center justify-center rounded-pill"
              :class="
                draft.companionPreference === option.value
                  ? 'bg-success text-ink'
                  : 'bg-surface-2 text-icon-muted'
              "
            >
              <IconUsers
                :size="22"
                :stroke-width="1.75"
              />
            </span>

            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <span class="text-title-sm text-ink">{{ t(option.labelKey) }}</span>
              <span class="text-body-sm text-ink-2">{{ t(option.descriptionKey) }}</span>
            </span>

            <span
              aria-hidden="true"
              class="flex size-6 shrink-0 items-center justify-center rounded-pill border"
              :class="
                draft.companionPreference === option.value
                  ? 'border-transparent bg-status-ongoing text-on-category'
                  : 'border-hairline-2'
              "
            >
              <IconCheck
                v-if="draft.companionPreference === option.value"
                :size="14"
                :stroke-width="2.5"
              />
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
