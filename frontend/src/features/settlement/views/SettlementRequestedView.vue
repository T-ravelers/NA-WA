<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'

/**
 * 정산 요청을 보낸 뒤의 완료 화면.
 *
 * 요청서로 되돌아갈 수 없는 지점이라 단계 표시를 두지 않는다. 여기서는 받을 목록으로
 * 넘어가 방금 만든 요청을 확인한다.
 */
const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <SettlementStatusScreen
    state="done"
    :title="t('settlement.create.requestedTitle')"
    :description="t('settlement.create.requestedDescription')"
    :action-label="t('settlement.create.goToCollect')"
    @action="router.replace({ name: 'settlements', query: { side: 'sent' } })"
  />
</template>
