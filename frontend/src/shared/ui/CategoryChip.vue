<script setup lang="ts">
import SelectChip, { type SelectChipFill } from './SelectChip.vue'
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
 *
 * 모양과 크기는 `SelectChip`이 소유한다. 여기서는 **소비영역을 색 이름으로 옮기는
 * 일만** 한다 — `Category`를 늘려도 `SelectChip`은 그대로다.
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

/* 소비영역 4종은 칩 채움색과 이름이 같다. 늘어나면 여기서 컴파일이 깨진다. */
const FILL: Record<Category, SelectChipFill> = {
  beauty: 'beauty',
  shopping: 'shopping',
  show: 'show',
  food: 'food',
}
</script>

<template>
  <SelectChip
    :label="label"
    :fill="FILL[category]"
    :size="size"
    :interactive="interactive"
    :selected="selected"
    :aria-pressed="interactive ? selected : undefined"
    @toggle="emit('toggle')"
  />
</template>
