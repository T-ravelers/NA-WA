<script setup lang="ts">
import { IconChevronLeft, IconInfoCircle } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { formatKrw } from '../model/qrPayment'
import { useQrRequestDraftStore } from '../model/qrRequestDraft'
import { useWalletHome } from '../model/walletQueries'

const QR_SIZE = 21

/**
 * 실제 QR 토큰 API가 연결되기 전까지 화면의 형태를 확인할 수 있는 미리보기 매트릭스다.
 * 세 모서리의 파인더 패턴은 실제 QR과 같은 구조로 만들고, 나머지는 고정된 규칙으로
 * 채워서 새로고침할 때마다 모양이 바뀌지 않게 한다. API 연결 시 이 값만 서버 응답으로
 * 교체하면 된다.
 */
function createQrCells(): boolean[] {
  const cells = Array.from({ length: QR_SIZE * QR_SIZE }, () => false)
  const reserved = Array.from({ length: QR_SIZE * QR_SIZE }, () => false)
  const indexOf = (x: number, y: number): number => y * QR_SIZE + x

  const reserve = (x: number, y: number, active = false): void => {
    if (x < 0 || y < 0 || x >= QR_SIZE || y >= QR_SIZE) return

    const index = indexOf(x, y)
    reserved[index] = true
    cells[index] = active
  }

  const drawFinder = (originX: number, originY: number): void => {
    for (let y = -1; y <= 7; y += 1) {
      for (let x = -1; x <= 7; x += 1) {
        reserve(originX + x, originY + y)
      }
    }

    for (let y = 0; y < 7; y += 1) {
      for (let x = 0; x < 7; x += 1) {
        const isBorder = x === 0 || x === 6 || y === 0 || y === 6
        const isCore = x >= 2 && x <= 4 && y >= 2 && y <= 4
        reserve(originX + x, originY + y, isBorder || isCore)
      }
    }
  }

  drawFinder(0, 0)
  drawFinder(QR_SIZE - 7, 0)
  drawFinder(0, QR_SIZE - 7)

  for (let position = 8; position < QR_SIZE - 8; position += 1) {
    reserve(position, 6, position % 2 === 0)
    reserve(6, position, position % 2 === 0)
  }

  for (let y = 0; y < QR_SIZE; y += 1) {
    for (let x = 0; x < QR_SIZE; x += 1) {
      const index = indexOf(x, y)
      if (!reserved[index]) {
        cells[index] = (x * 17 + y * 31 + x * y + ((x + y) % 5)) % 7 < 3
      }
    }
  }

  return cells
}

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const walletQuery = useWalletHome()
const qrRequestDraft = useQrRequestDraftStore()
const qrCells = createQrCells()

const isMyQrActive = computed(() => route.name === 'wallet-qr')

const qrRequest = computed(() => {
  const draft = qrRequestDraft.draft
  const payerEntersAmount = draft?.payerEntersAmount ?? false

  return {
    amount: payerEntersAmount ? null : (draft?.amount ?? 18_500),
    memo: draft?.memo ?? 'Seoul Night Tour',
    payerEntersAmount,
  }
})

const displayAmount = computed(() =>
  qrRequest.value.payerEntersAmount || qrRequest.value.amount === null
    ? t('wallet.qr.amountEnteredByPayer')
    : formatKrw(qrRequest.value.amount),
)

const balanceLabel = computed(() => {
  const balance = walletQuery.data.value?.balance

  if (typeof balance !== 'number') return t('wallet.qr.balanceUnavailable')

  const formattedBalance = new Intl.NumberFormat(locale.value, {
    maximumFractionDigits: 2,
  }).format(balance)

  return t('wallet.qr.balance', { amount: formattedBalance })
})

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

      <AppCard
        padding="lg"
        class="text-center"
      >
        <p class="text-body-sm text-ink-2">{{ t('wallet.qr.requestLabel') }}</p>

        <div
          class="mx-auto mt-4 size-52 rounded-sm bg-paper-fill p-3"
          role="img"
          :aria-label="t('wallet.qr.imageLabel')"
        >
          <div
            class="grid size-full overflow-hidden"
            :style="{ gridTemplateColumns: `repeat(${QR_SIZE}, minmax(0, 1fr))` }"
            aria-hidden="true"
          >
            <span
              v-for="(active, index) in qrCells"
              :key="index"
              class="aspect-square"
              :class="active ? 'bg-canvas' : 'bg-paper-fill'"
            />
          </div>
        </div>

        <p class="mt-4 text-body-sm font-semibold text-ink-2">
          {{ t('wallet.qr.validity') }}
        </p>

        <dl class="mt-4 divide-y divide-hairline border-t border-hairline text-left">
          <div class="flex items-center justify-between gap-4 py-3">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qr.amount') }}</dt>
            <dd class="text-right text-body-sm font-semibold">{{ displayAmount }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qr.memo') }}</dt>
            <dd class="max-w-[65%] text-right text-body-sm font-semibold">
              {{ qrRequest.memo || t('wallet.qr.noMemo') }}
            </dd>
          </div>
        </dl>

        <p class="mt-3 text-caption text-ink-3">
          {{ balanceLabel }}
        </p>
      </AppCard>

      <div
        class="mt-3 flex items-center gap-2 rounded-xs bg-surface-2 px-3 py-2 text-caption text-ink-2"
      >
        <IconInfoCircle
          :size="16"
          :stroke-width="1.75"
          aria-hidden="true"
        />
        <p>{{ t('wallet.qr.sandboxNotice') }}</p>
      </div>

      <AppButton
        block
        variant="secondary"
        class="mt-3"
        @click="createNewQr"
      >
        {{ t('wallet.qr.createNew') }}
      </AppButton>
    </section>
  </main>
</template>
