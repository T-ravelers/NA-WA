<script setup lang="ts">
import { computed } from 'vue'

/**
 * 선택 컨트롤의 알맹이 — 눌러서 고르는 작은 면 하나.
 *
 * 소비영역 필터 칩, QR 결제의 소비 카테고리 칩, `SegmentedControl`의 세그먼트가 모두
 * 같은 모양을 쓰는데 세 곳에 따로 적혀 있었다(#304 리뷰 → #305). 모양이 바뀌면 세 곳이
 * 따로 놀아서, 여기 한 곳으로 모은다.
 *
 * **카테고리 타입을 알지 못한다.** `fill`은 색 이름일 뿐이라 Explore 소비영역 4종과
 * 소비 카테고리 7종이 계속 갈라져 있어도 이 컴포넌트는 영향을 받지 않는다.
 * 선택 의미(`aria-pressed` / `role="radio"` + `aria-checked`)도 부르는 쪽이 정한다 —
 * 토글과 라디오는 접근성 트리에서 다른 것이라, 여기서 하나로 정하면 둘 중 하나가 틀린다.
 *
 * 포커스 링은 `app/styles/index.css`의 전역 `:focus-visible`이 그린다. 컴포넌트마다
 * 다시 적지 않는다.
 */

/** 선택됐을 때 채우는 면의 색. `Category` 유니온이 아니라 색 이름이다. */
export type SelectChipFill = 'paper' | 'beauty' | 'shopping' | 'show' | 'food'

interface Props {
  /** 화면에 보이는 문구. 로케일 문구는 부르는 쪽이 넣는다. */
  label: string
  /** 선택 여부. `interactive`가 아니면 항상 채워진 모습이다. */
  selected?: boolean
  /**
   * `sm` 티켓 안처럼 좁은 자리(px10 py4) · `md` 기본 칩(h36) ·
   * `segment` `SegmentedControl` 트랙 안에서 칸을 나눠 갖는 세그먼트.
   */
  size?: 'sm' | 'md' | 'segment'
  fill?: SelectChipFill
  /** 눌리는 컨트롤로 그린다. 지정하지 않으면 정적 라벨(`<span>`)이다. */
  interactive?: boolean
}

const {
  label,
  selected = false,
  size = 'md',
  fill = 'paper',
  interactive = false,
} = defineProps<Props>()

const emit = defineEmits<{ toggle: [] }>()

/* 코어색 면 위 텍스트는 `on-category`, 종이톤 면 위는 `on-paper`다. */
const FILL_CLASS: Record<SelectChipFill, string> = {
  paper: 'bg-paper-fill text-on-paper',
  beauty: 'bg-beauty text-on-category',
  shopping: 'bg-shopping text-on-category',
  show: 'bg-show text-on-category',
  food: 'bg-food text-on-category',
}

const SIZE_CLASS: Record<NonNullable<Props['size']>, string> = {
  sm: 'h-6 shrink-0 px-2.5 text-caption',
  md: 'h-9 shrink-0 px-3.5 text-caption',
  /* 높이는 트랙(h44 p4)이 정하므로 세그먼트는 칸만 나눠 갖는다. */
  segment: 'flex-1 text-title-sm',
}

/**
 * 선택되지 않았을 때의 모습.
 *
 * 세그먼트는 트랙(`surface-2`) 위에 얹혀 면이 이미 구분되므로 테두리를 두르지 않는다.
 * 칩은 바탕 위에 홀로 놓여서 테두리가 없으면 누를 수 있는 것으로 보이지 않는다.
 */
const unselectedClass = computed(() =>
  size === 'segment'
    ? 'bg-transparent text-ink-2'
    : 'border border-hairline bg-transparent text-ink-2',
)
</script>

<template>
  <button
    v-if="interactive"
    type="button"
    class="inline-flex items-center justify-center rounded-pill transition-transform active:scale-[0.98]"
    :class="[SIZE_CLASS[size], selected ? FILL_CLASS[fill] : unselectedClass]"
    @click="emit('toggle')"
  >
    {{ label }}
  </button>
  <span
    v-else
    class="inline-flex items-center justify-center rounded-pill"
    :class="[SIZE_CLASS[size], FILL_CLASS[fill]]"
  >
    {{ label }}
  </span>
</template>
