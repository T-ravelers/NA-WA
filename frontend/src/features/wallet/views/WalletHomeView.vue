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

import { NormalizedApiError } from '@/shared/api/apiError'
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

const walletQuery = useWalletHome()
const { data, isPending } = walletQuery

const wallet = computed(() => (data.value === undefined ? null : toWalletHomeData(data.value)))

/*
 * 포맷터는 로케일에 따라 다시 만들어야 한다. 화면 진입 시점에 한 번 만들어 두면
 * 언어를 바꿔도 숫자와 날짜만 이전 로케일에 남는다.
 *
 * 표시 타임존도 KST로 고정한다. 서버 시각을 KST로 해석해 놓고 표시만 기기 시간대로
 * 하면, 해외 기기에서 같은 거래가 다른 날짜로 보인다.
 */
const amountFormat = computed(
  () => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }),
)

const dateFormat = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      month: 'short',
      day: 'numeric',
      timeZone: 'Asia/Seoul',
    }),
)

function formatPoints(amount: number): string {
  return t('wallet.home.points', { amount: amountFormat.value.format(amount) })
}

function formatDate(occurredAt: Date): string {
  return dateFormat.value.format(occurredAt)
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
  DEPOSIT_FORFEIT_DISTRIBUTION: IconAlertTriangle,
  REVERSAL: IconRotateClockwise,
  UNKNOWN: IconReceipt,
}

/*
 * 세 버튼의 상세 화면이 아직 없다. 누를 수 있게 두고 아무 일도 일어나지 않으면
 * 고장으로 읽히므로 비활성으로 두고, 이유를 화면에 적는다. 후속 작업에서 화면을 붙일 때
 * `disabled`만 떼면 된다.
 */
const quickActions = computed(() => [
  { id: 'topUp', label: t('wallet.home.quickActions.topUp'), variant: 'secondary' as const },
  { id: 'qr', label: t('wallet.home.quickActions.qr'), variant: 'secondary' as const },
  { id: 'settlement', label: t('wallet.home.quickActions.settlement'), variant: 'settle' as const },
])

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
        aria-describedby="wallet-actions-hint"
        class="mt-4 grid grid-cols-3 gap-2"
      >
        <AppButton
          v-for="action in quickActions"
          :key="action.id"
          :variant="action.variant"
          block
          compact
          disabled
        >
          {{ action.label }}
        </AppButton>
      </div>

      <p
        id="wallet-actions-hint"
        class="mt-2 text-caption text-ink-3"
      >
        {{ t('wallet.home.quickActions.comingSoon') }}
      </p>

      <div class="mt-10 flex items-end justify-between gap-4">
        <h2 class="font-display text-section-header text-ink-display uppercase">
          {{ t('wallet.home.recentActivity') }}
        </h2>
        <AppButton
          variant="tertiary"
          disabled
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
