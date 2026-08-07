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
  /**
   * 면의 밝기. 기본은 어두운 `surface-1`이다.
   *
   * `paper`는 지갑 잔액처럼 화면에서 한 장만 떠 있어야 하는 카드에 쓴다. 밝은 면 위
   * 글자는 `on-paper`로 함께 뒤집히므로, 화면에서 텍스트 색을 따로 지정하지 않는다.
   */
  tone?: 'surface' | 'paper'
}

const { raised = false, padding = 'base', tone = 'surface' } = defineProps<Props>()

const PADDING_CLASS: Record<NonNullable<Props['padding']>, string> = {
  none: 'p-0',
  base: 'p-4',
  lg: 'p-5',
}

const TONE_CLASS: Record<NonNullable<Props['tone']>, string> = {
  surface: 'bg-surface-1 text-ink',
  paper: 'bg-paper text-on-paper',
}
</script>

<template>
  <div
    class="overflow-hidden rounded-card"
    :class="[TONE_CLASS[tone], PADDING_CLASS[padding], raised ? 'shadow-raised' : '']"
  >
    <slot />
  </div>
</template>
