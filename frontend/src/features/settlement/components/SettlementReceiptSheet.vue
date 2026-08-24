<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppImage from '@/shared/ui/AppImage.vue'

import SettlementBottomSheet from './SettlementBottomSheet.vue'

/**
 * 영수증 사진을 크게 보는 시트.
 *
 * 64px 썸네일로는 금액과 품목을 읽을 수 없어서, 분담이 맞는지 확인하려면 크게 볼 자리가
 * 필요하다.
 */
interface Props {
  url: string
}
defineProps<Props>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
</script>

<template>
  <SettlementBottomSheet
    :label="t('settlement.receipt.sheetLabel')"
    @close="emit('close')"
  >
    <AppImage
      :src="url"
      :alt="t('settlement.receipt.alt')"
      class="mx-auto max-h-[70dvh] w-full rounded-sm object-contain"
    />
  </SettlementBottomSheet>
</template>
