<script setup lang="ts">
/**
 * 44×44 원형 아이콘 버튼.
 *
 * 시안에서 뒤로가기·더보기·화살표 등에 28회 반복된다. 아이콘만 있고 라벨 텍스트가 없으므로
 * 접근 가능한 이름을 `label`로 반드시 받는다.
 *
 * 크기는 터치 타깃 최소치(44px)에 맞춰 고정한다. 아이콘 자체 크기는 slot에서 정한다.
 */
type IconOrbVariant = 'plain' | 'overlay' | 'surface'

interface Props {
  /** 스크린 리더에 읽히는 이름. 아이콘만 있는 버튼이라 생략할 수 없다. */
  label: string
  /**
   * `plain` 배경 없음(헤더 안) · `overlay` 이미지 위 · `surface` 면 위.
   */
  variant?: IconOrbVariant
}

const { label, variant = 'plain' } = defineProps<Props>()

const emit = defineEmits<{ click: [] }>()

const VARIANT_CLASS: Record<IconOrbVariant, string> = {
  plain: 'bg-transparent',
  overlay: 'bg-scrim/45',
  surface: 'bg-surface-2',
}
</script>

<template>
  <button
    type="button"
    class="flex size-11 shrink-0 items-center justify-center rounded-pill text-ink"
    :class="VARIANT_CLASS[variant]"
    :aria-label="label"
    @click="emit('click')"
  >
    <slot />
  </button>
</template>
