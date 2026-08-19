<script setup lang="ts">
import { IconX } from '@tabler/icons-vue'
import { onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 영수증을 앱 안에서 찍는 화면.
 *
 * 파일 입력의 capture 속성은 휴대폰에서만 통하고 노트북에서는 무시돼 파일 창만 열린다.
 * 그래서 카메라 영상을 직접 받아 한 장을 잘라낸다.
 */
const emit = defineEmits<{ capture: [file: File]; close: []; useLibrary: [] }>()

const { t } = useI18n()
const video = useTemplateRef('video')
const errorKey = ref<string | null>(null)
const busy = ref(false)
let stream: MediaStream | null = null

function stop(): void {
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

async function start(): Promise<void> {
  if (navigator.mediaDevices?.getUserMedia === undefined) {
    errorKey.value = 'settlement.receipt.camera.unsupported'
    return
  }

  try {
    // 휴대폰에서는 뒷면 카메라를 쓴다. 영수증은 자기 얼굴 쪽으로 찍지 않는다.
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
      audio: false,
    })

    if (video.value !== null) {
      video.value.srcObject = stream
      await video.value.play()
    }
  } catch (error) {
    errorKey.value =
      error instanceof DOMException && error.name === 'NotAllowedError'
        ? 'settlement.receipt.camera.denied'
        : 'settlement.receipt.camera.unavailable'
  }
}

/** 지금 보이는 화면을 그대로 한 장으로 잘라낸다. */
async function shoot(): Promise<void> {
  const element = video.value

  if (element === null || busy.value) return

  busy.value = true

  try {
    const canvas = document.createElement('canvas')
    canvas.width = element.videoWidth
    canvas.height = element.videoHeight
    canvas.getContext('2d')?.drawImage(element, 0, 0, canvas.width, canvas.height)

    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/jpeg', 0.9)
    })

    if (blob === null) {
      errorKey.value = 'settlement.receipt.camera.unavailable'
      return
    }

    emit('capture', new File([blob], 'receipt.jpg', { type: 'image/jpeg' }))
  } finally {
    busy.value = false
  }
}

onMounted(start)
onBeforeUnmount(stop)
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex flex-col bg-scrim/90"
    role="dialog"
    aria-modal="true"
    :aria-label="t('settlement.receipt.camera.title')"
  >
    <header class="flex items-center justify-between px-screen py-4">
      <p class="text-title-sm text-on-scrim">{{ t('settlement.receipt.camera.title') }}</p>
      <button
        type="button"
        data-action="receipt-camera-close"
        class="grid size-11 place-items-center rounded-sm text-on-scrim"
        :aria-label="t('settlement.receipt.camera.cancel')"
        @click="emit('close')"
      >
        <IconX
          :size="22"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </button>
    </header>

    <div class="flex flex-1 items-center justify-center px-screen">
      <p
        v-if="errorKey !== null"
        class="text-center text-body-sm text-on-scrim"
        role="alert"
      >
        {{ t(errorKey) }}
      </p>
      <!-- eslint-disable-next-line vuejs-accessibility/media-has-caption -->
      <video
        v-else
        ref="video"
        data-testid="receipt-camera-video"
        playsinline
        muted
        class="max-h-full w-full rounded-sm object-contain"
      />
    </div>

    <div class="flex flex-col gap-2 px-screen pt-4 pb-8">
      <AppButton
        v-if="errorKey === null"
        data-action="receipt-camera-shoot"
        block
        variant="settle"
        :disabled="busy"
        @click="shoot"
        >{{ t('settlement.receipt.camera.shutter') }}</AppButton
      >
      <AppButton
        data-action="receipt-camera-library"
        block
        variant="secondary"
        @click="emit('useLibrary')"
        >{{ t('settlement.receipt.source.library') }}</AppButton
      >
    </div>
  </div>
</template>
