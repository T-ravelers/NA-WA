<script setup lang="ts">
import { IconCamera, IconPhoto, IconReceipt } from '@tabler/icons-vue'
import { computed, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import SettlementBottomSheet from './SettlementBottomSheet.vue'
import SettlementReceiptCamera from './SettlementReceiptCamera.vue'

/**
 * 영수증 자리.
 *
 * 생성 화면에서는 사진을 고르는 버튼이고, 상세 화면에서는 붙어 있는 사진을 여는 버튼이다.
 * 두 화면이 같은 거래 카드를 쓰기 때문에 한 컴포넌트가 두 역할을 맡는다.
 *
 * 'empty'는 볼 사진이 없다는 것을 화면이 이미 아는 자리다. 눌러도 아무 일이 없는 버튼을
 * 두면 고장으로 보이므로, 눌리지 않게 하고 없다고 알린다.
 */
interface Props {
  mode?: 'add' | 'view' | 'empty'
  /** 썸네일 주소. 없으면 아이콘만 나온다. */
  previewUrl?: string | null
  pending?: boolean
  /** 'empty'일 때 읽어 줄 문구. 왜 볼 수 없는지는 화면마다 다르다. */
  emptyLabel?: string
}

const {
  mode = 'add',
  previewUrl = null,
  pending = false,
  emptyLabel = undefined,
} = defineProps<Props>()
const emit = defineEmits<{ select: [file: File]; open: [] }>()

const { t } = useI18n()
const libraryInput = useTemplateRef('libraryInput')
const sourceSheetOpen = ref(false)
const cameraOpen = ref(false)

const isDisabled = computed(() => pending || mode === 'empty')

const label = computed(() => {
  // 같은 "기다리는 중"이라도 올리는 것과 받는 것은 사용자에게 전혀 다른 일이다.
  if (pending)
    return mode === 'add' ? t('settlement.receipt.pending') : t('settlement.receipt.loading')
  if (mode === 'empty') return emptyLabel ?? t('settlement.receipt.none')
  if (mode === 'view') return t('settlement.receipt.view')
  return previewUrl === null ? t('settlement.receipt.label') : t('settlement.receipt.change')
})

function handleClick(): void {
  if (mode !== 'add') {
    emit('open')
    return
  }
  sourceSheetOpen.value = true
}

/*
 * 시트를 먼저 닫고 연다. 사용자가 방금 누른 흐름 안에서 열어야 브라우저가 사진 접근을
 * 허용한다.
 */
function chooseSource(source: 'camera' | 'library'): void {
  sourceSheetOpen.value = false

  if (source === 'camera') {
    cameraOpen.value = true
    return
  }

  libraryInput.value?.click()
}

function handleCapture(file: File): void {
  cameraOpen.value = false
  emit('select', file)
}

/** 카메라가 막혀 있을 때 저장소로 빠져나갈 길을 준다. */
function switchToLibrary(): void {
  cameraOpen.value = false
  libraryInput.value?.click()
}

function handleChange(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (file !== undefined) {
    emit('select', file)
  }

  // 같은 파일을 다시 골랐을 때도 change가 나도록 값을 비운다.
  target.value = ''
}
</script>

<template>
  <div class="shrink-0">
    <button
      type="button"
      data-action="add-receipt"
      :disabled="isDisabled"
      :aria-label="label"
      class="flex size-16 flex-col items-center justify-center gap-1 overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2 text-ink-3 disabled:opacity-60"
      :class="{ 'border-solid': previewUrl !== null }"
      @click="handleClick"
    >
      <img
        v-if="previewUrl !== null"
        :src="previewUrl"
        alt=""
        class="size-full object-cover"
      />
      <template v-else>
        <component
          :is="mode === 'add' ? IconCamera : IconReceipt"
          :size="20"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <span class="text-micro">{{ t('settlement.receipt.label') }}</span>
      </template>
    </button>

    <!--
      촬영은 파일 입력의 capture 속성을 쓰지 않는다. 그 속성은 휴대폰에서만 통하고
      노트북에서는 무시돼 파일 창만 열린다. 촬영은 카메라 화면이 따로 맡는다.
    -->
    <input
      v-if="mode === 'add'"
      ref="libraryInput"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      class="sr-only"
      data-testid="receipt-library-input"
      @change="handleChange"
    />

    <SettlementBottomSheet
      v-if="sourceSheetOpen"
      :label="t('settlement.receipt.source.title')"
      @close="sourceSheetOpen = false"
    >
      <h2 class="font-display text-section-header text-ink-display uppercase">
        {{ t('settlement.receipt.source.title') }}
      </h2>

      <div class="mt-4 flex flex-col gap-2">
        <button
          type="button"
          data-action="receipt-source-camera"
          class="flex min-h-14 items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left"
          @click="chooseSource('camera')"
        >
          <IconCamera
            :size="22"
            :stroke-width="1.8"
            aria-hidden="true"
            class="text-ink-2"
          />
          <span class="text-title-sm text-ink-display">
            {{ t('settlement.receipt.source.camera') }}
          </span>
        </button>

        <button
          type="button"
          data-action="receipt-source-library"
          class="flex min-h-14 items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left"
          @click="chooseSource('library')"
        >
          <IconPhoto
            :size="22"
            :stroke-width="1.8"
            aria-hidden="true"
            class="text-ink-2"
          />
          <span class="text-title-sm text-ink-display">
            {{ t('settlement.receipt.source.library') }}
          </span>
        </button>
      </div>

      <p class="mt-3 text-micro text-ink-3">{{ t('settlement.receipt.hint') }}</p>
    </SettlementBottomSheet>

    <SettlementReceiptCamera
      v-if="cameraOpen"
      @capture="handleCapture"
      @close="cameraOpen = false"
      @use-library="switchToLibrary"
    />
  </div>
</template>
