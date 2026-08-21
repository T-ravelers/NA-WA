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
  rangeStart !== null && rangeEnd !== null && date >= rangeStart && date <= rangeEnd

/**
 * 하루만 고른 상태. 하루 선택 모드와, 기간에서 시작일만 골랐거나 시작·종료가 같은 날인
 * 경우를 함께 본다. 같은 날을 두 번 탭하면 후자가 되는데, 둘을 다르게 그리면 같은 하루가
 * 탭 횟수에 따라 점과 띠로 갈린다.
 */
const isSingleDaySelection = computed(
  () =>
    selected !== null || (rangeStart !== null && (rangeEnd === null || rangeEnd === rangeStart)),
)

/**
 * 셀이 하루짜리 선택 점을 그리는지. 기간과 달리 칸을 채우지 않고 가운데 알약으로 남는다 —
 * 칸을 채우면 하루가 기간처럼 읽힌다.
 */
const showsDot = (index: number): boolean => {
  const cell = cells.value[index]

  if (cell === undefined || !cell.inMonth || !isSingleDaySelection.value) return false

  return cell.date === selected || cell.date === rangeStart
}

/**
 * 셀이 기간 띠를 그리는지. 이웃 달 셀은 날짜가 기간에 들어도 띠를 그리지 않는다 — 흐린
 * 글자 위에 흰 배경이 얹혀 선택할 수 없는 날짜가 오히려 도드라진다.
 */
const showsBand = (index: number): boolean => {
  const cell = cells.value[index]

  return cell !== undefined && cell.inMonth && !isSingleDaySelection.value && isInRange(cell.date)
}

/**
 * 띠가 시각적으로 시작하거나 끝나는 자리만 둥글게 깎는다. 기간의 양 끝만이 아니라, 주가
 * 바뀌어 줄이 끊기는 자리와 이웃 달 셀에 잘리는 자리도 띠가 새로 시작·끝나는 것으로 본다.
 */
const isBandLeftEdge = (index: number): boolean =>
  showsBand(index) && (index % 7 === 0 || !showsBand(index - 1))

const isBandRightEdge = (index: number): boolean =>
  showsBand(index) && (index % 7 === 6 || !showsBand(index + 1))

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
        v-for="(cell, index) in cells"
        :key="cell.date"
        type="button"
        class="flex h-9 items-center justify-center text-caption"
        :class="[
          // 폭은 둘 중 하나만 붙는다. w-full과 w-9를 함께 두면 어느 쪽이 이기는지가
          // CSS 정의 순서에 좌우된다.
          showsDot(index) ? 'mx-auto w-9' : 'w-full',
          !isSelectable(cell.date, cell.inMonth) && 'text-ink-3/40',
          isSelectable(cell.date, cell.inMonth) &&
            !showsBand(index) &&
            !showsDot(index) &&
            'rounded-pill text-ink-2',
          // 기간은 칸 전체를 채워 하나의 띠로 이어진다. 칸보다 좁은 칩을 가운데 두면
          // 흰색이어도 날짜마다 끊겨 보여 기간으로 읽히지 않는다. 띠가 시작·끝나는
          // 자리(기간 양 끝, 줄 넘김, 이웃 달 경계)만 둥글게 깎고 사이는 각지게 둔다.
          showsBand(index) && 'bg-paper-fill text-on-paper',
          // 하루짜리 선택은 칸을 채우지 않고 점으로 남는다. 배경이 있어야 화면에
          // 보인다 — 모서리만 깎으면 아무것도 그려지지 않는다.
          showsDot(index) && 'rounded-pill bg-paper-fill text-on-paper',
          isBandLeftEdge(index) && 'rounded-l-pill',
          isBandRightEdge(index) && 'rounded-r-pill',
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
