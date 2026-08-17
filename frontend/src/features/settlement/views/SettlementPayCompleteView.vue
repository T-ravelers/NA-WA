<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { useSettlementDetail } from '../model/settlementQueries'

/**
 * 결제 완료 화면.
 *
 * 결제 화면을 `replace`로 대체하며 들어오므로 뒤로가기는 정산 상세로 간다. 그래도
 * 주소로 직접 들어오는 경로가 남아 있어서, 서버가 아직 결제되지 않았다고 하면 상세로
 * 돌려보낸다. 완료를 화면이 스스로 주장하지 않는다.
 */
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)

function goToDetail(): void {
  void router.replace({ name: 'settlement-detail', params: { settlementId: settlementId.value } })
}

watchEffect(() => {
  const detail = detailQuery.data.value
  if (detail !== undefined && detail.viewer.requestStatus !== 'PAID') goToDetail()
})
</script>

<template>
  <SettlementStatusScreen
    state="done"
    :title="t('settlement.pay.completeTitle')"
    :description="t('settlement.pay.completeDescription')"
    :action-label="t('settlement.pay.backToDetail')"
    @action="goToDetail"
  />
</template>
