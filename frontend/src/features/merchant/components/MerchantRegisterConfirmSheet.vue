<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 가맹점 등록을 확정하기 전에 한 번 더 묻는 확인 모달(#340).
 *
 * 손님 → 가맹점 전환은 되돌리는 수단이 없다. 폼의 안내 문단은 읽지 않고 지나칠 수
 * 있어서, 입력한 상호명과 그 안내를 확정 버튼 바로 위에 다시 보여 준다.
 *
 * ESC 닫기·포커스 이동은 `shared/ui/InsufficientBalanceDialog`처럼 여기서 직접 한다.
 * 같은 일을 하는 `useOverlayDismiss`는 appointment feature 안에 있어 가져올 수 없다.
 */
interface Props {
  businessName: string
}

const { businessName } = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()

const { t } = useI18n()
const dialog = useTemplateRef('dialog')
let previousFocus: HTMLElement | null = null

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  window.addEventListener('keydown', handleKeydown)
  dialog.value?.querySelector<HTMLElement>('button:not([disabled])')?.focus()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  previousFocus?.focus()
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/70 px-screen"
    role="presentation"
    @click.self="emit('close')"
  >
    <section
      ref="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('merchant.register.confirmTitle')"
      class="w-full max-w-shell rounded-card bg-surface-1 p-5 shadow-sheet"
    >
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-title text-ink-display">{{ t('merchant.register.confirmTitle') }}</h2>
        <button
          type="button"
          class="text-title text-ink-2"
          :aria-label="t('merchant.register.confirmClose')"
          @click="emit('close')"
        >
          ×
        </button>
      </div>

      <p
        data-testid="merchant-confirm-name"
        class="mt-3 break-words text-body font-medium text-ink-display"
      >
        {{ businessName }}
      </p>
      <p class="mt-2 text-body-sm text-ink-3">
        {{ t('merchant.register.irreversible') }}
      </p>

      <div class="mt-5 grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary"
          @click="emit('close')"
        >
          {{ t('merchant.register.confirmCancel') }}
        </AppButton>
        <AppButton
          block
          @click="emit('confirm')"
        >
          {{ t('merchant.register.submit') }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
