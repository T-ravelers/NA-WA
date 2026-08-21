<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { vFitText } from '@/shared/lib/fitText'
import { spendingCategoryLabelKey } from '@/shared/lib/spendingCategory'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

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

// 서버가 저장한 값은 `FOOD` 같은 코드다. 그대로 찍으면 화면에 코드가 날것으로 보인다.
const formatSpendingCategory = (value: string | null | undefined): string =>
  value === null || value === undefined || value === ''
    ? t('wallet.transactionDetail.notAvailable')
    : t(spendingCategoryLabelKey(value))
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col px-screen pb-8 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('wallet.transactionDetail.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display"
      >
        {{ t('wallet.transactionDetail.title') }}
      </h1>
    </header>

    <section class="mt-5">
      <p
        v-if="!hasValidTransactionId"
        class="text-body-sm text-danger"
        role="alert"
      >
        {{ t('wallet.transactionDetail.error') }}
      </p>

      <StateLoading
        v-else-if="transactionQuery.isPending.value"
        :label="t('wallet.transactionDetail.loading')"
      />

      <StateError
        v-else-if="transactionQuery.isError.value || !transactionQuery.data.value"
        :description="t('wallet.transactionDetail.error')"
        :action-label="t('wallet.transactionDetail.retry')"
        @retry="transactionQuery.refetch()"
      />

      <template v-else>
        <AppCard class="text-center">
          <p class="text-body-sm text-ink-2">{{ t('wallet.transactionDetail.amount') }}</p>
          <p class="mt-2 text-data-xl text-ink">
            {{ formatAmount(transactionQuery.data.value.amount) }}
          </p>
          <p class="mt-2 text-body-sm text-ink-2">
            {{ getTransactionStatusLabel(transactionQuery.data.value.status) }}
          </p>
        </AppCard>

        <AppCard class="mt-4">
          <dl class="space-y-4 text-body-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.occurredAt') }}</dt>
              <dd class="text-right">
                {{ formatTransactionDateTime(transactionQuery.data.value.occurredAt) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.counterparty') }}</dt>
              <dd class="text-right">
                <span v-if="transactionQuery.data.value.counterparty">
                  {{ transactionQuery.data.value.counterparty.name }}
                  <span class="text-ink-2">
                    ({{ transactionQuery.data.value.counterparty.type }})
                  </span>
                </span>
                <span v-else>{{ t('wallet.transactionDetail.notAvailable') }}</span>
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">
                {{ t('wallet.transactionDetail.transactionNumber') }}
              </dt>
              <dd class="break-all text-right">
                {{ formatValue(transactionQuery.data.value.transactionNumber) }}
              </dd>
            </div>
          </dl>
        </AppCard>

        <AppCard
          v-if="transactionQuery.data.value.receipt"
          class="mt-4"
        >
          <h2 class="text-title-sm text-ink">{{ t('wallet.transactionDetail.receipt') }}</h2>
          <dl class="mt-4 space-y-4 text-body-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.memo') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.receipt.memo) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">
                {{ t('wallet.transactionDetail.spendingCategory') }}
              </dt>
              <dd class="text-right">
                {{ formatSpendingCategory(transactionQuery.data.value.receipt.spendingCategory) }}
              </dd>
            </div>
          </dl>
        </AppCard>

        <AppCard
          v-if="transactionQuery.data.value.fx"
          class="mt-4"
        >
          <h2 class="text-title-sm text-ink">{{ t('wallet.transactionDetail.exchange') }}</h2>
          <dl class="mt-4 space-y-4 text-body-sm">
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.sourceAmount') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.sourceAmount) }}
                {{ formatValue(transactionQuery.data.value.fx.sourceCurrency) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.displayCurrency') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.displayCurrency) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.exchangeRate') }}</dt>
              <dd class="text-right">
                {{ formatValue(transactionQuery.data.value.fx.exchangeRate) }}
              </dd>
            </div>
            <div class="flex items-start justify-between gap-4">
              <dt class="text-ink-2">{{ t('wallet.transactionDetail.ratedAt') }}</dt>
              <dd class="text-right">
                {{ formatTransactionDateTime(transactionQuery.data.value.fx.ratedAt) }}
              </dd>
            </div>
          </dl>
        </AppCard>
      </template>
    </section>
  </main>
</template>
