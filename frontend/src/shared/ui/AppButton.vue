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
}

const {
  variant = 'primary',
  block = false,
  disabled = false,
  loading = false,
  type = 'button',
} = defineProps<Props>()

const emit = defineEmits<{ click: [] }>()

const { t } = useI18n()

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'h-13 rounded-sm bg-paper-fill text-on-paper',
  secondary: 'h-12 rounded-sm border border-hairline-strong bg-transparent text-ink',
  tertiary: 'h-11 text-ink underline underline-offset-4',
  settle: 'h-12 rounded-sm bg-settlement text-on-paper',
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
    class="relative inline-flex shrink-0 items-center justify-center px-6 text-title-sm transition-transform active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40"
    :class="[VARIANT_CLASS[variant], block ? 'w-full' : '']"
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
