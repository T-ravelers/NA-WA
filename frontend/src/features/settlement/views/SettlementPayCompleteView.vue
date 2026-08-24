<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppTicket from '@/shared/ui/AppTicket.vue'
import StateError from '@/shared/ui/StateError.vue'

import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
import { resolveSettlementError } from '../model/settlementErrors'
import { resolveSide } from '../model/settlementList'
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
const points = useSettlementPoints()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const errorKey = computed(() => resolveSettlementError(detailQuery.error.value).messageKey)

function goToDetail(): void {
  void router.replace({
    name: 'settlement-detail',
    params: { settlementId: settlementId.value },
    query: { side: resolveSide(route.query.side) },
  })
}

watchEffect(() => {
  const detail = detailQuery.data.value
  if (detail !== undefined && detail.viewer.requestStatus !== 'PAID') goToDetail()
})
</script>

<template>
  <section
    v-if="detailQuery.isError.value"
    class="flex flex-col px-screen flex-1 w-full pt-6 pb-8"
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
  >
    <!--
      끝난 거래를 적어 두는 영수증. 시안은 절취선 위에 거래 번호와 받은 사람을, 아래에
      보낸 금액을 둔다. 조형은 `AppTicket` 하나가 소유하므로 여기서 다시 만들지 않는다.

      보낸 사람 줄은 두지 않는다. 이 화면이 아는 것은 정산 상세뿐이고 내 표시 이름은
      회원 도메인에 있어서, 이름을 채우려면 feature 경계를 넘어야 한다.
    -->
    <template #summary>
      <AppTicket
        class="mt-8 text-left"
        tone="paper"
        :body-size="detailQuery.data.value?.transactionId === undefined ? 56 : 88"
        :notch-size="16"
      >
        <template #body>
          <div class="space-y-3 px-5 py-4 text-body-sm">
            <div
              v-if="detailQuery.data.value?.transactionId !== undefined"
              class="flex justify-between gap-3"
            >
              <span class="text-on-paper/70">{{ t('settlement.detail.transactionId') }}</span>
              <span class="min-w-0 truncate font-semibold">{{
                detailQuery.data.value.transactionId
              }}</span>
            </div>
            <div class="flex justify-between gap-3">
              <span class="text-on-paper/70">{{ t('settlement.detail.sendTo') }}</span>
              <span class="min-w-0 truncate font-semibold">{{
                detailQuery.data.value?.requestedBy
              }}</span>
            </div>
          </div>
        </template>
        <template #stub>
          <div class="flex items-center justify-between gap-3 px-5 py-4">
            <span class="text-body-sm text-on-paper/70">{{
              t('settlement.detail.sendAmount')
            }}</span>
            <strong class="text-data-lg">{{
              points(detailQuery.data.value?.viewer.shareAmount ?? '0')
            }}</strong>
          </div>
        </template>
      </AppTicket>
    </template>
  </SettlementStatusScreen>
</template>
