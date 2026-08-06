<script setup lang="ts">
/**
 * 소비영역을 나타내는 8×8 원형 표식. 타임라인·칩·범례에서 반복된다.
 *
 * 색은 장식이 아니라 영역을 코드하는 수단이므로 임의 색을 받지 않는다.
 * 색만으로 구분되면 색각 이상 사용자가 읽을 수 없으니, 화면에서는 항상 라벨과 함께 쓴다.
 * 그래서 이 컴포넌트 자체는 장식으로 표시하고 접근성 트리에서 감춘다.
 */
export type Category = 'beauty' | 'shopping' | 'show' | 'food'

interface Props {
  category: Category
}

const { category } = defineProps<Props>()

const CATEGORY_CLASS: Record<Category, string> = {
  beauty: 'bg-beauty',
  shopping: 'bg-shopping',
  show: 'bg-show',
  food: 'bg-food',
}
</script>

<template>
  <span
    aria-hidden="true"
    class="inline-block size-2 shrink-0 rounded-pill"
    :class="CATEGORY_CLASS[category]"
  />
</template>
