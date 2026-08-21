<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { formatCalendarDate } from '@/shared/lib/datetime'
import { vFitText } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { getTransactions } from '../api/walletApi'
import {
  activityLabelKey,
  formatPointAmount,
  formatTransactionAmount,
  formatTransactionDateTime,
  getTransactionStatusLabel,
  isOutgoingEntry,
  toActivityKind,
  walletKeys,
  type TransactionSearchParams,
  type TransactionStatus,
  type TransactionType,
  type WalletTransactionResponse,
} from '../model/walletHome'

const PAGE_SIZE = 20

const { t, locale } = useI18n()
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
  { value: 'DEPOSIT_NO_SHOW_DISTRIBUTION', labelKey: 'depositNoShowShare' },
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

/**
 * 달력에서 기간을 고른다.
 *
 * 첫 탭이 시작일, 두 번째 탭이 종료일이다. 이미 기간이 닫혀 있으면 새로 시작한다.
 * 시작일보다 이른 날짜를 고르면 두 값을 뒤집어 항상 from <= to를 유지한다.
 */
const selectDate = (date: string): void => {
  if (!formFrom.value || formTo.value) {
    formFrom.value = date
    formTo.value = ''
    return
  }

  if (date < formFrom.value) {
    formTo.value = formFrom.value
    formFrom.value = date
    return
  }

  formTo.value = date
}

const clearDates = (): void => {
  formFrom.value = ''
  formTo.value = ''
}

const dateRangeLabel = computed(() => {
  if (!formFrom.value) return t('wallet.transactions.anyDate')

  const from = formatCalendarDate(formFrom.value, locale.value)

  return formTo.value ? `${from} – ${formatCalendarDate(formTo.value, locale.value)}` : from
})

const applyFilters = (): void => {
  // 달력이 두 값을 뒤집어 주므로 지금은 여기에 걸리지 않는다. 날짜를 넣는 다른 경로가
  // 생겼을 때 잘못된 기간이 서버로 나가지 않게 남겨 둔다.
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

/** 정산은 낸 쪽과 받은 쪽을 갈라 부른다. 방향은 입출금 구분에만 있다. */
const transactionLabel = (transaction: WalletTransactionResponse): string =>
  t(
    activityLabelKey(
      toActivityKind(transaction.transferType),
      isOutgoingEntry(transaction.entryType),
    ),
  )

const openTransactionDetail = (transactionId: number): void => {
  void router.push({ name: 'wallet-transaction-detail', params: { transactionId } })
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col px-screen pb-8 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('wallet.transactions.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display"
      >
        {{ t('wallet.transactions.title') }}
      </h1>
    </header>

    <section class="mt-5">
      <AppCard>
        <form @submit.prevent="applyFilters">
          <h2 class="text-title-sm text-ink">{{ t('wallet.transactions.filterTitle') }}</h2>

          <div class="mt-4 grid grid-cols-2 gap-3">
            <!-- 폼 라벨은 보조 텍스트라 ink-2다. ink-3은 흐림·비활성 전용 — #332 참조. -->
            <label class="text-caption text-ink-2">
              {{ t('wallet.transactions.type') }}
              <select
                v-model="formType"
                class="mt-2 w-full rounded-sm border border-hairline-2 bg-surface-2 px-3 py-3 text-body-sm text-ink outline-none focus:border-ink"
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

            <label class="text-caption text-ink-2">
              {{ t('wallet.transactions.status') }}
              <select
                v-model="formStatus"
                class="mt-2 w-full rounded-sm border border-hairline-2 bg-surface-2 px-3 py-3 text-body-sm text-ink outline-none focus:border-ink"
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

            <div class="col-span-2">
              <p class="text-caption text-ink-2">{{ t('wallet.transactions.dateRange') }}</p>
              <p
                class="mt-1 text-body-sm text-ink"
                aria-live="polite"
              >
                {{ dateRangeLabel }}
              </p>
              <!--
                네이티브 <input type="date">를 쓰지 않는다. 표시 형식이 브라우저 UI 언어를
                따라 한국어 브라우저에서 `연도. 월. 일.`로 나오고 lang 속성으로 바꿀 수 없다.
              -->
              <CalendarGrid
                class="mt-3"
                :range-start="formFrom || null"
                :range-end="formTo || null"
                @select="selectDate"
              />
              <button
                v-if="formFrom || formTo"
                type="button"
                class="mt-2 text-caption text-ink-2 underline underline-offset-4"
                @click="clearDates"
              >
                {{ t('wallet.transactions.clearDates') }}
              </button>
            </div>
          </div>

          <p
            v-if="filterError"
            class="mt-3 text-body-sm text-danger"
            role="alert"
          >
            {{ filterError }}
          </p>

          <div class="mt-4 grid grid-cols-2 gap-3">
            <AppButton
              variant="secondary"
              @click="resetFilters"
            >
              {{ t('wallet.transactions.resetFilters') }}
            </AppButton>
            <AppButton type="submit">
              {{ t('wallet.transactions.applyFilters') }}
            </AppButton>
          </div>
        </form>
      </AppCard>
    </section>

    <section
      class="mt-6"
      aria-labelledby="transaction-list-title"
    >
      <h2
        id="transaction-list-title"
        class="font-display text-section-header uppercase text-ink-display"
      >
        {{ t('wallet.home.recentActivity') }}
      </h2>

      <StateLoading
        v-if="transactionsQuery.isPending.value && transactions.length === 0"
        class="mt-4"
        :label="t('wallet.transactions.loading')"
      />

      <StateError
        v-else-if="transactionsQuery.isError.value && transactions.length === 0"
        class="mt-4"
        :description="t('wallet.transactions.error')"
        :action-label="t('wallet.transactions.retry')"
        @retry="transactionsQuery.refetch()"
      />

      <StateEmpty
        v-else-if="transactions.length === 0"
        class="mt-4"
        :description="t('wallet.transactions.empty')"
      />

      <ul
        v-else
        class="mt-4 space-y-3"
      >
        <li
          v-for="transaction in transactions"
          :key="`${transaction.transferId}-${transaction.createdAt}`"
          class="rounded-md bg-surface-1"
        >
          <button
            type="button"
            class="w-full p-4 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ink"
            :aria-label="t('wallet.transactions.openDetail')"
            @click="openTransactionDetail(transaction.transferId)"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate text-body-sm font-semibold text-ink">
                  {{ transactionLabel(transaction) }}
                </p>
                <p class="mt-1 text-caption text-ink-3">
                  {{ formatTransactionDateTime(transaction.createdAt) }}
                </p>
              </div>
              <div class="shrink-0 text-right">
                <!-- 들어온 돈만 민트 계열로 구분한다. V2 거래내역/01의 Credit 표기. -->
                <p
                  class="text-title-sm font-bold"
                  :class="isOutgoingEntry(transaction.entryType) ? 'text-ink' : 'text-success-2'"
                >
                  {{ formatTransactionAmount(transaction) }}
                </p>
                <p
                  class="mt-1 text-caption"
                  :class="
                    isOutgoingEntry(transaction.entryType) ? 'text-ink-3' : 'text-success-subtle'
                  "
                >
                  {{
                    isOutgoingEntry(transaction.entryType)
                      ? t('wallet.transactions.debit')
                      : t('wallet.transactions.credit')
                  }}
                </p>
              </div>
            </div>

            <div
              class="mt-3 flex items-center justify-between border-t border-hairline pt-3 text-caption"
            >
              <span class="text-ink-2">
                {{ t('wallet.transactions.balanceAfter') }}
                <strong class="ml-1 font-semibold text-ink">
                  {{ formatPointAmount(String(transaction.balanceAfter)) }} P
                </strong>
              </span>
              <span class="flex items-center gap-1 font-semibold text-ink-2">
                {{ t('wallet.transactions.details') }}
                <span
                  class="text-title-sm leading-none"
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
        class="mt-3 text-body-sm text-danger"
        role="alert"
      >
        {{ t('wallet.transactions.error') }}
      </p>

      <AppButton
        v-if="nextCursor"
        variant="secondary"
        block
        class="mt-4"
        :disabled="isLoadingMore"
        @click="loadMore"
      >
        {{
          isLoadingMore ? t('wallet.transactions.loadingMore') : t('wallet.transactions.loadMore')
        }}
      </AppButton>
    </section>
  </main>
</template>
