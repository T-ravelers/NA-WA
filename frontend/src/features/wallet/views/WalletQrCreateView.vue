<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import { createPaymentQr } from '../api/qrPaymentApi'
import { isValidQrPaymentAmount, qrPaymentKeys } from '../model/qrPayment'

const i18n = useI18n()
const { t } = i18n
const router = useRouter()
const queryClient = useQueryClient()

const amount = ref<number | null>(18_500)
const memo = ref('Seoul Night Tour')
const payerEntersAmount = ref(false)

const createMutation = useMutation({ mutationFn: createPaymentQr })

const canCreate = computed(
  () =>
    (payerEntersAmount.value || isValidQrPaymentAmount(amount.value)) &&
    !createMutation.isPending.value,
)

const errorMessage = computed(() => {
  const error = createMutation.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('wallet.qrCreate.createError')
  }

  return t(error.messageKey)
})

const goBack = (): void => {
  void router.push({ name: 'wallet' })
}

const createQr = (): void => {
  if (!canCreate.value) return

  const trimmedMemo = memo.value.trim()

  createMutation.mutate(
    {
      amount: payerEntersAmount.value ? null : amount.value,
      memo: trimmedMemo === '' ? null : trimmedMemo,
    },
    {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: qrPaymentKeys.active() })
        void router.push({ name: 'wallet-qr' })
      },
    },
  )
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

      <p
        v-if="createMutation.isError.value"
        role="alert"
        class="rounded-sm bg-surface-3 px-3.5 py-3 text-body-sm text-ink-2"
      >
        {{ errorMessage }}
      </p>

      <AppButton
        block
        :disabled="!canCreate"
        @click="createQr"
      >
        {{
          createMutation.isPending.value
            ? t('wallet.qrCreate.creating')
            : t('wallet.qrCreate.create')
        }}
      </AppButton>
    </section>
  </main>
</template>
