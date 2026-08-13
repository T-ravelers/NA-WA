<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import { MAX_QR_PAYMENT_AMOUNT } from '../model/qrPayment'
import { useQrRequestDraftStore } from '../model/qrRequestDraft'

const { t } = useI18n()
const router = useRouter()
const qrRequestDraft = useQrRequestDraftStore()

const amount = ref<number | null>(18_500)
const memo = ref('Seoul Night Tour')
const payerEntersAmount = ref(false)

const isValidFixedAmount = (value: number | null): value is number =>
  value !== null &&
  Number.isFinite(value) &&
  Number.isSafeInteger(value) &&
  value > 0 &&
  value <= MAX_QR_PAYMENT_AMOUNT

const canCreate = computed(() => payerEntersAmount.value || isValidFixedAmount(amount.value))

const goBack = (): void => {
  void router.push({ name: 'wallet' })
}

/** API 연동 전까지는 입력값을 세션 메모리 상태로 넘기는 로컬 목업 흐름으로 연결한다. */
const createMockQr = (): void => {
  if (!canCreate.value) return

  qrRequestDraft.setDraft({
    amount: payerEntersAmount.value ? null : amount.value,
    memo: memo.value.trim(),
    payerEntersAmount: payerEntersAmount.value,
  })

  void router.push({ name: 'wallet-qr' })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <header class="flex items-center border-b border-hairline py-4">
      <button
        type="button"
        class="grid size-11 place-items-center rounded-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        :aria-label="t('wallet.qrCreate.back')"
        @click="goBack"
      >
        <IconChevronLeft
          :size="22"
          :stroke-width="2"
          aria-hidden="true"
        />
      </button>
      <h1 class="flex-1 text-center text-title font-bold tracking-[-0.03em]">
        {{ t('wallet.qrCreate.title') }}
      </h1>
      <span
        class="size-11"
        aria-hidden="true"
      />
    </header>

    <section
      class="space-y-4 pt-6"
      aria-labelledby="wallet-qr-create-heading"
    >
      <h2
        id="wallet-qr-create-heading"
        class="sr-only"
      >
        {{ t('wallet.qrCreate.heading') }}
      </h2>

      <AppCard padding="lg">
        <h3 class="text-title-sm">{{ t('wallet.qrCreate.heading') }}</h3>
        <p class="mt-1 text-caption text-ink-3">
          {{ t('wallet.qrCreate.description') }}
        </p>

        <div class="mt-5">
          <AmountInput
            v-if="!payerEntersAmount"
            v-model="amount"
            :label="t('wallet.qrCreate.amount')"
            :helper="t('wallet.qrCreate.amountHelper')"
          />

          <div
            v-else
            class="rounded-sm border border-hairline bg-surface-2 px-4 py-3"
            role="status"
          >
            <p class="text-body-sm font-semibold">{{ t('wallet.qrCreate.payerAmountTitle') }}</p>
            <p class="mt-1 text-caption text-ink-3">
              {{ t('wallet.qrCreate.payerAmountDescription') }}
            </p>
          </div>
        </div>

        <label
          class="mt-4 flex cursor-pointer items-start gap-3 rounded-sm border border-hairline px-3 py-3 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-ink"
        >
          <input
            v-model="payerEntersAmount"
            type="checkbox"
            class="mt-0.5 size-4 accent-current"
          />
          <span>
            <span class="block text-body-sm font-semibold">
              {{ t('wallet.qrCreate.payerEntersAmount') }}
            </span>
            <span class="mt-1 block text-caption text-ink-3">
              {{ t('wallet.qrCreate.payerEntersAmountHelper') }}
            </span>
          </span>
        </label>

        <TextInput
          v-model="memo"
          class="mt-5"
          :label="t('wallet.qrCreate.memo')"
          :placeholder="t('wallet.qrCreate.memoPlaceholder')"
          :helper="t('wallet.qrCreate.memoHelper')"
        />
      </AppCard>

      <p class="rounded-xs bg-surface-2 px-3 py-2 text-caption text-ink-2">
        {{ t('wallet.qrCreate.mockNotice') }}
      </p>

      <AppButton
        block
        :disabled="!canCreate"
        @click="createMockQr"
      >
        {{ t('wallet.qrCreate.create') }}
      </AppButton>
    </section>
  </main>
</template>
