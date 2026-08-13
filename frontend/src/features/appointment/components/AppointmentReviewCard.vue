<script setup lang="ts">
import { computed, reactive, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import type {
  AppointmentMember,
  AppointmentReviewRequest,
  ReviewCategory,
  ReviewKeywordCode,
} from '../api/appointmentApi'

interface Props {
  member: AppointmentMember
  expanded?: boolean
  pending?: boolean
  completed?: boolean
  errorMessage?: string
}

const {
  member,
  expanded = true,
  pending = false,
  completed = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{
  submit: [request: AppointmentReviewRequest]
  toggle: []
}>()
const { t } = useI18n()
const panelId = `appointment-review-${useId()}`

const categories: ReviewCategory[] = ['PUNCTUALITY', 'MANNERS', 'COMMUNICATION']
const keywords: ReviewKeywordCode[] = [
  'FRIENDLY',
  'ON_TIME',
  'CONSIDERATE',
  'GOOD_COMMUNICATOR',
  'WOULD_JOIN_AGAIN',
]
const scores = reactive<Record<ReviewCategory, number | null>>({
  PUNCTUALITY: null,
  MANNERS: null,
  COMMUNICATION: null,
})
const selectedKeywords = ref<ReviewKeywordCode[]>([])

const isComplete = computed(() => categories.every((category) => scores[category] !== null))

function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}

function selectScore(category: ReviewCategory, score: number): void {
  scores[category] = score
}

function toggleKeyword(keyword: ReviewKeywordCode): void {
  if (selectedKeywords.value.includes(keyword)) {
    selectedKeywords.value = selectedKeywords.value.filter((value) => value !== keyword)
    return
  }

  selectedKeywords.value = [...selectedKeywords.value, keyword]
}

function submit(): void {
  if (!isComplete.value || pending || completed) return

  emit('submit', {
    reviewedAppointmentMemberId: member.appointmentMemberId,
    scores: {
      PUNCTUALITY: scores.PUNCTUALITY as number,
      MANNERS: scores.MANNERS as number,
      COMMUNICATION: scores.COMMUNICATION as number,
    },
    keywordCodes: selectedKeywords.value,
  })
}
</script>

<template>
  <AppCard padding="lg">
    <article class="flex flex-col gap-4">
      <button
        type="button"
        class="flex w-full items-center gap-3 text-left"
        :aria-expanded="expanded"
        :aria-controls="panelId"
        @click="emit('toggle')"
      >
        <div
          class="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-2 text-title text-ink"
          aria-hidden="true"
        >
          <img
            v-if="member.profileImageUrl"
            :src="member.profileImageUrl"
            alt=""
            class="size-full object-cover"
          />
          <span v-else>{{ initials(member.displayName) }}</span>
        </div>
        <div class="min-w-0 flex-1">
          <h2 class="truncate text-title-sm text-ink">{{ member.displayName }}</h2>
        </div>
        <AppBadge
          :tone="completed ? 'completed' : 'pending'"
          dot
        >
          {{ completed ? t('appointment.review.completed') : t('appointment.review.pending') }}
        </AppBadge>
        <span
          class="text-title text-ink-3"
          aria-hidden="true"
        >
          {{ expanded ? '⌃' : '⌄' }}
        </span>
      </button>

      <div
        v-if="expanded"
        :id="panelId"
        class="flex flex-col gap-4"
      >
        <p
          v-if="!completed"
          class="text-caption text-ink-2"
        >
          {{ t('appointment.review.chooseScores') }}
        </p>

        <div class="flex flex-col gap-3">
          <fieldset
            v-for="category in categories"
            :key="category"
            class="flex items-center justify-between gap-3"
          >
            <legend class="text-caption text-ink-2">
              {{ t(`appointment.review.category.${category}`) }}
            </legend>
            <div class="flex gap-1">
              <button
                v-for="score in 5"
                :key="score"
                type="button"
                class="flex size-11 items-center justify-center text-title text-ink-3"
                :class="
                  scores[category] !== null && score <= scores[category] ? 'text-settlement' : ''
                "
                :aria-label="
                  t('appointment.review.selectScore', {
                    category: t(`appointment.review.category.${category}`),
                    score,
                  })
                "
                :aria-pressed="scores[category] === score"
                :disabled="pending || completed"
                @click="selectScore(category, score)"
              >
                ★
              </button>
            </div>
          </fieldset>
        </div>

        <fieldset class="flex flex-col gap-4">
          <legend class="text-caption text-ink-2">{{ t('appointment.review.keywords') }}</legend>
          <div class="flex flex-wrap gap-2 pt-3">
            <button
              v-for="keyword in keywords"
              :key="keyword"
              type="button"
              class="rounded-pill border px-3 py-2 text-caption"
              :class="
                selectedKeywords.includes(keyword)
                  ? 'border-settlement bg-settlement/10 text-settlement'
                  : 'border-hairline-strong text-ink-2'
              "
              :aria-pressed="selectedKeywords.includes(keyword)"
              :disabled="pending || completed"
              @click="toggleKeyword(keyword)"
            >
              {{ t(`appointment.review.keyword.${keyword}`) }}
            </button>
          </div>
        </fieldset>

        <p
          v-if="errorMessage !== undefined"
          class="text-caption text-danger"
          role="alert"
        >
          {{ errorMessage }}
        </p>

        <AppButton
          block
          :loading="pending"
          :disabled="!isComplete || completed"
          @click="submit"
        >
          {{ t('appointment.review.save') }}
        </AppButton>
      </div>
    </article>
  </AppCard>
</template>
