<script setup lang="ts">
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconArrowsExchange,
  IconBell,
  IconLock,
  IconPlus,
  IconQrcode,
  IconReceipt,
  IconRotateClockwise,
} from '@tabler/icons-vue'
import { animate, type AnimationPlaybackControls, useReducedMotion } from 'motion-v'
import { computed, onBeforeUnmount, ref, type Component, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { formatServerDateTime } from '@/shared/lib/datetime'
import { formatNumber } from '@/shared/lib/money'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { useWalletNotificationIntegration } from '../model/notificationIntegration'
import { activityLabelKey, toWalletHomeData, type ActivityKind } from '../model/walletHome'
import { useWalletHome } from '../model/walletQueries'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()

const walletQuery = useWalletHome()
const { data, isPending } = walletQuery

/*
 * 벨 배지.
 *
 * 개수를 숫자로만 두면 읽어 줄 이름이 없다. 배지 자체는 장식으로 감추고, 개수를 벨 버튼의
 * 이름에 실어 "3 unread"로 읽히게 한다. 색이나 점만으로 상태를 말하지 않는다는 규칙과 같다.
 */
const { useUnreadNotificationCount } = useWalletNotificationIntegration()
const unreadCountQuery = useUnreadNotificationCount()
const unreadCount = computed(() => unreadCountQuery.data.value ?? 0)
const unreadBadgeText = computed(() =>
  unreadCount.value > 9 ? t('notification.unreadBadgeOverflow') : String(unreadCount.value),
)
const bellLabel = computed(() =>
  unreadCount.value > 0
    ? t('notification.unreadBadge', { count: unreadCount.value })
    : t('notification.bell'),
)

function openNotifications(): void {
  void router.push({ name: 'notifications' })
}

const wallet = computed(() => (data.value === undefined ? null : toWalletHomeData(data.value)))
const walletBalance = computed(() => wallet.value?.balance ?? null)
const displayedBalance = ref<number | null>(null)
const reducedMotion = useReducedMotion()
let balanceAnimation: AnimationPlaybackControls | null = null

function stopBalanceAnimation(): void {
  balanceAnimation?.stop()
  balanceAnimation = null
}

/*
 * 첫 응답은 바로 읽히게 하고, 같은 화면을 보고 있는 동안 잔액이 바뀔 때만 보간한다.
 * 숫자 보간은 MotionConfig가 대신 줄여 주지 않으므로 감소 모션 설정을 여기서 직접 본다.
 */
watch(
  walletBalance,
  (next, previous) => {
    if (next === null) {
      return
    }

    if (previous === null || previous === undefined || previous === next || reducedMotion.value) {
      stopBalanceAnimation()
      displayedBalance.value = next
      return
    }

    const animationStart = displayedBalance.value ?? previous
    stopBalanceAnimation()
    displayedBalance.value = animationStart
    balanceAnimation = animate(animationStart, next, {
      duration: 0.6,
      ease: 'easeOut',
      onUpdate: (latest: number) => {
        displayedBalance.value = latest
      },
      onComplete: () => {
        displayedBalance.value = next
        balanceAnimation = null
      },
    })
  },
  { immediate: true },
)

watch(reducedMotion, (shouldReduce) => {
  if (!shouldReduce || walletBalance.value === null) {
    return
  }

  stopBalanceAnimation()
  displayedBalance.value = walletBalance.value
})

onBeforeUnmount(stopBalanceAnimation)

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
  <section class="flex px-screen flex-1 flex-col w-full pt-6 pb-8">
    <div class="flex items-center justify-between gap-3">
      <h1 class="min-w-0 font-display text-screen-title font-bold text-ink-display uppercase">
        {{ t('wallet.home.title') }}
      </h1>

      <div class="relative shrink-0">
        <IconOrb
          :label="bellLabel"
          variant="surface"
          @click="openNotifications"
        >
          <IconBell class="size-5" />
        </IconOrb>
        <span
          v-if="unreadCount > 0"
          aria-hidden="true"
          class="absolute -top-0.5 -right-0.5 flex min-w-5 items-center justify-center rounded-pill bg-ink px-1.5 text-caption text-canvas"
        >
          {{ unreadBadgeText }}
        </span>
      </div>
    </div>

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

        <p
          class="mt-3 text-data-xl"
          data-testid="wallet-balance"
        >
          {{ formatPoints(displayedBalance ?? wallet.balance) }}
        </p>
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
          :data-testid="`wallet-action-${action.id}`"
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
                class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-3 text-ink-2"
              >
                <component
                  :is="ACTIVITY_ICON[activity.kind]"
                  :size="20"
                  :stroke-width="1.75"
                />
              </span>

              <div class="min-w-0 flex-1">
                <p class="truncate text-title-sm text-ink">
                  {{ t(activityLabelKey(activity.kind, activity.outgoing)) }}
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
