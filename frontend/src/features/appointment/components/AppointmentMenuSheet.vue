<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import { useOverlayDismiss } from '../composables/useOverlayDismiss'

/**
 * 약속 상세 헤더의 버거 버튼이 여는 바텀시트.
 *
 * 항목은 조건을 만족하지 않아도 숨기지 않고 비활성으로 두고 이유를 함께 적는다.
 * 출석 확정은 활동이 끝난 뒤에 열리므로, 조건에 맞을 때만 넣으면 시트가 열 때마다
 * 다른 모양이 되고 사용자는 그 기능이 있다는 것조차 알 수 없다.
 *
 * **시트는 비어 있지 않다.** 출석 확정은 방장만 할 수 있어(APPOINTMENT-004) 방장이
 * 아니면 담을 항목이 없는데, 그 판정은 이 시트가 아니라 **호출부가 한 곳에서** 한다 —
 * 버거 버튼과 시트가 같은 조건을 쓴다. 여기에 `v-if`를 하나 더 두면 조건이 두 군데로
 * 갈라져, 열려 있는 동안 값이 뒤집힐 때 빈 시트가 남는다(#483 리뷰에서 실제로 났다).
 *
 * 나가기는 여기 있다가 회원 목록의 내 행으로 옮겼다. 이 시트는 "약속"을 대상으로
 * 하는 자리라 자기 참여를 취소하는 것인지 약속을 없애는 것인지 구분되지 않았다.
 */
interface Props {
  appointmentName: string
  /** 방장에게만 넣는다. */
  attendanceDisabledReason?: string
}

const { appointmentName, attendanceDisabledReason = undefined } = defineProps<Props>()

const emit = defineEmits<{
  close: []
  attendance: []
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
    </section>
  </div>
</template>
