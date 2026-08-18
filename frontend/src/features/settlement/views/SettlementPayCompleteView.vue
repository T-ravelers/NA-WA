<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import StateError from '@/shared/ui/StateError.vue'

import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import { useSettlementDetail } from '../model/settlementQueries'

/**
 * 결제 완료 화면.
 *
 * 결제 화면을 `replace`로 대체하며 들어오므로 뒤로가기는 정산 상세로 간다. 그래도
 * 주소로 직접 들어오는 경로가 남아 있어서, 서버가 아직 결제되지 않았다고 하면 상세로
 * 돌려보낸다. 완료를 화면이 스스로 주장하지 않는다.
 *
 * 그래서 조회가 끝나기 전과 조회가 실패했을 때도 성공을 그리지 않는다. 실패하면 상세를
 * 읽지 못해 되돌려보내는 판정 자체가 서지 않으므로, 결제된 적 없는 정산에 완료 화면이
 * 그대로 남는다.
 */
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const errorKey = computed(() => resolveSettlementError(detailQuery.error.value).messageKey)

function goToDetail(): void {
  void router.replace({ name: 'settlement-detail', params: { settlementId: settlementId.value } })
}

watchEffect(() => {
  const detail = detailQuery.data.value
  if (detail !== undefined && detail.viewer.requestStatus !== 'PAID') goToDetail()
})
</script>

<template>
  <section
    v-if="detailQuery.isError.value"
    class="flex min-h-dvh flex-col px-screen pt-8 pb-32"
  >
    <StateError
      class="my-auto"
      :title="t(errorKey)"
      @retry="detailQuery.refetch()"
    />
  </section>
  <SettlementStatusScreen
    v-else-if="detailQuery.data.value === undefined"
    state="processing"
    :title="t('settlement.pay.processing')"
    :description="t('settlement.pay.processingHint')"
  />
  <SettlementStatusScreen
    v-else
    state="done"
    :title="t('settlement.pay.completeTitle')"
    :description="t('settlement.pay.completeDescription')"
    :action-label="t('settlement.pay.backToDetail')"
    @action="goToDetail"
  />
</template>
