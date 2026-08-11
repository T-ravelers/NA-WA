<script setup lang="ts">
import { IconLoader2 } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

/**
 * 공용 버튼.
 *
 * 도메인마다 버튼을 다시 만들면 높이·라운드·눌림 반응이 화면마다 달라진다.
 * 새 모양이 필요하면 여기에 variant를 추가하고, feature 안에서 버튼을 정의하지 않는다.
 *
 * 시안 실측: primary h52 · 나머지 h48 · 라운드 12.
 */
type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'settle'

interface Props {
  variant?: ButtonVariant
  /** 가로를 가득 채운다. 하단 고정 CTA는 항상 이쪽이다. */
  block?: boolean
  disabled?: boolean
  /**
   * 처리 중. 라벨을 스피너로 바꾸고 입력을 막는다.
   * 라벨은 화면에서만 사라지고 접근성 이름으로는 남는다.
   */
  loading?: boolean
  /** 폼 안에서 의도치 않게 submit되지 않도록 기본은 `button`이다. */
  type?: 'button' | 'submit'
  /**
   * 좌우 여백을 줄인다.
   *
   * 기본 여백(24px)은 한 줄을 채우는 CTA 기준이다. 지갑의 3분할 버튼처럼 좁은 칸에
   * 넣으면 라벨이 먼저 잘린다. 높이와 라운드는 그대로 두고 여백만 좁힌다.
   */
  compact?: boolean
  /** 카드 안의 짧은 동작 버튼에 사용하는 낮은 높이. */
  dense?: boolean
}

const {
  variant = 'primary',
  block = false,
  disabled = false,
  loading = false,
  type = 'button',
  compact = false,
  dense = false,
} = defineProps<Props>()

const emit = defineEmits<{ click: [] }>()

const { t } = useI18n()

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'rounded-sm bg-paper-fill text-on-paper',
  secondary: 'rounded-sm border border-hairline-strong bg-transparent text-ink',
  tertiary: 'text-ink underline underline-offset-4',
  settle: 'rounded-sm bg-settlement text-on-paper',
}

const HEIGHT_CLASS: Record<ButtonVariant, string> = {
  primary: 'h-13',
  secondary: 'h-12',
  tertiary: 'h-11',
  settle: 'h-12',
}

function handleClick(): void {
  // 로딩 중 연타는 백엔드에 같은 요청을 두 번 보낸다. 여기서 한 번 막는다.
  if (disabled || loading) {
    return
  }

  emit('click')
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
    class="relative inline-flex shrink-0 items-center justify-center text-title-sm transition-transform active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40"
    :class="[
      VARIANT_CLASS[variant],
      dense ? 'h-10' : HEIGHT_CLASS[variant],
      block ? 'w-full' : '',
      compact ? 'px-3' : 'px-6',
    ]"
    @click="handleClick"
  >
    <span :class="loading ? 'sr-only' : 'truncate'">
      <slot />
    </span>
    <IconLoader2
      v-if="loading"
      :size="20"
      :stroke-width="2"
      class="animate-spin"
      :aria-label="t('state.loading')"
    />
  </button>
</template>
