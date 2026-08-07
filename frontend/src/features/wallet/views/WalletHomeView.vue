<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { getWalletHome } from '../api/walletApi'
import {
  formatPointAmount,
  getWalletStatusLabel,
  toWalletHomeData,
  walletKeys,
  type WalletActivityStatus,
} from '../model/walletHome'

type QuickAction = 'topUp' | 'qr' | 'settlement'

const { t } = useI18n()
const selectedAction = ref<QuickAction | null>(null)

const walletQuery = useQuery({
  queryKey: walletKeys.home(),
  queryFn: getWalletHome,
})

const walletHomeData = computed(() =>
  walletQuery.data.value ? toWalletHomeData(walletQuery.data.value) : null,
)

const formattedBalance = computed(() =>
  walletHomeData.value ? formatPointAmount(walletHomeData.value.balance) : '',
)

const quickActions: Array<{ id: QuickAction; label: string }> = [
  { id: 'topUp', label: t('wallet.home.quickActions.topUp') },
  { id: 'qr', label: t('wallet.home.quickActions.qr') },
  { id: 'settlement', label: t('wallet.home.quickActions.settlement') },
]

const getActivityStatusLabel = (status: WalletActivityStatus): string =>
  status === 'available' ? t('wallet.home.availableToSettle') : t('wallet.home.settled')

const selectQuickAction = (action: QuickAction): void => {
  selectedAction.value = action
}
</script>

<template>
  <main class="min-h-dvh bg-[#151515] px-4 py-12 text-[#f5f4f0] sm:py-16">
    <section
      v-if="walletQuery.isPending.value"
      class="mx-auto flex min-h-dvh max-w-[430px] items-center justify-center"
      role="status"
    >
      {{ t('wallet.home.loading') }}
    </section>

    <section
      v-else-if="walletQuery.isError.value"
      class="mx-auto flex min-h-dvh max-w-[430px] flex-col items-center justify-center gap-4 text-center"
      role="alert"
    >
      <p>{{ t('wallet.home.error') }}</p>
      <button
        type="button"
        class="rounded-xl border border-[#878787] px-5 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        @click="walletQuery.refetch()"
      >
        {{ t('wallet.home.retry') }}
      </button>
    </section>

    <section
      v-else-if="walletHomeData"
      class="mx-auto flex min-h-[calc(100dvh-6rem)] max-w-[430px] flex-col sm:min-h-[calc(100dvh-8rem)]"
      aria-labelledby="wallet-title"
    >
      <header class="px-3">
        <h1
          id="wallet-title"
          class="text-[38px] font-black uppercase leading-none tracking-[-0.07em]"
        >
          {{ t('wallet.home.title') }}
        </h1>
      </header>

      <section
        class="mt-7"
        :aria-label="walletHomeData.accountName"
      >
        <div class="relative pt-1">
          <div
            class="absolute inset-x-3 top-0 h-8 rounded-t-[32px] bg-gradient-to-r from-[#7bbaff] to-[#e9c5f6]"
            aria-hidden="true"
          />
          <article class="relative min-h-[158px] rounded-[31px] bg-[#f2f0ea] p-6 text-[#172033]">
            <div class="flex items-start justify-between gap-4">
              <p class="text-xs font-semibold tracking-[-0.02em]">
                {{ walletHomeData.accountName }}
              </p>
              <span
                class="rounded-full bg-[#91cdbb] px-3 py-1 text-[10px] font-semibold text-[#236451]"
              >
                {{ getWalletStatusLabel(walletHomeData.status) }}
              </span>
            </div>

            <p class="mt-2 text-[36px] font-extrabold leading-none tracking-[-0.04em]">
              {{ formattedBalance }} P
            </p>
            <p class="mt-4 text-xs text-[#aaa8a3]">
              {{ t('wallet.home.balanceLabel') }}
            </p>
          </article>
        </div>
      </section>

      <section
        class="mt-6 grid grid-cols-3 gap-2"
        :aria-label="t('wallet.home.quickActions.topUp')"
      >
        <button
          v-for="action in quickActions"
          :key="action.id"
          type="button"
          class="min-h-13 rounded-2xl px-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          :class="
            action.id === 'settlement'
              ? 'border border-[#f2f0ea] bg-[#f2f0ea] text-[#172033]'
              : 'border border-[#878787] text-[#f5f4f0] hover:border-[#f5f4f0]'
          "
          @click="selectQuickAction(action.id)"
        >
          {{ action.label }}
        </button>
      </section>

      <p
        class="sr-only"
        aria-live="polite"
      >
        {{
          selectedAction
            ? t('wallet.home.actionAnnouncement', {
                action: t(`wallet.home.quickActions.${selectedAction}`),
              })
            : ''
        }}
      </p>

      <section class="mt-11 flex-1">
        <div class="flex items-end justify-between gap-4">
          <h2 class="text-[22px] font-bold tracking-[-0.04em]">
            {{ t('wallet.home.recentActivity') }}
          </h2>
          <button
            type="button"
            class="pb-0.5 text-xs text-[#aaa8a3] underline-offset-4 hover:text-[#f5f4f0] hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          >
            {{ t('wallet.home.viewAll') }}
          </button>
        </div>

        <ul
          v-if="walletHomeData.activities.length > 0"
          class="mt-4 space-y-4"
        >
          <li
            v-for="activity in walletHomeData.activities"
            :key="activity.id"
            class="flex items-center gap-3 rounded-2xl bg-[#262626] px-4 py-4"
          >
            <span
              class="grid size-11 shrink-0 place-items-center rounded-full text-xl font-medium text-white"
              :style="{ backgroundColor: activity.color }"
              aria-hidden="true"
            >
              {{ activity.initial }}
            </span>

            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-semibold text-[#f5f4f0]">
                {{ activity.title }}
              </p>
              <p class="mt-1 truncate text-xs text-[#989898]">
                {{ activity.meta }}
              </p>
            </div>

            <div class="shrink-0 text-right">
              <p class="text-base font-semibold tracking-[-0.03em] text-[#f5f4f0]">
                {{ formatPointAmount(activity.amount) }} P
              </p>
              <p class="mt-1 text-xs text-[#47b99b]">
                {{ getActivityStatusLabel(activity.status) }}
              </p>
            </div>
          </li>
        </ul>

        <p
          v-else
          class="mt-4 rounded-2xl bg-[#262626] px-4 py-8 text-center text-sm text-[#989898]"
        >
          {{ t('wallet.home.emptyActivity') }}
        </p>
      </section>
    </section>
  </main>
</template>
