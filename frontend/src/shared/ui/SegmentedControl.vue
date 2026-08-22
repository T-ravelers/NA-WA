<script setup lang="ts">
/**
 * 두 개 이상의 배타 선택지를 고르는 pill 트랙.
 *
 * 시안에서 `Ongoing | Past`, `Itinerary | Spending`, `Group | Individual`에 6회 반복된다.
 *
 * 라디오 그룹으로 그린다. 탭이 아니다 — 선택이 콘텐츠 패널을 전환하는 것이 아니라
 * 같은 목록의 필터를 바꾸는 용도이기 때문이다.
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
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label"
    class="flex h-11 rounded-pill bg-surface-2 p-1"
  >
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :data-testid="`segment-${option.value}`"
      :aria-checked="option.value === modelValue"
      class="flex-1 rounded-pill text-title-sm"
      :class="
        option.value === modelValue ? 'bg-paper-fill text-on-paper' : 'bg-transparent text-ink-2'
      "
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
