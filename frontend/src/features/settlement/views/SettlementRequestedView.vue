<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'

import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
import { resolveSettlementError } from '../model/settlementErrors'
import { useSettlementDetail } from '../model/settlementQueries'

/**
 * 정산 요청을 보낸 뒤의 완료 화면.
 *
 * 요청서로 되돌아갈 수는 없지만 눈금은 마지막 칸이 채워진 채로 남는다. 여기서는 받을
 * 목록으로 넘어가 방금 만든 요청을 확인한다.
 *
 * 결제 완료 화면과 같은 기준으로, 완료를 화면이 스스로 주장하지 않는다. 서버가 이 정산의
 * 요청자로 나를 인정한 경우에만 "Request sent"를 띄우고, 그 전과 실패에는 띄우지 않는다.
 */

/**
 * 눈금 칸 수.
 *
 * 요청서 헤더(`SettlementFlowHeader`)와 같은 네 칸이어야 마지막 칸이 채워진 것으로 읽힌다.
 */
const REQUEST_STEPS = 4

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const points = useSettlementPoints()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const errorKey = computed(() => resolveSettlementError(detailQuery.error.value).messageKey)

function goToCollect(): void {
  void router.replace({ name: 'settlements', query: { side: 'sent' } })
}

watchEffect(() => {
  const detail = detailQuery.data.value
  if (detail === undefined || detail.viewer.role === 'CREATOR') return
  // 남이 만든 정산으로 주소를 열었다. 요청을 보냈다고 말할 자리가 아니다.
  void router.replace({
    name: 'settlement-detail',
    params: { settlementId: settlementId.value },
  })
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
    :title="t('settlement.create.requesting')"
    :description="t('settlement.create.requestingHint')"
  />
  <SettlementStatusScreen
    v-else
    state="done"
    icon="send"
    :steps="REQUEST_STEPS"
    :title="t('settlement.create.requestedTitle')"
    :description="t('settlement.create.requestedDescription')"
    :action-label="t('settlement.create.goToCollect')"
    @action="goToCollect"
  >
    <!-- 방금 보낸 요청이 어떤 상태로 어느 금액에 걸렸는지 한 번 더 적어 둔다. -->
    <template #summary>
      <AppCard class="mt-8 text-left">
        <dl class="space-y-3 text-body-sm">
          <div class="flex items-center justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.state') }}</dt>
            <dd>
              <AppBadge tone="info">{{
                t(`settlement.status.${detailQuery.data.value.status}`)
              }}</AppBadge>
            </dd>
          </div>
          <div class="flex items-center justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.total') }}</dt>
            <dd class="text-title">{{ points(detailQuery.data.value.totalAmount) }}</dd>
          </div>
        </dl>
      </AppCard>
    </template>
  </SettlementStatusScreen>
</template>
