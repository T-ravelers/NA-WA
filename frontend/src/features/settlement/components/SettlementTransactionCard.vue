<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppCard from '@/shared/ui/AppCard.vue'

import { useSettlementPoints } from '../composables/useSettlementPoints'
import SettlementReceiptBox from './SettlementReceiptBox.vue'

/**
 * 무엇을 정산하는 중인지 계속 보여주는 원거래 카드.
 *
 * 요청 생성 2단계와 정산 상세가 같은 카드를 쓴다. 두 화면에서 같은 거래를 다르게
 * 그리면 사용자가 같은 건인지 확신할 수 없다.
 */
interface Props {
  gatheringName: string
  amount: string
  /**
   * 표시용으로 이미 포맷된 결제시각.
   *
   * TODO: 정산 상세 응답에는 결제시각이 없어 상세에서는 비어 있다. 후속 이슈에서
   * 서버가 내려주면 상세에도 채운다.
   */
  paidAt?: string
  payerName?: string
}

const { gatheringName, amount, paidAt = undefined, payerName = undefined } = defineProps<Props>()

const { t } = useI18n()
const points = useSettlementPoints()

// TODO: `merchantName`이 지금은 약속 이름의 복사본이라 표시하면 같은 값이 두 번 나온다.
// 서버가 실제 매장명을 내려주면 약속 이름 위에 매장명을 얹는다.
</script>

<template>
  <AppCard data-testid="settlement-transaction-card">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <p class="text-title">{{ gatheringName }}</p>
        <p
          v-if="payerName !== undefined"
          class="mt-1 text-body-sm text-ink-2"
        >
          {{ t('settlement.detail.paidBy') }} · {{ payerName }}
        </p>
        <p
          v-if="paidAt !== undefined"
          class="mt-1 text-caption text-ink-3"
        >
          {{ paidAt }}
        </p>
      </div>
      <SettlementReceiptBox />
    </div>
    <p class="mt-4 text-data-lg">{{ points(amount) }}</p>
  </AppCard>
</template>
