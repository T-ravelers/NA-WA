<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getTransactionDetail } from '../api/walletApi'
import {
  formatPointAmount,
  formatTransactionDateTime,
  getTransactionStatusLabel,
  walletKeys,
} from '../model/walletHome'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const transactionId = computed(() => Number(route.params.transactionId))
const hasValidTransactionId = computed(
  () => Number.isInteger(transactionId.value) && transactionId.value > 0,
)

const transactionQuery = useQuery({
  queryKey: computed(() => walletKeys.transactionDetail(transactionId.value)),
  queryFn: () => getTransactionDetail(transactionId.value),
  enabled: hasValidTransactionId,
})

const goBack = (): void => {
  void router.push({ name: 'wallet-transactions' })
}

const formatAmount = (amount: string | number | null | undefined): string =>
  amount === null || amount === undefined
    ? t('wallet.transactionDetail.notAvailable')
    : `${formatPointAmount(String(amount))} P`

const formatValue = (value: string | number | null | undefined): string =>
  value === null || value === undefined || value === ''
    ? t('wallet.transactionDetail.notAvailable')
    : String(value)
</script>

<template>
  <main class="min-h-dvh bg-[#151515] px-4 pb-8 text-[#f5f4f0]">
    <header class="mx-auto flex max-w-[430px] items-center border-b border-[#2d2d2d] px-1 py-4">
      <button
        type="button"
        class="grid size-8 place-items-center text-2xl leading-none text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        :aria-label="t('wallet.transactionDetail.back')"
        @click="goBack"
      >
        ‹
      </button>
      <h1 class="flex-1 text-center text-lg font-bold tracking-[-0.03em]">
        {{ t('wallet.transactionDetail.title') }}
      </h1>
      <span
        class="size-8"
        aria-hidden="true"
      />
    </header>

    <section class="mx-auto max-w-[430px] pt-6">
      <p
        v-if="!hasValidTransactionId"
        class="rounded-2xl bg-[#3b2422] px-4 py-8 text-center text-sm text-[#ffaaa4]"
        role="alert"
      >
        {{ t('wallet.transactionDetail.error') }}
      </p>

      <p
        v-else-if="transactionQuery.isPending.value"
        class="rounded-2xl bg-[#262626] px-4 py-8 text-center text-sm text-[#aaa8a3]"
        role="status"
      >
        {{ t('wallet.transactionDetail.loading') }}
      </p>

      <div
        v-else-if="transactionQuery.isError.value || !transactionQuery.data.value"
        class="flex flex-col items-center gap-3 rounded-2xl bg-[#3b2422] px-4 py-8 text-center text-sm text-[#ffaaa4]"
        role="alert"
      >
        <p>{{ t('wallet.transactionDetail.error') }}</p>
        <button
          type="button"
          class="rounded-xl border border-[#ffaaa4] px-4 py-2 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          @click="transactionQuery.refetch()"
        >
          {{ t('wallet.transactionDetail.retry') }}
        </button>
      </div>

      <template v-else>
        <section class="rounded-[22px] bg-[#1d1d1b] p-5 text-center">
          <p class="text-xs text-[#aaa8a3]">{{ t('wallet.transactionDetail.amount') }}</p>
          <p class="mt-2 text-3xl font-extrabold tracking-[-0.04em]">
            {{ formatAmount(transactionQuery.data.value.amount) }}
          </p>
          <p class="mt-2 text-sm text-[#aaa8a3]">
            {{ getTransactionStatusLabel(transactionQuery.data.value.status) }}
          </p>
        </section>

        <section class="mt-4 rounded-[22px] bg-[#1d1d1b] p-5">
          <dl class="space-y-4 text-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.occurredAt') }}</dt>
              <dd class="text-right">
                {{ formatTransactionDateTime(transactionQuery.data.value.occurredAt) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.counterparty') }}</dt>
              <dd class="text-right">
                <span v-if="transactionQuery.data.value.counterparty">
                  {{ transactionQuery.data.value.counterparty.name }}
                  <span class="text-[#aaa8a3]">
                    ({{ transactionQuery.data.value.counterparty.type }})
                  </span>
                </span>
                <span v-else>{{ t('wallet.transactionDetail.notAvailable') }}</span>
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">
                {{ t('wallet.transactionDetail.transactionNumber') }}
              </dt>
              <dd class="break-all text-right">
                {{ formatValue(transactionQuery.data.value.transactionNumber) }}
              </dd>
            </div>
          </dl>
        </section>

        <section
          v-if="transactionQuery.data.value.receipt"
          class="mt-4 rounded-[22px] bg-[#1d1d1b] p-5"
        >
          <h2 class="text-sm font-bold">{{ t('wallet.transactionDetail.receipt') }}</h2>
          <dl class="mt-4 space-y-4 text-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.memo') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.receipt.memo) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">
                {{ t('wallet.transactionDetail.spendingCategory') }}
              </dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.receipt.spendingCategory) }}
              </dd>
            </div>
          </dl>
        </section>

        <section
          v-if="transactionQuery.data.value.fx"
          class="mt-4 rounded-[22px] bg-[#1d1d1b] p-5"
        >
          <h2 class="text-sm font-bold">{{ t('wallet.transactionDetail.exchange') }}</h2>
          <dl class="mt-4 space-y-4 text-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.sourceAmount') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.sourceAmount) }}
                {{ formatValue(transactionQuery.data.value.fx.sourceCurrency) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.displayCurrency') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.displayCurrency) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.exchangeRate') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.exchangeRate) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-[#aaa8a3]">{{ t('wallet.transactionDetail.ratedAt') }}</dt>
              <dd class="text-right">
                {{ formatTransactionDateTime(transactionQuery.data.value.fx.ratedAt) }}
              </dd>
            </div>
          </dl>
        </section>
      </template>
    </section>
  </main>
</template>
