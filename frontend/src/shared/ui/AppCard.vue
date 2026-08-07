<script setup lang="ts">
/**
 * 기본 카드 면.
 *
 * 시안의 `surface-1` r20 컨테이너다. 티켓 조형이 필요한 곳에는 `Ticket`을 쓰고,
 * 여기는 목록 항목·요약 블록처럼 절취선이 없는 면에 쓴다.
 *
 * 카드 자체를 버튼으로 만들지 않는다. 카드 안에 링크와 버튼이 함께 들어가는 화면이
 * 많아 중첩 인터랙티브가 되기 때문이다. 탭 동작은 카드 안쪽에 둔다.
 */
interface Props {
  /** 떠 있는 카드(지갑 등)에만 그림자를 쓴다. 기본은 거의 플랫이다. */
  raised?: boolean
  /** `none`은 이미지를 가장자리까지 채울 때 쓴다. */
  padding?: 'none' | 'base' | 'lg'
}

const { raised = false, padding = 'base' } = defineProps<Props>()

const PADDING_CLASS: Record<NonNullable<Props['padding']>, string> = {
  none: 'p-0',
  base: 'p-4',
  lg: 'p-5',
}
</script>

<template>
  <div
    class="overflow-hidden rounded-card bg-surface-1 text-ink"
    :class="[PADDING_CLASS[padding], raised ? 'shadow-raised' : '']"
  >
    <slot />
  </div>
</template>
