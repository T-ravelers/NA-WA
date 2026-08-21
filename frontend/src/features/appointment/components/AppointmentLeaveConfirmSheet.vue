<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

import { useOverlayDismiss } from '../composables/useOverlayDismiss'

/**
 * 약속에서 나가기 전 마지막으로 묻는 확인 모달.
 *
 * 활동 시작 전에는 서버가 같은 트랜잭션에서 보증금을 지갑으로 환급한다
 * (HELD → REFUNDED). 활동이 시작된 뒤(noShow)에는 나가는 순간 노쇼로 굳어
 * 보증금이 몰수되고 출석한 회원에게 분배된다 — 어느 쪽이든 돈이 움직이는
 * 행동이라 결과를 먼저 보여주고 묻는다.
 */
interface Props {
  appointmentName: string
  depositAmount: string
  /** 활동이 시작된 뒤의 탈퇴 — 환급 대신 노쇼 몰수를 안내한다. */
  noShow?: boolean
  confirmDisabled?: boolean
  errorMessage?: string
}

const {
  appointmentName,
  depositAmount,
  noShow = false,
  confirmDisabled = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()

const { locale, t } = useI18n()
const dialog = useTemplateRef('dialog')
useOverlayDismiss(dialog, () => emit('close'))

function formatDeposit(value: string): string {
  const amount = Number(value)
  return Number.isFinite(amount) ? new Intl.NumberFormat(locale.value).format(amount) : value
}
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
      :aria-label="t('appointment.leave.title')"
      class="w-full max-w-[390px] rounded-card bg-paper p-5 text-on-paper shadow-sheet"
    >
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-title text-on-paper">{{ t('appointment.leave.title') }}</h2>
        <button
          type="button"
          class="text-title text-on-paper"
          :aria-label="t('appointment.leave.close')"
          @click="emit('close')"
        >
          ×
        </button>
      </div>

      <p class="mt-3 text-body-sm text-on-paper/70">
        {{ t('appointment.leave.description', { name: appointmentName }) }}
      </p>
      <p class="mt-2 text-body-sm text-on-paper/70">
        {{
          noShow
            ? t('appointment.leave.noShowForfeit', { amount: formatDeposit(depositAmount) })
            : t('appointment.leave.refund', { amount: formatDeposit(depositAmount) })
        }}
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
          variant="secondary"
          @click="emit('close')"
        >
          {{ t('appointment.leave.cancel') }}
        </AppButton>
        <AppButton
          block
          :loading="confirmDisabled"
          :disabled="confirmDisabled"
          @click="emit('confirm')"
        >
          {{ t('appointment.leave.confirm') }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
