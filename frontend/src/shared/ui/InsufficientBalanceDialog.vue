<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'

import AppButton from './AppButton.vue'

/**
 * "잔액이 모자랍니다 — 채우러 갈까요?"를 묻는 팝업.
 *
 * 지갑에서 돈이 빠져나가는 자리는 여럿인데(약속 보증금, 정산 결제) 서버가 거절하는 이유는
 * 같다. 화면마다 이 팝업을 다시 그리면 같은 실패가 자리에 따라 다르게 보이고, 실제로
 * 그렇게 됐다 — 두 벌 복제본 중 어느 쪽도 Escape로 닫히지 않았고 열려도 포커스가 팝업
 * 안으로 들어오지 않았다. 그래서 마크업 대신 이 컴포넌트를 쓴다.
 *
 * 문구는 부르는 쪽이 넘긴다. "무엇을 하려다 모자랐는지"는 화면마다 다르기 때문이다.
 */
interface Props {
  title: string
  description: string
  laterLabel: string
  topupLabel: string
  /**
   * 팝업이 서는 자리. 화면 한가운데(`center`)가 기본이고, 아래에서 올라오는 시트처럼
   * 보여야 하는 화면만 `bottom`을 준다.
   */
  placement?: 'center' | 'bottom'
}

const { placement = 'center' } = defineProps<Props>()

const emit = defineEmits<{ close: []; topup: [] }>()

const dialog = useTemplateRef('dialog')
let previousFocus: HTMLElement | null = null

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

// 열릴 때 포커스를 팝업 안으로 들여보내고, 닫힐 때 원래 있던 곳으로 돌려준다.
// 이것이 없으면 키보드·스크린리더 사용자는 팝업이 떴다는 사실조차 알기 어렵고,
// 닫은 뒤에도 화면 맨 위에서 다시 짚어 내려와야 한다.
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
    class="fixed inset-0 z-50 flex justify-center bg-scrim/70 px-screen"
    :class="placement === 'bottom' ? 'items-end pb-6' : 'items-center'"
    role="presentation"
    @click.self="emit('close')"
  >
    <section
      ref="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="w-full max-w-shell rounded-card bg-surface-1 p-5 shadow-sheet"
    >
      <h2 class="text-title text-ink-display">{{ title }}</h2>
      <p class="mt-2 text-body-sm text-ink-3">{{ description }}</p>
      <div class="mt-5 grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary"
          @click="emit('close')"
        >
          {{ laterLabel }}
        </AppButton>
        <AppButton
          block
          @click="emit('topup')"
        >
          {{ topupLabel }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
