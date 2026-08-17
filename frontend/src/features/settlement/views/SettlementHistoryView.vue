<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import StateError from '@/shared/ui/StateError.vue'

import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementInlineLoading from '../components/SettlementInlineLoading.vue'
import SettlementListCard from '../components/SettlementListCard.vue'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import { splitIntoSections, type SettlementSide } from '../model/settlementList'
import { useSettlements } from '../model/settlementQueries'

/**
 * 완료된 정산 전체 내역.
 *
 * 목록 API가 페이지네이션 없이 전량을 내려주므로 새로 요청하지 않고 같은 쿼리 캐시를
 * 걸러 쓴다. 목록 화면에서 이미 받아 둔 응답을 그대로 재사용한다.
 */
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const settlementQuery = useSettlements()

const side = computed<SettlementSide>(() => (route.query.side === 'sent' ? 'sent' : 'received'))
const completed = computed(
  () => splitIntoSections(settlementQuery.data.value?.[side.value] ?? []).completed,
)
const errorKey = computed(() => resolveSettlementError(settlementQuery.error.value).messageKey)

function open(settlementId: string): void {
  void router.push({ name: 'settlement-detail', params: { settlementId } })
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      :title="
        t(side === 'sent' ? 'settlement.history.titleCollect' : 'settlement.history.titlePay')
      "
      :back-label="t('settlement.backToList')"
      @back="router.push({ name: 'settlements' })"
    />
    <SettlementInlineLoading
      v-if="settlementQuery.isPending.value"
      class="mt-8"
      :label="t('settlement.list.loading')"
    />
    <StateError
      v-else-if="settlementQuery.isError.value"
      class="my-auto"
      :title="t(errorKey)"
      :description="t('settlement.list.retryHint')"
      @retry="settlementQuery.refetch()"
    />
    <SettlementEmptyState
      v-else-if="completed.length === 0"
      class="flex-1"
      :title="t('settlement.history.emptyTitle')"
      :description="t('settlement.history.emptyDescription')"
    />
    <ul
      v-else
      class="mt-8 space-y-2"
    >
      <li
        v-for="settlement in completed"
        :key="settlement.id"
      >
        <SettlementListCard
          compact
          :settlement="settlement"
          :side="side"
          @open="open(settlement.id)"
        />
      </li>
    </ul>
  </section>
</template>
