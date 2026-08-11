<script setup lang="ts">
/**
 * 44×44 또는 48×48 원형 아이콘 버튼.
 *
 * 시안에서 뒤로가기·더보기·화살표 등에 28회 반복된다. 아이콘만 있고 라벨 텍스트가 없으므로
 * 접근 가능한 이름을 `label`로 반드시 받는다.
 *
 * 기본 크기는 터치 타깃 최소치(44px)다. 화면 헤더에서 48px가 필요할 때만 `lg`를 쓴다.
 * 아이콘 자체 크기는 slot에서 정한다.
 */
type IconOrbVariant = 'plain' | 'overlay' | 'surface'
type IconOrbSize = 'md' | 'lg'

interface Props {
  /** 스크린 리더에 읽히는 이름. 아이콘만 있는 버튼이라 생략할 수 없다. */
  label: string
  /** `md` 44px(기본) · `lg` 48px(화면 헤더). */
  size?: IconOrbSize
  /** 토글 버튼일 때 현재 눌림 상태. 일반 버튼이면 생략한다. */
  pressed?: boolean | null
  /**
   * `plain` 배경 없음(헤더 안) · `overlay` 이미지 위 · `surface` 면 위.
   */
  variant?: IconOrbVariant
}

const { label, size = 'md', pressed = null, variant = 'plain' } = defineProps<Props>()

const emit = defineEmits<{ click: [] }>()

const VARIANT_CLASS: Record<IconOrbVariant, string> = {
  plain: 'bg-transparent',
  overlay: 'bg-scrim/45',
  surface: 'bg-surface-2',
}

const SIZE_CLASS: Record<IconOrbSize, string> = {
  md: 'size-11',
  lg: 'size-12',
}
</script>

<template>
  <button
    type="button"
    class="flex shrink-0 items-center justify-center rounded-pill text-ink"
    :class="[VARIANT_CLASS[variant], SIZE_CLASS[size]]"
    :aria-label="label"
    :aria-pressed="pressed ?? undefined"
    @click="emit('click')"
  >
    <slot />
  </button>
</template>
