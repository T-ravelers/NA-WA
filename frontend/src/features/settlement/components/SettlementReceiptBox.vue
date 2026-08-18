<script setup lang="ts">
import { IconReceipt } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

/**
 * 영수증 등록 자리.
 *
 * 촬영·갤러리 선택·업로드·OCR·확대 보기는 후속 이슈에서 붙인다. 저장소에 파일 업로드
 * 인프라가 아직 없어 지금은 자리와 이름만 두고 비활성으로 둔다. 품목별 정산의 영수증
 * 필수 규칙도 함께 켜야 하므로 여기서 먼저 켜지 않는다.
 */
const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    data-action="add-receipt"
    disabled
    class="flex size-16 shrink-0 flex-col items-center justify-center gap-1 rounded-sm border border-dashed border-hairline-strong bg-surface-2 text-ink-3 disabled:opacity-60"
  >
    <IconReceipt
      :size="20"
      :stroke-width="1.8"
      aria-hidden="true"
    />
    <span class="text-micro">{{ t('settlement.receipt.label') }}</span>
    <span class="sr-only">{{ t('settlement.receipt.comingSoon') }}</span>
  </button>
</template>
