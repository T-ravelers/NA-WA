<script setup lang="ts">
import QRCode from 'qrcode'
import { onMounted, ref } from 'vue'

import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 초대 링크를 QR로 보여주는 바텀시트.
 *
 * 코드를 불러 주거나 링크를 보내기 어려운 자리(마주 앉아 있을 때)를 위한 것이다.
 * 담고 있는 값은 「링크 복사」와 **같은 주소**다 — 둘이 다른 곳을 가리키면 어느 쪽으로
 * 받았는지에 따라 다른 곳에 도착한다.
 *
 * `qrcode`는 `WalletQrView`·`MerchantView`가 이미 쓰는 의존성이다.
 */
interface Props {
  /** QR에 담을 초대 링크. */
  url: string
  title: string
  /** QR 아래에 글자로도 적는 코드. 카메라가 없을 때 읽어 줄 수 있어야 한다. */
  code: string
  closeLabel: string
  /** QR 이미지의 대체 문구. */
  imageLabel: string
  /** 그리기에 실패했을 때의 문구. */
  failedLabel: string
}

const { url, title, code, closeLabel, imageLabel, failedLabel } = defineProps<Props>()

const emit = defineEmits<{ close: [] }>()

const dataUrl = ref<string | null>(null)
const failed = ref(false)

onMounted(() => {
  QRCode.toDataURL(url, { margin: 1, width: 320 })
    .then((value) => {
      dataUrl.value = value
    })
    .catch(() => {
      failed.value = true
    })
})
</script>

<template>
  <div class="fixed inset-0 z-30">
    <button
      type="button"
      class="absolute inset-0 bg-scrim/60"
      tabindex="-1"
      aria-hidden="true"
      @click="emit('close')"
    />

    <div
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex w-full max-w-[390px] flex-col items-center gap-4 rounded-t-lg bg-surface-2 px-screen pb-8 pt-3 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="h-1 w-10 rounded-pill bg-hairline-2"
      />

      <h2 class="font-display text-section-header uppercase text-ink-display">{{ title }}</h2>

      <img
        v-if="dataUrl !== null"
        :src="dataUrl"
        :alt="imageLabel"
        class="size-[240px] rounded-card bg-paper-fill p-3"
      />
      <p
        v-else-if="failed"
        role="alert"
        class="py-16 text-body-sm text-ink-2"
      >
        {{ failedLabel }}
      </p>
      <span
        v-else
        aria-hidden="true"
        class="size-[240px] rounded-card bg-surface-3"
      />

      <p class="text-title-sm tracking-[0.2em] text-ink">{{ code }}</p>

      <AppButton
        block
        variant="secondary"
        @click="emit('close')"
      >
        {{ closeLabel }}
      </AppButton>
    </div>
  </div>
</template>
