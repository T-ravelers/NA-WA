<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { QrcodeStream, type DetectedBarcode, type EmittedError } from 'vue-qrcode-reader'

import { NormalizedApiError } from '@/shared/api/apiError'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import { resolvePaymentQr } from '../api/qrPaymentApi'
import { useQrPaymentSessionStore } from '../model/qrPaymentSession'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const qrPaymentSession = useQrPaymentSessionStore()

const isScanQrActive = computed(() => route.name === 'wallet-qr-scan')

const paused = ref(false)
const cameraError = ref<'denied' | 'unsupported' | null>(null)

const resolveMutation = useMutation({ mutationFn: resolvePaymentQr })

const resolveErrorMessage = computed(() => {
  const error = resolveMutation.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('wallet.qrScan.resolveErrorGeneric')
  }

  return t(error.messageKey)
})

const onDetect = (detectedCodes: DetectedBarcode[]): void => {
  if (paused.value) return

  const qrToken = detectedCodes[0]?.rawValue

  if (qrToken === undefined || qrToken === '') return

  paused.value = true
  resolveMutation.mutate(qrToken, {
    onSuccess: (resolved) => {
      qrPaymentSession.setSession({ qrToken, resolved })
    },
  })
}

const onCameraError = (error: EmittedError): void => {
  cameraError.value = error.name === 'NotAllowedError' ? 'denied' : 'unsupported'
}

const rescan = (): void => {
  resolveMutation.reset()
  cameraError.value = null
  paused.value = false
}

const goBack = (): void => {
  void router.push({ name: 'wallet' })
}

const continueToPreview = (): void => {
  void router.push({ name: 'wallet-qr-payment-preview' })
}
</script>

<template>
  <main class="flex px-screen flex-1 flex-col w-full pt-6 pb-8">
    <ScreenHeader
      variant="back"
      :title="t('wallet.qr.title')"
      :back-label="t('wallet.qrScan.back')"
      @back="goBack"
    />

    <nav
      class="grid grid-cols-2 border-b border-hairline"
      :aria-label="t('wallet.qr.tabs.label')"
    >
      <RouterLink
        :to="{ name: 'wallet-qr' }"
        class="flex min-h-12 items-center justify-center border-b-2 border-transparent px-3 text-body-sm text-ink-3 transition-colors hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ink"
      >
        {{ t('wallet.qr.tabs.myQr') }}
      </RouterLink>
      <RouterLink
        :to="{ name: 'wallet-qr-scan' }"
        class="flex min-h-12 items-center justify-center border-b-2 px-3 text-body-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ink"
        :class="
          isScanQrActive
            ? 'border-ink font-semibold text-ink'
            : 'border-transparent text-ink-3 hover:text-ink'
        "
      >
        {{ t('wallet.qr.tabs.scan') }}
      </RouterLink>
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
        <template v-if="cameraError !== null">
          <p class="text-body-sm font-semibold text-ink-2">
            {{
              cameraError === 'denied'
                ? t('wallet.qrScan.cameraDenied')
                : t('wallet.qrScan.cameraUnavailable')
            }}
          </p>
        </template>

        <template v-else-if="resolveMutation.isSuccess.value && qrPaymentSession.session !== null">
          <p class="text-body-sm text-ink-2">{{ t('wallet.qrScan.resolvedTitle') }}</p>
          <p class="mt-2 text-title-sm font-semibold">
            {{ qrPaymentSession.session.resolved.payeeName }}
          </p>
          <AppButton
            block
            class="mt-5"
            @click="continueToPreview"
          >
            {{ t('wallet.qrScan.continue') }}
          </AppButton>
        </template>

        <template v-else>
          <div
            class="relative mx-auto aspect-square w-full max-w-[240px] overflow-hidden rounded-card border-2 border-hairline-strong bg-surface-1"
            role="img"
            :aria-label="t('wallet.qrScan.imageLabel')"
          >
            <QrcodeStream
              :paused="paused"
              :formats="['qr_code']"
              @detect="onDetect"
              @error="onCameraError"
            />
          </div>

          <p class="mt-4 text-body-sm text-ink-2">
            {{
              resolveMutation.isPending.value
                ? t('wallet.qrScan.resolving')
                : t('wallet.qrScan.instruction')
            }}
          </p>

          <p
            v-if="resolveMutation.isError.value"
            role="alert"
            class="mt-3 rounded-sm bg-surface-3 px-3.5 py-3 text-body-sm text-ink-2"
          >
            {{ resolveErrorMessage }}
          </p>
        </template>

        <AppButton
          v-if="cameraError !== null || resolveMutation.isError.value"
          variant="secondary"
          block
          class="mt-3"
          @click="rescan"
        >
          {{ t('wallet.qrScan.rescan') }}
        </AppButton>
      </AppCard>
    </section>
  </main>
</template>
