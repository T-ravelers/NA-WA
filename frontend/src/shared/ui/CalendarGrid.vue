<script setup lang="ts">
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  buildCalendarMonth,
  formatCalendarDate,
  parseCalendarDate,
  shiftCalendarMonth,
} from '@/shared/lib/datetime'

/**
 * 달 하나를 보여주고 날짜를 고르는 달력.
 *
 * 네이티브 `<input type="date">`를 쓰지 않는 이유가 있다. 그 입력의 표시 형식은 브라우저
 * UI 언어를 따르고 `lang` 속성으로 바꿀 수 없어, 한국어 브라우저에서 `연도. 월. 일.`로
 * 나온다. 방한 외국인이 대상이라 앱 로케일을 따르는 달력을 직접 그린다.
 *
 * 하루 선택과 기간 선택을 모두 지원한다. **고를 수 있는 날짜인지는 호출부가 판단한다** —
 * 여행 기간, 프리셋, 제약 없음이 화면마다 다르다.
 */
interface Props {
  /** 하루 선택에서 고른 날짜. 기간 선택에서는 `rangeStart`·`rangeEnd`를 쓴다. */
  selected?: string | null
  rangeStart?: string | null
  rangeEnd?: string | null
  /** 처음 보여줄 달. 지정하지 않으면 선택값이나 이번 달을 쓴다. */
  initialMonth?: string | null
  /** `false`를 돌려주면 그 칸은 흐리게 그리고 누를 수 없다. */
  isDateAllowed?: (date: string) => boolean
  /** 날짜 셀에 스크린 리더용 이름을 붙인다. 기간 선택 화면은 끄기도 한다. */
  labelDates?: boolean
}

const {
  selected = null,
  rangeStart = null,
  rangeEnd = null,
  initialMonth = null,
  isDateAllowed = () => true,
  labelDates = true,
} = defineProps<Props>()

const emit = defineEmits<{ select: [date: string] }>()

const { t, locale } = useI18n()

const WEEKDAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'] as const

function startOfMonth(value: string | null): Date | null {
  const date = parseCalendarDate(value)

  return date === null ? null : new Date(date.getFullYear(), date.getMonth(), 1)
}

function initialCursor(): Date {
  const today = new Date()

  return (
    startOfMonth(initialMonth) ??
    startOfMonth(selected) ??
    startOfMonth(rangeStart) ??
    new Date(today.getFullYear(), today.getMonth(), 1)
  )
}

const monthCursor = ref(initialCursor())

// 시트를 열어 둔 채 바깥에서 선택이 바뀌면 그 달로 따라간다.
watch(
  () => [selected, rangeStart] as const,
  () => {
    const next = startOfMonth(selected) ?? startOfMonth(rangeStart)

    if (next !== null && next.getTime() !== monthCursor.value.getTime()) {
      monthCursor.value = next
    }
  },
)

const monthLabel = computed(() =>
  new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric' }).format(
    monthCursor.value,
  ),
)

const cells = computed(() => buildCalendarMonth(monthCursor.value))

const isSelectable = (date: string, inMonth: boolean): boolean => inMonth && isDateAllowed(date)

const isSelected = (date: string): boolean =>
  date === selected || date === rangeStart || date === rangeEnd

const isInRange = (date: string): boolean =>
  rangeStart !== null && rangeEnd !== null && date > rangeStart && date < rangeEnd

const accessibleLabel = (date: string): string =>
  t('calendar.selectDate', {
    date: formatCalendarDate(date, locale.value, { dateStyle: 'long' }) || date,
  })

const shiftMonth = (offset: number): void => {
  monthCursor.value = shiftCalendarMonth(monthCursor.value, offset)
}

const select = (date: string, inMonth: boolean): void => {
  if (isSelectable(date, inMonth)) emit('select', date)
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between">
      <button
        type="button"
        class="flex size-8 items-center justify-center text-ink-2"
        :aria-label="t('calendar.previousMonth')"
        @click="shiftMonth(-1)"
      >
        <IconChevronLeft
          :size="18"
          aria-hidden="true"
        />
      </button>
      <strong class="text-title-sm text-ink">{{ monthLabel }}</strong>
      <button
        type="button"
        class="flex size-8 items-center justify-center text-ink-2"
        :aria-label="t('calendar.nextMonth')"
        @click="shiftMonth(1)"
      >
        <IconChevronRight
          :size="18"
          aria-hidden="true"
        />
      </button>
    </div>

    <div class="mt-3 grid grid-cols-7 text-center text-micro text-ink-3">
      <span
        v-for="day in WEEKDAYS"
        :key="day"
        >{{ t(`calendar.weekdays.${day}`) }}</span
      >
    </div>

    <div class="mt-2 grid grid-cols-7 gap-y-1 text-center">
      <button
        v-for="cell in cells"
        :key="cell.date"
        type="button"
        class="mx-auto flex size-9 items-center justify-center rounded-pill text-caption"
        :class="[
          !isSelectable(cell.date, cell.inMonth) ? 'text-ink-3/40' : 'text-ink-2',
          /*
           * 선택 칩과 범위 배경이 같은 칸에 겹치면 CSS 순서에 따라 칩이 묻힌다.
           * 양 끝 날짜는 범위 배경을 받지 않게 상호 배타로 가른다.
           */
          isSelected(cell.date)
            ? 'bg-paper-fill text-on-paper'
            : isInRange(cell.date) && 'rounded-none bg-paper-fill text-on-paper',
        ]"
        :aria-label="labelDates ? accessibleLabel(cell.date) : undefined"
        :aria-pressed="labelDates ? isSelected(cell.date) : undefined"
        :disabled="!isSelectable(cell.date, cell.inMonth)"
        @click="select(cell.date, cell.inMonth)"
      >
        {{ cell.day }}
      </button>
    </div>
  </div>
</template>
