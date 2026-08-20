<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import { useOverlayDismiss } from '../composables/useOverlayDismiss'

/**
 * 약속 상세 헤더의 버거 버튼이 여는 바텀시트.
 *
 * 조건을 만족하지 않는 항목은 숨기지 않고 비활성으로 두고 이유를 함께 적는다.
 * 세 항목은 서로 다른 시점에만 열리기 때문에(출석은 활동 중, 후기는 완료 후,
 * 나가기는 참여 마감 전) 조건에 맞는 것만 넣으면 시트가 매번 다른 모양이 되고
 * 사용자는 나머지 기능이 있는 줄도 모른다.
 *
 * 다만 볼 자격 자체가 없는 항목은 아예 넣지 않는다. 출석 확인은 방장만,
 * 나가기는 방장이 아닌 참여자만 해당한다 — 방장은 어떤 상태에서도 자기 참여를
 * 취소할 수 없어서(APPOINTMENT-007) 비활성으로 보여줄 이유가 없다.
 */
interface Props {
  appointmentName: string
  /** 방장에게만 노출한다. */
  showAttendance: boolean
  attendanceDisabledReason?: string
  /** 참여 이력이 있는 회원에게만 노출한다. */
  showReviews: boolean
  reviewsDisabledReason?: string
  /** 방장이 아닌 활성 참여자에게만 노출한다. */
  showLeave: boolean
  leaveDisabledReason?: string
}

const {
  appointmentName,
  showAttendance,
  attendanceDisabledReason = undefined,
  showReviews,
  reviewsDisabledReason = undefined,
  showLeave,
  leaveDisabledReason = undefined,
} = defineProps<Props>()

const emit = defineEmits<{
  close: []
  attendance: []
  reviews: []
  leave: []
}>()

const { t } = useI18n()
const dialog = useTemplateRef('dialog')
useOverlayDismiss(dialog, () => emit('close'))

const title = computed(() => t('appointment.detail.menu.title'))
</script>

<template>
  <div
    class="fixed inset-0 z-40 flex items-end justify-center bg-scrim/70"
    role="presentation"
    @click.self="emit('close')"
  >
    <section
      ref="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="flex max-h-[88dvh] w-full max-w-[390px] flex-col gap-1 overflow-y-auto rounded-t-lg bg-surface-2 px-screen pt-3 pb-8 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 self-center rounded-pill bg-hairline-2"
      />

      <h2 class="font-display text-section-header uppercase text-ink-display">{{ title }}</h2>
      <p class="mb-3 truncate text-body-sm text-ink-3">{{ appointmentName }}</p>

      <button
        v-if="showAttendance"
        type="button"
        class="flex min-h-14 flex-col justify-center rounded-sm px-1 py-3 text-left active:bg-surface-3 disabled:active:bg-transparent"
        :disabled="attendanceDisabledReason !== undefined"
        @click="emit('attendance')"
      >
        <span
          class="text-title-sm"
          :class="attendanceDisabledReason === undefined ? 'text-ink-display' : 'text-ink-3'"
        >
          {{ t('appointment.detail.menu.attendance') }}
        </span>
        <span class="mt-0.5 text-caption text-ink-3">
          {{ attendanceDisabledReason ?? t('appointment.detail.menu.attendanceDescription') }}
        </span>
      </button>

      <button
        v-if="showReviews"
        type="button"
        class="flex min-h-14 flex-col justify-center rounded-sm px-1 py-3 text-left active:bg-surface-3 disabled:active:bg-transparent"
        :disabled="reviewsDisabledReason !== undefined"
        @click="emit('reviews')"
      >
        <span
          class="text-title-sm"
          :class="reviewsDisabledReason === undefined ? 'text-ink-display' : 'text-ink-3'"
        >
          {{ t('appointment.detail.menu.reviews') }}
        </span>
        <span class="mt-0.5 text-caption text-ink-3">
          {{ reviewsDisabledReason ?? t('appointment.detail.menu.reviewsDescription') }}
        </span>
      </button>

      <button
        v-if="showLeave"
        type="button"
        class="flex min-h-14 flex-col justify-center rounded-sm px-1 py-3 text-left active:bg-surface-3 disabled:active:bg-transparent"
        :disabled="leaveDisabledReason !== undefined"
        @click="emit('leave')"
      >
        <span
          class="text-title-sm"
          :class="leaveDisabledReason === undefined ? 'text-danger' : 'text-ink-3'"
        >
          {{ t('appointment.detail.menu.leave') }}
        </span>
        <span class="mt-0.5 text-caption text-ink-3">
          {{ leaveDisabledReason ?? t('appointment.detail.menu.leaveDescription') }}
        </span>
      </button>
    </section>
  </div>
</template>
