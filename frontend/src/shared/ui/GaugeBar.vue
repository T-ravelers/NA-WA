<script setup lang="ts">
import { computed } from 'vue'

/**
 * 예산·진행률 가로 막대. 시안에서 16회 반복된다.
 *
 * 비율만 받는다. 금액 계산이나 포맷은 호출하는 화면이 책임진다.
 */
interface Props {
  /** 0~1. 범위를 벗어난 값은 잘라낸다. */
  value: number
  /** 스크린 리더에 읽히는 이름. */
  label: string
}

const { value, label } = defineProps<Props>()

/** 0으로 나누기나 음수 폭 같은 것을 화면까지 내려보내지 않는다. */
const ratio = computed(() => {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(Math.max(value, 0), 1)
})

const percentage = computed(() => Math.round(ratio.value * 100))
</script>

<template>
  <div
    role="progressbar"
    :aria-label="label"
    :aria-valuenow="percentage"
    aria-valuemin="0"
    aria-valuemax="100"
    class="h-1.5 w-full overflow-hidden rounded-pill bg-hairline-strong"
  >
    <div
      class="h-full rounded-pill bg-gauge"
      :style="{ width: `${percentage}%` }"
    />
  </div>
</template>
