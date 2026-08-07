<script setup lang="ts">
import type { Category } from './category'

/**
 * 소비영역 칩.
 *
 * 두 가지 용도를 한 컴포넌트로 덮는다. 모양이 같은데 파일이 둘로 갈라지면
 * 곧 서로 다르게 변한다.
 *
 * - 필터·관심사 선택 (`interactive`): 토글 버튼. 선택 시 영역 코어색으로 채운다.
 * - 티켓 안의 구성 카테고리 표시: 정적 라벨. 항상 채워진 상태로 그린다.
 *
 * 색만으로는 영역을 구분할 수 없으므로 칩에는 반드시 텍스트 라벨이 들어간다.
 * 색은 라벨을 보조할 뿐이다.
 */
interface Props {
  category: Category
  /** 화면에 보이는 문구. 로케일 문구를 호출하는 쪽에서 넣는다. */
  label: string
  /** 토글 버튼으로 그린다. 지정하지 않으면 정적 라벨이다. */
  interactive?: boolean
  /** `interactive`일 때의 선택 여부. 정적 라벨은 항상 채워진다. */
  selected?: boolean
  /** `sm`은 티켓 안처럼 좁은 자리용이다. 시안 실측 px10 py4. */
  size?: 'sm' | 'md'
}

const { category, label, interactive = false, selected = false, size = 'md' } = defineProps<Props>()

const emit = defineEmits<{ toggle: [] }>()

/* 코어색 면 위 텍스트는 `on-category`다. 종이톤 면 위의 `on-paper`와 다르다. */
const FILLED_CLASS: Record<Category, string> = {
  beauty: 'bg-beauty text-on-category',
  shopping: 'bg-shopping text-on-category',
  show: 'bg-show text-on-category',
  food: 'bg-food text-on-category',
}

const SIZE_CLASS: Record<NonNullable<Props['size']>, string> = {
  sm: 'h-6 px-2.5',
  md: 'h-9 px-3.5',
}

const UNSELECTED_CLASS = 'border border-hairline bg-transparent text-ink-2'
</script>

<template>
  <button
    v-if="interactive"
    type="button"
    :aria-pressed="selected"
    class="inline-flex shrink-0 items-center rounded-pill text-caption transition-transform active:scale-[0.98]"
    :class="[SIZE_CLASS[size], selected ? FILLED_CLASS[category] : UNSELECTED_CLASS]"
    @click="emit('toggle')"
  >
    {{ label }}
  </button>
  <span
    v-else
    class="inline-flex shrink-0 items-center rounded-pill text-caption"
    :class="[SIZE_CLASS[size], FILLED_CLASS[category]]"
  >
    {{ label }}
  </span>
</template>
