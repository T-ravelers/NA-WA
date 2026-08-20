<script setup lang="ts">
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconArrowsExchange,
  IconLock,
  IconPlus,
  IconQrcode,
  IconReceipt,
  IconRotateClockwise,
} from '@tabler/icons-vue'
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { formatServerDateTime } from '@/shared/lib/datetime'
import { formatNumber } from '@/shared/lib/money'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { toWalletHomeData, type ActivityKind } from '../model/walletHome'
import { useWalletHome } from '../model/walletQueries'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()

const walletQuery = useWalletHome()
const { data, isPending } = walletQuery

const wallet = computed(() => (data.value === undefined ? null : toWalletHomeData(data.value)))

function formatPoints(amount: number): string {
  return t('wallet.home.points', {
    amount: formatNumber(amount, locale.value, { maximumFractionDigits: 2 }),
  })
}

function formatDate(occurredAt: Date): string {
  return formatServerDateTime(occurredAt, locale.value, {
    month: 'short',
    day: 'numeric',
  })
}

/**
 * 아이콘은 거래 종류에 붙는다. 목록 순서에 붙이면 새 거래가 하나 들어올 때마다
 * 기존 항목의 표식이 전부 밀려, 같은 거래가 매번 다르게 보인다.
 */
const ACTIVITY_ICON: Record<ActivityKind, Component> = {
  TOPUP: IconPlus,
  QR_PAYMENT: IconQrcode,
  SETTLEMENT: IconArrowsExchange,
  DEPOSIT_HOLD: IconLock,
  DEPOSIT_REFUND: IconArrowBackUp,
  DEPOSIT_NO_SHOW_DISTRIBUTION: IconAlertTriangle,
  REVERSAL: IconRotateClockwise,
  UNKNOWN: IconReceipt,
}

/* QR은 내 QR 화면으로, 정산은 정산 요청 목록으로 연결한다. */
const quickActions = computed(() => [
  {
    id: 'topUp',
    label: t('wallet.home.quickActions.topUp'),
    variant: 'secondary' as const,
    disabled: false,
  },
  {
    id: 'qr',
    label: t('wallet.home.quickActions.qr'),
    variant: 'secondary' as const,
    disabled: false,
  },
  {
    id: 'settlement',
    label: t('wallet.home.quickActions.settlement'),
    variant: 'settle' as const,
    disabled: false,
  },
])

function handleQuickAction(id: string): void {
  if (id === 'topUp') {
    void router.push({ name: 'wallet-top-up' })
  }

  if (id === 'qr') {
    void router.push({ name: 'wallet-qr' })
  }

  if (id === 'settlement') {
    void router.push({ name: 'settlements' })
  }
}

function openTransactions(): void {
  void router.push({ name: 'wallet-transactions' })
}

/** 실패 사유. 번역된 코드가 있을 때만 덧붙이고, 서버 message는 화면에 내지 않는다. */
const errorDescription = computed(() => {
  const error = walletQuery.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return undefined
  }

  return t(error.messageKey)
})
</script>

<template>
  <section class="px-screen pt-14 pb-8">
    <h1 class="font-display text-screen-title font-bold text-ink-display uppercase">
      {{ t('wallet.home.title') }}
    </h1>

    <StateLoading
      v-if="isPending"
      class="mt-8"
    />

    <template v-else-if="wallet !== null">
      <AppCard
        tone="paper"
        padding="lg"
        raised
        class="mt-6"
      >
        <div class="flex items-start justify-between gap-4">
          <p class="text-caption">{{ t('wallet.home.accountName') }}</p>
          <AppBadge tone="onPaper">{{ t(`wallet.home.status.${wallet.status}`) }}</AppBadge>
        </div>

        <p class="mt-3 text-data-xl">{{ formatPoints(wallet.balance) }}</p>
        <p class="mt-3 text-body-sm text-on-paper/70">{{ t('wallet.home.balanceLabel') }}</p>
      </AppCard>

      <div
        role="group"
        :aria-label="t('wallet.home.quickActions.label')"
        class="mt-4 grid grid-cols-3 gap-2"
      >
        <AppButton
          v-for="action in quickActions"
          :key="action.id"
          :variant="action.variant"
          block
          compact
          :disabled="action.disabled"
          @click="handleQuickAction(action.id)"
        >
          {{ action.label }}
        </AppButton>
      </div>

      <div class="mt-10 flex items-end justify-between gap-4">
        <h2 class="font-display text-section-header text-ink-display uppercase">
          {{ t('wallet.home.recentActivity') }}
        </h2>
        <AppButton
          variant="tertiary"
          @click="openTransactions"
        >
          {{ t('wallet.home.viewAll') }}
        </AppButton>
      </div>

      <ul
        v-if="wallet.activities.length > 0"
        class="mt-3 flex flex-col gap-2"
      >
        <li
          v-for="activity in wallet.activities"
          :key="activity.id"
        >
          <AppCard>
            <div class="flex items-center gap-3">
              <span
                aria-hidden="true"
                class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-2 text-ink-2"
              >
                <component
                  :is="ACTIVITY_ICON[activity.kind]"
                  :size="20"
                  :stroke-width="1.75"
                />
              </span>

              <div class="min-w-0 flex-1">
                <p class="truncate text-title-sm text-ink">
                  {{ t(`wallet.home.activity.${activity.kind}`) }}
                </p>
                <p
                  v-if="activity.occurredAt !== null"
                  class="mt-0.5 text-caption text-ink-3"
                >
                  {{ formatDate(activity.occurredAt) }}
                </p>
              </div>

              <div class="shrink-0 text-right">
                <p class="text-title-sm text-ink">{{ formatPoints(activity.signedAmount) }}</p>
                <p
                  class="mt-0.5 text-caption"
                  :class="activity.settled ? 'text-success' : 'text-settlement'"
                >
                  {{
                    activity.settled
                      ? t('wallet.home.activityStatus.settled')
                      : t('wallet.home.activityStatus.available')
                  }}
                </p>
              </div>
            </div>
          </AppCard>
        </li>
      </ul>

      <StateEmpty
        v-else
        :title="t('wallet.home.empty.title')"
        :description="t('wallet.home.empty.description')"
      />
    </template>

    <StateError
      v-else
      :description="errorDescription"
      @retry="walletQuery.refetch()"
    />
  </section>
</template>
