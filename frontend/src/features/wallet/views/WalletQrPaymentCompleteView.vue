<script setup lang="ts">
import { IconCheck } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'

import { ACTIVE_APPOINTMENTS, formatKrw, QR_PAYMENT_PREVIEW } from '../model/qrPayment'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const selectedAppointment = computed(() => {
  const appointmentId = route.query.appointment

  return ACTIVE_APPOINTMENTS.find((appointment) => appointment.id === appointmentId)
})

const hasValidExpenseContext = computed(() => {
  if (route.query.scope === 'personal') return true

  return route.query.scope === 'shared' && selectedAppointment.value !== undefined
})

const expenseLabel = computed(() => {
  if (route.query.scope === 'shared' && selectedAppointment.value) {
    return t('wallet.qrPayment.sharedExpense', {
      appointment: selectedAppointment.value.name,
    })
  }

  return t('wallet.qrPayment.personalExpense')
})

const backToWallet = (): void => {
  void router.push({ name: 'wallet' })
}

const backToPreview = (): void => {
  void router.push({ name: 'wallet-qr-payment-preview' })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <section
      class="flex min-h-dvh flex-col items-center text-center"
      aria-labelledby="wallet-qr-payment-complete-heading"
    >
      <div
        v-if="hasValidExpenseContext"
        class="flex flex-1 flex-col items-center justify-center pt-12"
      >
        <div
          class="grid size-16 place-items-center rounded-full border-2 border-hairline-strong"
          role="img"
          :aria-label="t('wallet.qrPayment.completeIconLabel')"
        >
          <IconCheck
            :size="32"
            :stroke-width="2"
            aria-hidden="true"
          />
        </div>

        <h1
          id="wallet-qr-payment-complete-heading"
          class="mt-5 text-title font-bold"
        >
          {{ t('wallet.qrPayment.completeTitle') }}
        </h1>
        <p class="mt-4 text-data-lg font-bold text-danger">
          -{{ formatKrw(QR_PAYMENT_PREVIEW.amount) }}
        </p>
        <p class="mt-3 text-body-sm text-ink-2">
          {{
            t('wallet.qrPayment.remainingBalance', {
              amount: formatKrw(QR_PAYMENT_PREVIEW.balanceAfter),
            })
          }}
        </p>
        <p class="mt-6 rounded-pill bg-surface-2 px-4 py-2 text-caption text-ink-2">
          {{ expenseLabel }}
        </p>
      </div>

      <AppButton
        v-if="hasValidExpenseContext"
        block
        class="mb-1"
        @click="backToWallet"
      >
        {{ t('wallet.qrPayment.backToWallet') }}
      </AppButton>

      <div
        v-else
        class="flex flex-1 flex-col items-center justify-center px-6"
      >
        <h1
          id="wallet-qr-payment-complete-heading"
          class="text-title font-bold"
        >
          {{ t('wallet.qrPayment.invalidContextTitle') }}
        </h1>
        <p class="mt-4 max-w-sm text-body-sm text-ink-2">
          {{ t('wallet.qrPayment.invalidContextDescription') }}
        </p>
      </div>

      <AppButton
        v-if="!hasValidExpenseContext"
        block
        class="mb-1"
        @click="backToPreview"
      >
        {{ t('wallet.qrPayment.backToPreview') }}
      </AppButton>
    </section>
  </main>
</template>
