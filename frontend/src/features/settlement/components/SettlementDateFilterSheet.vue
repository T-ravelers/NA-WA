<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCalendarDate } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'

import type { SettlementDateRange } from '../model/settlementHistoryFilter'

import SettlementBottomSheet from './SettlementBottomSheet.vue'

/**
 * 완료 내역을 좁혀 볼 기간을 고르는 시트.
 *
 * 달력은 공용 컴포넌트를 그대로 쓴다. 고를 수 있는 날짜에 제약이 없다 — 이미 끝난
 * 정산을 보는 화면이라 과거·미래를 막을 이유가 없다.
 */
interface Props {
  /** 지금 적용된 기간. 없으면 아무 날짜도 고르지 않은 상태로 연다. */
  range: SettlementDateRange | null
}

const { range } = defineProps<Props>()

const emit = defineEmits<{
  /** 고른 기간. `null`이면 기간을 걷어내고 전체를 본다. */
  apply: [range: SettlementDateRange | null]
  close: []
}>()

const { t, locale } = useI18n()

// 시트 안에서 고르는 동안에는 화면 목록을 건드리지 않는다. Apply를 눌러야 반영된다.
const start = ref(range?.from ?? '')
const end = ref(range?.to ?? '')

/**
 * 첫 탭이 시작일, 두 번째 탭이 종료일이다. 이미 기간이 닫혀 있으면 새로 시작하고,
 * 시작일보다 이른 날을 고르면 두 값을 뒤집어 언제나 시작일이 앞에 온다.
 */
function selectDate(date: string): void {
  if (start.value === '' || end.value !== '') {
    start.value = date
    end.value = ''
    return
  }

  if (date < start.value) {
    end.value = start.value
    start.value = date
    return
  }

  end.value = date
}

const dateLabel = (value: string): string => formatCalendarDate(value, locale.value) || value

/** 고르는 중인 기간. 하루만 고른 동안에는 그 하루만 보여준다. */
function draftLabel(): string {
  if (start.value === '') return t('settlement.history.anyDate')
  if (end.value === '' || end.value === start.value) return dateLabel(start.value)

  return t('settlement.history.periodRange', {
    from: dateLabel(start.value),
    to: dateLabel(end.value),
  })
}

function apply(): void {
  if (start.value === '') {
    emit('apply', null)
    return
  }

  emit('apply', { from: start.value, to: end.value === '' ? start.value : end.value })
}
</script>

<template>
  <SettlementBottomSheet
    :label="t('settlement.history.choosePeriod')"
    @close="emit('close')"
  >
    <h2 class="text-title text-ink">{{ t('settlement.history.choosePeriod') }}</h2>
    <p class="mt-1 text-body-sm text-ink-3">{{ t('settlement.history.periodHint') }}</p>
    <p
      class="mt-4 text-body text-ink"
      aria-live="polite"
    >
      {{ draftLabel() }}
    </p>

    <CalendarGrid
      class="mt-4"
      :range-start="start || null"
      :range-end="end || null"
      :label-dates="false"
      @select="selectDate"
    />

    <div class="mt-6 grid grid-cols-2 gap-3">
      <AppButton
        variant="secondary"
        @click="emit('apply', null)"
      >
        {{ t('settlement.history.anyDate') }}
      </AppButton>
      <AppButton @click="apply">{{ t('settlement.history.applyPeriod') }}</AppButton>
    </div>
  </SettlementBottomSheet>
</template>
