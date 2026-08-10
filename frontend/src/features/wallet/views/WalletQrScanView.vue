<script setup lang="ts">
import { IconChevronLeft, IconInfoCircle } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import AppCard from '@/shared/ui/AppCard.vue'

const { t } = useI18n()
const router = useRouter()

const goBack = (): void => {
  void router.push({ name: 'wallet' })
}

const openMyQr = (): void => {
  void router.push({ name: 'wallet-qr' })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <header class="flex items-center border-b border-hairline py-4">
      <button
        type="button"
        class="grid size-11 place-items-center rounded-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        :aria-label="t('wallet.qrScan.back')"
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
      <button
        type="button"
        role="tab"
        aria-selected="false"
        class="min-h-12 border-b-2 border-transparent px-3 text-body-sm text-ink-3 transition-colors hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ink"
        @click="openMyQr"
      >
        {{ t('wallet.qr.tabs.myQr') }}
      </button>
      <button
        type="button"
        role="tab"
        aria-selected="true"
        class="min-h-12 border-b-2 border-ink px-3 text-body-sm font-semibold text-ink"
      >
        {{ t('wallet.qr.tabs.scan') }}
      </button>
    </nav>

    <section
      class="pt-6"
      aria-labelledby="wallet-qr-scan-heading"
    >
      <h2
        id="wallet-qr-scan-heading"
        class="sr-only"
      >
        {{ t('wallet.qrScan.heading') }}
      </h2>

      <AppCard
        padding="lg"
        class="text-center"
      >
        <div
          class="relative mx-auto aspect-square w-full max-w-[240px] rounded-card border-2 border-hairline-strong bg-surface-1"
          role="img"
          :aria-label="t('wallet.qrScan.imageLabel')"
        >
          <span
            class="absolute left-1/2 top-1/2 size-8 -translate-x-1/2 -translate-y-1/2 rounded-xs bg-surface-2"
            aria-hidden="true"
          />
        </div>

        <p class="mt-4 text-body-sm text-ink-2">
          {{ t('wallet.qrScan.instruction') }}
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
        <p>{{ t('wallet.qrScan.sandboxNotice') }}</p>
      </div>

      <div
        class="mt-3 flex min-h-12 items-center justify-center rounded-sm border border-hairline-strong px-4 text-body-sm text-ink-2"
        role="status"
      >
        {{ t('wallet.qrScan.memo') }}
      </div>
    </section>
  </main>
</template>
