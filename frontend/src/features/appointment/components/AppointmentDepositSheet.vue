<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

interface Props {
  appointmentName: string
  depositAmount: string
  confirmDisabled?: boolean
  errorMessage?: string
}

const {
  appointmentName,
  depositAmount,
  confirmDisabled = false,
  errorMessage = undefined,
} = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()

const { locale, t } = useI18n()

function formatDeposit(value: string): string {
  const amount = Number(value)
  return Number.isFinite(amount) ? new Intl.NumberFormat(locale.value).format(amount) : value
}
</script>

<template>
  <div class="fixed inset-0 z-40 flex items-center justify-center bg-scrim/70 px-screen">
    <button
      type="button"
      class="absolute inset-0"
      :aria-label="t('appointment.deposit.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('appointment.deposit.title')"
      class="relative z-10 w-full max-w-[390px] rounded-card bg-paper p-5 text-on-paper shadow-sheet"
    >
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-title text-on-paper">{{ t('appointment.deposit.title') }}</h2>
        <button
          type="button"
          class="text-title text-on-paper"
          :aria-label="t('appointment.deposit.close')"
          @click="emit('close')"
        >
          ×
        </button>
      </div>

      <p class="mt-3 text-body-sm text-on-paper/70">
        {{ t('appointment.deposit.description', { name: appointmentName }) }}
      </p>

      <div class="mt-4 rounded-sm bg-canvas p-4">
        <p class="text-caption text-settlement">{{ t('appointment.deposit.refundable') }}</p>
        <p class="mt-1 text-data-lg text-ink-display">₩{{ formatDeposit(depositAmount) }}</p>
      </div>

      <p class="mt-3 text-caption text-on-paper/60">
        {{ t('appointment.deposit.paymentPending') }}
      </p>

      <p
        v-if="errorMessage !== undefined"
        class="mt-3 text-body-sm text-danger"
      >
        {{ errorMessage }}
      </p>

      <div class="mt-5 grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary-on-paper"
          @click="emit('close')"
        >
          {{ t('appointment.deposit.cancel') }}
        </AppButton>
        <AppButton
          block
          variant="settle"
          :disabled="confirmDisabled"
          @click="emit('confirm')"
        >
          {{ t('appointment.deposit.confirm', { amount: formatDeposit(depositAmount) }) }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
