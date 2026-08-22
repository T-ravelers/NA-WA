<script setup lang="ts">
import { computed } from 'vue'

import { seriesSurfaceClass } from './seriesPalette'

/**
 * Report 범례의 8×8 표식.
 *
 * `shared/ui`의 `CategoryDot`을 쓰지 않는다. 그쪽은 Explore 소비영역 4종만 받는데
 * Report의 카테고리는 임의 문자열이라 타입이 맞지 않는다. 색은 `seriesPalette`가 정한다.
 *
 * 색만으로는 아무것도 읽히지 않으므로 접근성 트리에서 감춘다. 범례 행이 라벨과 금액을
 * 텍스트로 이미 전달한다.
 */
interface Props {
  /** 지출 카테고리. 색은 이 값으로만 정해진다. */
  category: string
}

const { category } = defineProps<Props>()

const surfaceClass = computed(() => seriesSurfaceClass(category))
</script>

<template>
  <span
    aria-hidden="true"
    class="inline-block size-2 shrink-0 rounded-pill"
    :class="surfaceClass"
  />
</template>
