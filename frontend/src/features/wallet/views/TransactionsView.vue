<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { getTransactions } from '../api/walletApi'
import {
  formatPointAmount,
  formatTransactionAmount,
  formatTransactionDateTime,
  getTransactionStatusLabel,
  getTransactionTypeLabel,
  walletKeys,
  type TransactionSearchParams,
  type TransactionStatus,
  type TransactionType,
  type WalletTransactionResponse,
} from '../model/walletHome'

const PAGE_SIZE = 20

const { t } = useI18n()
const router = useRouter()

const formType = ref<TransactionType | ''>('')
const formStatus = ref<TransactionStatus | ''>('')
const formFrom = ref('')
const formTo = ref('')
const appliedFilters = ref<TransactionSearchParams>({})
const cursor = ref<string | undefined>(undefined)
const transactions = ref<WalletTransactionResponse[]>([])
const nextCursor = ref<string | null>(null)
const filterError = ref('')

const typeOptions: Array<{ value: TransactionType; labelKey: string }> = [
  { value: 'TOPUP', labelKey: 'topUp' },
  { value: 'QR_PAYMENT', labelKey: 'qrPayment' },
  { value: 'SETTLEMENT', labelKey: 'settlement' },
  { value: 'DEPOSIT_HOLD', labelKey: 'depositHold' },
  { value: 'DEPOSIT_REFUND', labelKey: 'depositRefund' },
  { value: 'DEPOSIT_FORFEIT_DISTRIBUTION', labelKey: 'depositForfeiture' },
  { value: 'REVERSAL', labelKey: 'reversal' },
]

const statusOptions: TransactionStatus[] = [
  'PENDING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'REVERSED',
]

const transactionsQuery = useQuery({
  queryKey: computed(() => walletKeys.transactionList(appliedFilters.value, cursor.value)),
  queryFn: () =>
    getTransactions({
      ...appliedFilters.value,
      cursor: cursor.value,
      size: PAGE_SIZE,
    }),
})

watch(
  () => transactionsQuery.data.value,
  (response) => {
    if (!response) return

    transactions.value = cursor.value
      ? [...transactions.value, ...response.transactions]
      : response.transactions
    nextCursor.value = response.nextCursor
  },
  { immediate: true },
)

const isLoadingMore = computed(
  () => transactionsQuery.isFetching.value && transactions.value.length > 0,
)

const goBack = (): void => {
  void router?.push({ name: 'wallet' })
}

const applyFilters = (): void => {
  if (formFrom.value && formTo.value && formFrom.value > formTo.value) {
    filterError.value = t('wallet.transactions.dateError')
    return
  }

  filterError.value = ''
  appliedFilters.value = {
    ...(formType.value ? { type: formType.value } : {}),
    ...(formStatus.value ? { status: formStatus.value } : {}),
    ...(formFrom.value ? { from: formFrom.value } : {}),
    ...(formTo.value ? { to: formTo.value } : {}),
  }
  cursor.value = undefined
  transactions.value = []
  nextCursor.value = null
}

const resetFilters = (): void => {
  formType.value = ''
  formStatus.value = ''
  formFrom.value = ''
  formTo.value = ''
  applyFilters()
}

const loadMore = (): void => {
  if (!nextCursor.value || transactionsQuery.isFetching.value) return

  cursor.value = nextCursor.value
}

const openTransactionDetail = (transactionId: number): void => {
  void router.push({ name: 'wallet-transaction-detail', params: { transactionId } })
}
</script>

<template>
  <main class="min-h-dvh bg-[#151515] px-4 pb-8 text-[#f5f4f0]">
    <header class="mx-auto flex max-w-[430px] items-center border-b border-[#2d2d2d] px-1 py-4">
      <button
        type="button"
        class="grid size-8 place-items-center text-2xl leading-none text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        :aria-label="t('wallet.transactions.back')"
        @click="goBack"
      >
        ‹
      </button>
      <h1 class="flex-1 text-center text-lg font-bold tracking-[-0.03em]">
        {{ t('wallet.transactions.title') }}
      </h1>
      <span
        class="size-8"
        aria-hidden="true"
      />
    </header>

    <section class="mx-auto max-w-[430px] pt-4">
      <form
        class="rounded-[22px] bg-[#1d1d1b] p-4"
        @submit.prevent="applyFilters"
      >
        <h2 class="text-sm font-bold">{{ t('wallet.transactions.filterTitle') }}</h2>

        <div class="mt-4 grid grid-cols-2 gap-3">
          <label class="text-xs text-[#aaa8a3]">
            {{ t('wallet.transactions.type') }}
            <select
              v-model="formType"
              class="mt-2 w-full rounded-xl border border-[#353533] bg-[#292927] px-3 py-3 text-sm text-[#f5f4f0] outline-none focus:border-[#91cdbb]"
              :aria-label="t('wallet.transactions.type')"
            >
              <option value="">{{ t('wallet.transactions.allTypes') }}</option>
              <option
                v-for="option in typeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ t(`wallet.transactions.${option.labelKey}`) }}
              </option>
            </select>
          </label>

          <label class="text-xs text-[#aaa8a3]">
            {{ t('wallet.transactions.status') }}
            <select
              v-model="formStatus"
              class="mt-2 w-full rounded-xl border border-[#353533] bg-[#292927] px-3 py-3 text-sm text-[#f5f4f0] outline-none focus:border-[#91cdbb]"
              :aria-label="t('wallet.transactions.status')"
            >
              <option value="">{{ t('wallet.transactions.allStatuses') }}</option>
              <option
                v-for="status in statusOptions"
                :key="status"
                :value="status"
              >
                {{ getTransactionStatusLabel(status) }}
              </option>
            </select>
          </label>

          <label class="text-xs text-[#aaa8a3]">
            {{ t('wallet.transactions.from') }}
            <input
              v-model="formFrom"
              type="date"
              class="mt-2 w-full rounded-xl border border-[#353533] bg-[#292927] px-3 py-3 text-sm text-[#f5f4f0] outline-none focus:border-[#91cdbb]"
              :aria-label="t('wallet.transactions.from')"
            />
          </label>

          <label class="text-xs text-[#aaa8a3]">
            {{ t('wallet.transactions.to') }}
            <input
              v-model="formTo"
              type="date"
              class="mt-2 w-full rounded-xl border border-[#353533] bg-[#292927] px-3 py-3 text-sm text-[#f5f4f0] outline-none focus:border-[#91cdbb]"
              :aria-label="t('wallet.transactions.to')"
            />
          </label>
        </div>

        <p
          v-if="filterError"
          class="mt-3 rounded-lg bg-[#3b2422] px-3 py-2 text-xs text-[#ffaaa4]"
          role="alert"
        >
          {{ filterError }}
        </p>

        <div class="mt-4 grid grid-cols-2 gap-3">
          <button
            type="button"
            class="min-h-11 rounded-xl border border-[#5e5e5b] px-3 text-sm font-semibold text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
            @click="resetFilters"
          >
            {{ t('wallet.transactions.resetFilters') }}
          </button>
          <button
            type="submit"
            class="min-h-11 rounded-xl bg-[#f2f0ea] px-3 text-sm font-bold text-[#172033] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          >
            {{ t('wallet.transactions.applyFilters') }}
          </button>
        </div>
      </form>
    </section>

    <section
      class="mx-auto max-w-[430px] pt-6"
      aria-labelledby="transaction-list-title"
    >
      <h2
        id="transaction-list-title"
        class="text-xl font-bold tracking-[-0.04em]"
      >
        {{ t('wallet.home.recentActivity') }}
      </h2>

      <p
        v-if="transactionsQuery.isPending.value && transactions.length === 0"
        class="mt-4 rounded-2xl bg-[#262626] px-4 py-8 text-center text-sm text-[#aaa8a3]"
        role="status"
      >
        {{ t('wallet.transactions.loading') }}
      </p>

      <div
        v-else-if="transactionsQuery.isError.value && transactions.length === 0"
        class="mt-4 flex flex-col items-center gap-3 rounded-2xl bg-[#262626] px-4 py-8 text-center text-sm"
        role="alert"
      >
        <p>{{ t('wallet.transactions.error') }}</p>
        <button
          type="button"
          class="rounded-xl border border-[#878787] px-4 py-2 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          @click="transactionsQuery.refetch()"
        >
          {{ t('wallet.transactions.retry') }}
        </button>
      </div>

      <p
        v-else-if="transactions.length === 0"
        class="mt-4 rounded-2xl bg-[#262626] px-4 py-8 text-center text-sm text-[#989898]"
      >
        {{ t('wallet.transactions.empty') }}
      </p>

      <ul
        v-else
        class="mt-4 space-y-3"
      >
        <li
          v-for="transaction in transactions"
          :key="`${transaction.transferId}-${transaction.createdAt}`"
          class="rounded-2xl bg-[#262626]"
        >
          <button
            type="button"
            class="w-full p-4 text-left focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[#91cdbb]"
            :aria-label="t('wallet.transactions.openDetail')"
            @click="openTransactionDetail(transaction.transferId)"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold">
                  {{ getTransactionTypeLabel(transaction.transferType) }}
                </p>
                <p class="mt-1 text-xs text-[#989898]">
                  {{ formatTransactionDateTime(transaction.createdAt) }}
                </p>
              </div>
              <div class="shrink-0 text-right">
                <p
                  class="text-base font-bold"
                  :class="
                    transaction.entryType.toUpperCase() === 'DEBIT'
                      ? 'text-[#f5f4f0]'
                      : 'text-[#47c887]'
                  "
                >
                  {{ formatTransactionAmount(transaction) }}
                </p>
                <p class="mt-1 text-xs text-[#91cdbb]">
                  {{
                    transaction.entryType.toUpperCase() === 'DEBIT'
                      ? t('wallet.transactions.debit')
                      : t('wallet.transactions.credit')
                  }}
                </p>
              </div>
            </div>

            <div
              class="mt-3 flex items-center justify-between border-t border-[#353533] pt-3 text-xs"
            >
              <span class="text-[#aaa8a3]">
                {{ t('wallet.transactions.balanceAfter') }}
                <strong class="ml-1 font-semibold text-[#f5f4f0]">
                  {{ formatPointAmount(String(transaction.balanceAfter)) }} P
                </strong>
              </span>
              <span class="flex items-center gap-1 font-semibold text-[#aaa8a3]">
                {{ t('wallet.transactions.details') }}
                <span
                  class="text-lg leading-none"
                  aria-hidden="true"
                >
                  ›
                </span>
              </span>
            </div>
          </button>
        </li>
      </ul>

      <p
        v-if="transactionsQuery.isError.value && transactions.length > 0"
        class="mt-3 rounded-lg bg-[#3b2422] px-3 py-2 text-xs text-[#ffaaa4]"
        role="alert"
      >
        {{ t('wallet.transactions.error') }}
      </p>

      <button
        v-if="nextCursor"
        type="button"
        class="mt-4 min-h-12 w-full rounded-xl border border-[#5e5e5b] px-4 text-sm font-semibold text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb] disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="isLoadingMore"
        @click="loadMore"
      >
        {{
          isLoadingMore ? t('wallet.transactions.loadingMore') : t('wallet.transactions.loadMore')
        }}
      </button>
    </section>
  </main>
</template>
