<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

import { useOverlayDismiss } from '../composables/useOverlayDismiss'

/**
 * 지금은 나갈 수 없다고 알리는 모달.
 *
 * 나가기 버튼은 어떤 상태에서도 눌린다. 비활성 버튼은 모바일에서(hover가 없다)
 * 왜 안 되는지 말해 줄 자리가 없어 "눌리기는 하는데 아무 일도 없는 버튼"이 되고,
 * 이유를 회원 이름 옆에 상시로 적어 두면 목록이 안내문으로 채워진다. 그래서 이유는
 * 누른 자리에서 한 번만 말한다.
 */
interface Props {
  reason: string
}

const { reason } = defineProps<Props>()

const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const dialog = useTemplateRef('dialog')
useOverlayDismiss(dialog, () => emit('close'))
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
      :aria-label="t('appointment.members.leaveBlockedTitle')"
      class="w-full max-w-shell rounded-card bg-paper p-5 text-on-paper shadow-sheet"
    >
      <h2 class="text-title text-on-paper">{{ t('appointment.members.leaveBlockedTitle') }}</h2>
      <p class="mt-3 text-body-sm text-on-paper/70">{{ reason }}</p>

      <div class="mt-5">
        <!-- paper 카드 위라 채움(paper-fill)은 배경과 대비가 1.02로 사라진다.
             테두리와 어두운 글자를 가진 secondary-on-paper를 쓴다. -->
        <AppButton
          block
          variant="secondary-on-paper"
          @click="emit('close')"
        >
          {{ t('action.close') }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
