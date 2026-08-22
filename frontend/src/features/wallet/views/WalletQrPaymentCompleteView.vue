<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { IconCheck } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AppButton from '@/shared/ui/AppButton.vue'

import { getQrPaymentStatus } from '../api/qrPaymentApi'
import { formatPoints, qrPaymentKeys } from '../model/qrPayment'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()

const transferId = computed(() => {
  const raw = route.params.transferId
  const value = typeof raw === 'string' ? Number(raw) : NaN

  return Number.isSafeInteger(value) && value > 0 ? value : null
})

const statusQuery = useQuery({
  queryKey: computed(() => qrPaymentKeys.status(transferId.value ?? 0)),
  queryFn: () => getQrPaymentStatus(transferId.value ?? 0),
  enabled: computed(() => transferId.value !== null),
})

const statusErrorMessage = computed(() => {
  const error = statusQuery.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('wallet.qrPayment.statusError')
  }

  return t(error.messageKey)
})

const backToWallet = (): void => {
  void router.push({ name: 'wallet' })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <section
      class="flex min-h-dvh flex-col items-center text-center"
      aria-labelledby="wallet-qr-payment-complete-heading"
      aria-live="polite"
    >
      <template v-if="transferId === null">
        <div class="flex flex-1 flex-col items-center justify-center px-6">
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
          block
          class="mb-1"
          @click="backToWallet"
        >
          {{ t('wallet.qrPayment.backToWallet') }}
        </AppButton>
      </template>

      <template v-else-if="statusQuery.isPending.value">
        <div class="flex flex-1 flex-col items-center justify-center px-6">
          <h1
            id="wallet-qr-payment-complete-heading"
            class="sr-only"
          >
            {{ t('wallet.qrPayment.completeTitle') }}
          </h1>
          <p class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.loadingStatus') }}</p>
        </div>
      </template>

      <template v-else-if="statusQuery.isError.value">
        <div class="flex flex-1 flex-col items-center justify-center px-6">
          <h1
            id="wallet-qr-payment-complete-heading"
            class="text-title font-bold"
          >
            {{ t('wallet.qrPayment.statusErrorTitle') }}
          </h1>
          <p class="mt-4 max-w-sm text-body-sm text-ink-2">{{ statusErrorMessage }}</p>
        </div>
        <AppButton
          block
          class="mb-1"
          @click="backToWallet"
        >
          {{ t('wallet.qrPayment.backToWallet') }}
        </AppButton>
      </template>

      <template v-else-if="statusQuery.data.value">
        <div class="flex flex-1 flex-col items-center justify-center pt-12">
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
            -{{ formatPoints(statusQuery.data.value.amount) }}
          </p>
          <p class="mt-3 text-body-sm text-ink-2">
            {{
              t('wallet.qrPayment.remainingBalance', {
                amount: formatPoints(statusQuery.data.value.balanceAfter),
              })
            }}
          </p>
        </div>
        <AppButton
          block
          class="mb-1"
          @click="backToWallet"
        >
          {{ t('wallet.qrPayment.backToWallet') }}
        </AppButton>
      </template>
    </section>
  </main>
</template>
