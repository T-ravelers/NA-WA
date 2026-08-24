<script setup lang="ts">
import { computed } from 'vue'

import type { Category } from './category'

/**
 * 티켓 — NA-WA의 시그니처 조형.
 *
 * 라운드 사각을 퍼포레이션(점선 절취선)으로 body와 stub으로 나누고, 절취선 양끝을
 * 원형으로 파낸다(노치). 여정 카드·추천 카드·리포트 요약·성향유형이 모두 이 하나의
 * 조형을 공유한다.
 *
 * **도메인에서 이 조형을 다시 만들지 않는다.** 노치 지름, 점선 굵기, 절취선 위치가
 * 화면마다 달라지면 브랜드가 무너진다. 필요한 변형은 여기에 prop으로 추가한다.
 *
 * 노치는 배경색 원을 덧대는 것이 아니라 `mask`로 실제로 파낸다. 티켓이 어떤 면 위에
 * 놓이든 뒤가 비쳐야 하기 때문이다. 배경색 원을 쓰면 캐러셀·이미지 위에서 어긋난다.
 *
 * 클릭 가능한 컨테이너로 만들지 않는다. 티켓 안에는 칩·링크·버튼이 들어가므로
 * 바깥을 버튼으로 감싸면 중첩 인터랙티브가 되어 스크린 리더에서 읽히지 않는다.
 * 탭 동작이 필요하면 stub 안에 버튼이나 RouterLink를 둔다.
 */
type TicketTone = 'paper' | 'dark' | Category

interface Props {
  /**
   * `vertical` 위=body·아래=stub (여정 티켓) · `horizontal` 좌=body·우=stub (카테고리 티켓).
   */
  orientation?: 'vertical' | 'horizontal'
  /**
   * 분할 위치. 세로형은 body의 높이(px), 가로형은 body의 폭(px)이다.
   * 절취선과 노치가 이 값 위에 놓인다.
   *
   * 시안 실측: 여정 티켓 이미지 154 · 카테고리 티켓 포토슬롯 88.
   */
  bodySize: number
  /** 배경과 그 위 텍스트 색을 함께 정한다. */
  tone?: TicketTone
  /** 노치 지름. 시안 실측은 여정 티켓 20 · 카테고리 티켓 12다. */
  notchSize?: number
  /** 선택 상태. 영역 코어색 2px 링을 두른다. */
  selected?: boolean
}

const {
  orientation = 'vertical',
  bodySize,
  tone = 'dark',
  notchSize = 20,
  selected = false,
} = defineProps<Props>()

const TONE_CLASS: Record<TicketTone, string> = {
  paper: 'bg-paper text-on-paper',
  dark: 'bg-surface-1 text-ink',
  beauty: 'bg-beauty text-on-category',
  shopping: 'bg-shopping text-on-category',
  show: 'bg-show text-on-category',
  food: 'bg-food text-on-category',
}

/**
 * 절취선 색.
 *
 * 밝은 면 위에서는 `hairline`이 보이지 않고, 어두운 면 위에서는 반투명 검정이 보이지
 * 않는다. 톤마다 뒤집어야 해서 클래스가 아니라 값으로 둔다.
 */
const PERFORATION_COLOR: Record<TicketTone, string> = {
  paper: 'rgb(16 24 40 / 0.35)',
  dark: 'var(--color-hairline-2)',
  beauty: 'rgb(14 14 12 / 0.3)',
  shopping: 'rgb(14 14 12 / 0.3)',
  show: 'rgb(14 14 12 / 0.3)',
  food: 'rgb(14 14 12 / 0.3)',
}

/** 선택 링은 티켓 색과 구분돼야 하므로 밝은 면 위에서는 잉크색을 쓴다. */
const SELECTED_RING_COLOR: Record<TicketTone, string> = {
  paper: 'var(--color-on-paper)',
  dark: 'var(--color-ink)',
  beauty: 'var(--color-on-category)',
  shopping: 'var(--color-on-category)',
  show: 'var(--color-on-category)',
  food: 'var(--color-on-category)',
}

const isVertical = computed(() => orientation === 'vertical')

const notchRadius = computed(() => notchSize / 2)

/**
 * 노치 컷아웃.
 *
 * 절취선 양끝에 원을 하나씩 두고 두 마스크를 교집합으로 합성한다. 0.5px의 여유는
 * 원 경계의 계단 현상을 없애기 위한 것이다.
 */
const maskImage = computed(() => {
  const r = notchRadius.value
  const positions = isVertical.value
    ? [`0 ${bodySize}px`, `100% ${bodySize}px`]
    : [`${bodySize}px 0`, `${bodySize}px 100%`]

  return positions
    .map(
      (at) =>
        `radial-gradient(circle ${r}px at ${at}, transparent ${r}px, var(--color-scrim) ${r + 0.5}px)`,
    )
    .join(', ')
})

const rootStyle = computed(() => ({
  maskImage: maskImage.value,
  WebkitMaskImage: maskImage.value,
  maskComposite: 'intersect',
  WebkitMaskComposite: 'source-in',
  /*
   * 선택 링은 inset 그림자로 그린다. 바깥 outline은 마스크 밖에 있어 파낸 노치를
   * 가로질러 그려진다.
   */
  boxShadow: selected ? `inset 0 0 0 2px ${SELECTED_RING_COLOR[tone]}` : undefined,
}))

/** 절취선은 노치를 침범하지 않도록 양끝을 노치 반지름만큼 비운다. */
const perforationStyle = computed(() => {
  const gap = `${notchRadius.value + 2}px`
  const color = PERFORATION_COLOR[tone]

  return isVertical.value
    ? { left: gap, right: gap, top: `${bodySize - 1}px`, borderTop: `2px dashed ${color}` }
    : { top: gap, bottom: gap, left: `${bodySize - 1}px`, borderLeft: `2px dashed ${color}` }
})

const bodyStyle = computed(() =>
  isVertical.value ? { height: `${bodySize}px` } : { width: `${bodySize}px` },
)
</script>

<template>
  <div
    class="relative overflow-hidden rounded-ticket"
    :class="[TONE_CLASS[tone], isVertical ? 'flex flex-col' : 'flex flex-row']"
    :style="rootStyle"
  >
    <div
      class="relative shrink-0 overflow-hidden"
      :style="bodyStyle"
    >
      <slot name="body" />
    </div>

    <!-- 절취선. 장식이므로 접근성 트리에서 감춘다. -->
    <div
      aria-hidden="true"
      class="pointer-events-none absolute"
      :style="perforationStyle"
    />

    <div class="min-w-0 flex-1">
      <slot name="stub" />
    </div>
  </div>
</template>
