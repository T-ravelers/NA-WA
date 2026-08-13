<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { IconChevronLeft } from '@tabler/icons-vue'
import QRCode from 'qrcode'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { listActiveQrPayments } from '../api/qrPaymentApi'
import { formatKrw, qrPaymentKeys, type QrPaymentCreateResponse } from '../model/qrPayment'
import { useWalletHome } from '../model/walletQueries'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const walletQuery = useWalletHome()

const activeQrQuery = useQuery({
  queryKey: qrPaymentKeys.active(),
  queryFn: listActiveQrPayments,
})

const isMyQrActive = computed(() => route.name === 'wallet-qr')

const activeQrList = computed(() => activeQrQuery.data.value ?? [])

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
    : formatKrw(amount)
})

const expiresAtLabel = computed(() => {
  const expiresAt = selectedQr.value?.expiresAt

  if (expiresAt === undefined) return null

  const formattedTime = new Intl.DateTimeFormat(locale.value, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(expiresAt))

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
                    qr.amount === null ? t('wallet.qr.amountEnteredByPayer') : formatKrw(qr.amount)
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
