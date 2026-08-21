<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { IconChevronLeft } from '@tabler/icons-vue'
import QRCode from 'qrcode'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { listActiveQrPayments } from '../api/qrPaymentApi'
import { formatPoints, qrPaymentKeys, type QrPaymentCreateResponse } from '../model/qrPayment'
import { parseServerDateTime } from '../model/walletHome'
import { useWalletHome } from '../model/walletQueries'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const walletQuery = useWalletHome()
const queryClient = useQueryClient()

const activeQrQuery = useQuery({
  queryKey: qrPaymentKeys.active(),
  queryFn: listActiveQrPayments,
})

const now = ref(Date.now())
const nowTimer = setInterval(() => {
  now.value = Date.now()
}, 1000)

let expiryRefetchTimer: ReturnType<typeof setTimeout> | null = null

/** 목록 중 가장 빨리 만료되는 QR 시점에 맞춰 재조회한다. 화면을 열어둔 채로 TTL이 지나도 만료된 QR이 남지 않게. */
function scheduleExpiryRefetch(list: QrPaymentCreateResponse[]): void {
  if (expiryRefetchTimer !== null) {
    clearTimeout(expiryRefetchTimer)
    expiryRefetchTimer = null
  }

  const expiryTimes = list
    .map((qr) => parseServerDateTime(qr.expiresAt))
    .filter((date): date is Date => date !== null)
    .map((date) => date.getTime())

  if (expiryTimes.length === 0) return

  const delay = Math.max(Math.min(...expiryTimes) - Date.now(), 0)

  expiryRefetchTimer = setTimeout(() => {
    void queryClient.invalidateQueries({ queryKey: qrPaymentKeys.active() })
  }, delay)
}

onUnmounted(() => {
  clearInterval(nowTimer)
  if (expiryRefetchTimer !== null) clearTimeout(expiryRefetchTimer)
})

const isMyQrActive = computed(() => route.name === 'wallet-qr')

/**
 * `now` 기준으로 만료된 QR을 걸러낸다. 재조회 타이머가 정확히 걸려도, 만료 경계에서
 * 서버가 같은 QR을 한 번 더 "아직 활성"으로 돌려주면 TanStack Query의 structural
 * sharing이 이전 데이터 참조를 그대로 유지해 재조회 스케줄이 다시 안 걸릴 수 있다.
 * 그 경우에도 화면은 이 필터로 정확하게 유지된다 — 재조회 성공 여부에 기대지 않는다.
 */
const activeQrList = computed(() =>
  (activeQrQuery.data.value ?? []).filter((qr) => {
    const expiresAt = parseServerDateTime(qr.expiresAt)

    return expiresAt === null || expiresAt.getTime() > now.value
  }),
)

// 재조회 스케줄은 원본 쿼리 데이터를 지켜본다. 화면 필터(activeQrList)는 매초 다시
// 계산되므로, 여기서 그걸 지켜보면 값이 안 바뀌어도 매초 타이머를 다시 걸게 된다.
watch(() => activeQrQuery.data.value ?? [], scheduleExpiryRefetch, { immediate: true })

/** 방금 만든 QR(목록의 맨 앞)을 기본으로 보여주고, 다른 항목을 탭하면 그걸로 바꾼다. */
const selectedQrToken = ref<string | null>(null)

const selectedQr = computed<QrPaymentCreateResponse | null>(() => {
  const list = activeQrList.value

  if (list.length === 0) return null

  return (
    list.find((qr) => qr.qrToken === selectedQrToken.value) ?? (list[0] as QrPaymentCreateResponse)
  )
})

const otherQrs = computed(() =>
  activeQrList.value.filter((qr) => qr.qrToken !== selectedQr.value?.qrToken),
)

const displayAmount = computed(() => {
  const amount = selectedQr.value?.amount

  return amount === undefined || amount === null
    ? t('wallet.qr.amountEnteredByPayer')
    : formatPoints(amount)
})

const expiresAtLabel = computed(() => {
  const expiresAt = selectedQr.value?.expiresAt

  if (expiresAt === undefined) return null

  const parsedExpiresAt = parseServerDateTime(expiresAt)

  if (parsedExpiresAt === null) return null

  const remainingSeconds = Math.max(0, Math.ceil((parsedExpiresAt.getTime() - now.value) / 1000))
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  const formattedTime = `${minutes}:${String(seconds).padStart(2, '0')}`

  return t('wallet.qr.validity', { time: formattedTime })
})

const balanceLabel = computed(() => {
  const balance = walletQuery.data.value?.balance

  if (typeof balance !== 'number') return t('wallet.qr.balanceUnavailable')

  const formattedBalance = new Intl.NumberFormat(locale.value, {
    maximumFractionDigits: 2,
  }).format(balance)

  return t('wallet.qr.balance', { amount: formattedBalance })
})

const qrImageSrc = ref<string | null>(null)

watch(
  () => selectedQr.value?.qrToken,
  (qrToken) => {
    if (qrToken === undefined) {
      qrImageSrc.value = null
      return
    }

    void QRCode.toDataURL(qrToken, { margin: 1, width: 320 }).then((dataUrl) => {
      qrImageSrc.value = dataUrl
    })
  },
  { immediate: true },
)

const selectQr = (qrToken: string): void => {
  selectedQrToken.value = qrToken
}

const goBack = (): void => {
  void router.push({ name: 'wallet' })
}

const createNewQr = (): void => {
  void router.push({ name: 'wallet-qr-create' })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <header class="flex items-center border-b border-hairline py-4">
      <button
        type="button"
        class="grid size-11 place-items-center rounded-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        :aria-label="t('wallet.qr.back')"
        @click="goBack"
      >
        <IconChevronLeft
          :size="22"
          :stroke-width="2"
          aria-hidden="true"
        />
      </button>
      <h1 class="flex-1 text-center text-title font-bold tracking-[-0.03em]">
        {{ t('wallet.qr.title') }}
      </h1>
      <span
        class="size-11"
        aria-hidden="true"
      />
    </header>

    <nav
      class="grid grid-cols-2 border-b border-hairline"
      :aria-label="t('wallet.qr.tabs.label')"
    >
      <RouterLink
        :to="{ name: 'wallet-qr' }"
        class="flex min-h-12 items-center justify-center border-b-2 px-3 text-body-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ink"
        :class="
          isMyQrActive
            ? 'border-ink font-semibold text-ink'
            : 'border-transparent text-ink-3 hover:text-ink'
        "
      >
        {{ t('wallet.qr.tabs.myQr') }}
      </RouterLink>
      <RouterLink
        :to="{ name: 'wallet-qr-scan' }"
        class="flex min-h-12 items-center justify-center border-b-2 border-transparent px-3 text-body-sm text-ink-3 transition-colors hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ink"
      >
        {{ t('wallet.qr.tabs.scan') }}
      </RouterLink>
    </nav>

    <section
      class="pt-6"
      aria-labelledby="wallet-qr-heading"
    >
      <h2
        id="wallet-qr-heading"
        class="sr-only"
      >
        {{ t('wallet.qr.heading') }}
      </h2>

      <StateLoading
        v-if="activeQrQuery.isPending.value"
        class="mt-4"
      />

      <StateError
        v-else-if="activeQrQuery.isError.value"
        :description="t('wallet.qr.listError')"
        @retry="activeQrQuery.refetch"
      />

      <template v-else>
        <AppCard
          v-if="selectedQr !== null"
          padding="lg"
          class="text-center"
        >
          <p class="text-body-sm text-ink-2">{{ t('wallet.qr.requestLabel') }}</p>

          <img
            v-if="qrImageSrc !== null"
            :src="qrImageSrc"
            :alt="t('wallet.qr.imageLabel')"
            class="mx-auto mt-4 size-52 rounded-sm bg-paper-fill p-3"
          />

          <p
            v-if="expiresAtLabel !== null"
            class="mt-4 text-body-sm font-semibold text-ink-2"
          >
            {{ expiresAtLabel }}
          </p>

          <dl class="mt-4 divide-y divide-hairline border-t border-hairline text-left">
            <div class="flex items-center justify-between gap-4 py-3">
              <dt class="text-body-sm text-ink-2">{{ t('wallet.qr.amount') }}</dt>
              <dd class="text-right text-body-sm font-semibold">{{ displayAmount }}</dd>
            </div>
            <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
              <dt class="text-body-sm text-ink-2">{{ t('wallet.qr.memo') }}</dt>
              <dd class="max-w-[65%] text-right text-body-sm font-semibold">
                {{ selectedQr.memo || t('wallet.qr.noMemo') }}
              </dd>
            </div>
          </dl>

          <p class="mt-3 text-caption text-ink-3">
            {{ balanceLabel }}
          </p>
        </AppCard>

        <StateEmpty
          v-else
          :title="t('wallet.qr.emptyTitle')"
          :description="t('wallet.qr.emptyDescription')"
          :action-label="t('wallet.qr.createNew')"
          @action="createNewQr"
        />

        <div
          v-if="otherQrs.length > 0"
          class="mt-3"
        >
          <h3 class="text-caption text-ink-3">{{ t('wallet.qr.otherActive') }}</h3>
          <ul class="mt-2 space-y-2">
            <li
              v-for="qr in otherQrs"
              :key="qr.qrToken"
            >
              <button
                type="button"
                class="flex w-full items-center justify-between gap-3 rounded-sm border border-hairline px-3 py-3 text-left transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
                @click="selectQr(qr.qrToken)"
              >
                <span class="text-body-sm font-semibold">
                  {{ qr.memo || t('wallet.qr.noMemo') }}
                </span>
                <span class="shrink-0 text-body-sm text-ink-2">
                  {{
                    qr.amount === null
                      ? t('wallet.qr.amountEnteredByPayer')
                      : formatPoints(qr.amount)
                  }}
                </span>
              </button>
            </li>
          </ul>
        </div>

        <AppButton
          v-if="selectedQr !== null"
          block
          variant="secondary"
          class="mt-3"
          @click="createNewQr"
        >
          {{ t('wallet.qr.createNew') }}
        </AppButton>
      </template>
    </section>
  </main>
</template>
