<script setup lang="ts">
import SelectChip from './SelectChip.vue'
import { useRovingRadioGroup } from './useRovingRadioGroup'

/**
 * 두 개 이상의 배타 선택지를 고르는 pill 트랙.
 *
 * 시안에서 `Ongoing | Past`, `Itinerary | Spending`, `Group | Individual`에 6회 반복된다.
 *
 * 라디오 그룹으로 그린다. 탭이 아니다 — 선택이 콘텐츠 패널을 전환하는 것이 아니라
 * 같은 목록의 필터를 바꾸는 용도이기 때문이다. 화살표 키 이동과 그룹당 탭 스톱 하나는
 * `useRovingRadioGroup`이 맡는다.
 */
interface Option {
  value: string
  label: string
}

interface Props {
  modelValue: string
  options: Option[]
  /** 그룹 전체의 접근 가능한 이름. */
  label: string
}

const { modelValue, options, label } = defineProps<Props>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

function select(value: string): void {
  emit('update:modelValue', value)
}

/**
 * 조형과 접근성 트리가 같은 판정을 쓰게 묶는다.
 *
 * `selected`와 `aria-checked`에 같은 식을 두 번 적으면 한쪽만 고쳐도 아무도 못 잡는다 —
 * 화면은 골라진 것으로 그려지는데 보조기술은 아니라고 읽는 상태가 조용히 만들어진다.
 */
function isSelected(value: string): boolean {
  return value === modelValue
}

const { onKeydown, tabindexFor } = useRovingRadioGroup(
  () => options.map((option) => option.value),
  () => modelValue,
  select,
)
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label"
    class="flex h-11 rounded-pill bg-surface-2 p-1"
    @keydown="onKeydown"
  >
    <SelectChip
      v-for="option in options"
      :key="option.value"
      interactive
      size="segment"
      :label="option.label"
      :selected="isSelected(option.value)"
      role="radio"
      :data-testid="`segment-${option.value}`"
      :data-value="option.value"
      :aria-checked="isSelected(option.value)"
      :tabindex="tabindexFor(option.value)"
      @toggle="select(option.value)"
    />
  </div>
</template>
