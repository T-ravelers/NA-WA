<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

import { useOverlayDismiss } from '../composables/useOverlayDismiss'

/**
 * 출석을 확정하기 전 마지막으로 묻는 확인 모달.
 *
 * 확정은 되돌리는 상태 전이가 없고, 미참석으로 넘어간 사람은 보증금을 잃는다.
 * 게다가 아직 손대지 않은 사람은 전부 미참석에서 출발하므로 "안 눌렀다"와 "안
 * 왔다"가 화면에서 같은 모양이다. 그래서 제출 직전에 숫자로 한 번 되짚는다.
 *
 * 나가기(환급)에는 확인 모달이 있는데 더 무거운 이쪽에 없으면, 안전장치가
 * 위험도와 반대로 붙는다.
 */
interface Props {
  attendedCount: number
  noShowCount: number
  /** 방장 자신이 미참석으로 남아 있는지. 자기 보증금을 잃는 경우다. */
  selfNoShow: boolean
  confirmDisabled?: boolean
  errorMessage?: string
}

const {
  attendedCount,
  noShowCount,
  selfNoShow,
  confirmDisabled = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()

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
      :aria-label="t('appointment.attendance.confirmSheet.title')"
      class="w-full max-w-[390px] rounded-card bg-paper p-5 text-on-paper shadow-sheet"
    >
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-title text-on-paper">
          {{ t('appointment.attendance.confirmSheet.title') }}
        </h2>
        <button
          type="button"
          class="text-title text-on-paper"
          :aria-label="t('appointment.attendance.confirmSheet.close')"
          @click="emit('close')"
        >
          ×
        </button>
      </div>

      <p class="mt-3 text-title-sm text-on-paper">
        {{
          t('appointment.attendance.confirmSheet.counts', {
            attended: attendedCount,
            noShow: noShowCount,
          })
        }}
      </p>
      <p class="mt-2 text-body-sm text-on-paper/70">
        {{ t('appointment.attendance.confirmSheet.forfeit') }}
      </p>
      <p class="mt-2 text-body-sm text-on-paper/70">
        {{ t('appointment.attendance.confirmSheet.irreversible') }}
      </p>

      <p
        v-if="selfNoShow"
        class="mt-3 text-body-sm text-danger"
      >
        {{ t('appointment.attendance.confirmSheet.selfNoShow') }}
      </p>

      <p
        v-if="errorMessage !== undefined"
        class="mt-3 text-caption text-danger"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <div class="mt-5 flex flex-col gap-2">
        <AppButton
          block
          variant="secondary-on-paper"
          @click="emit('close')"
        >
          {{ t('appointment.attendance.confirmSheet.cancel') }}
        </AppButton>
        <AppButton
          block
          :loading="confirmDisabled"
          :disabled="confirmDisabled"
          @click="emit('confirm')"
        >
          {{ t('appointment.attendance.confirmSheet.confirm') }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
