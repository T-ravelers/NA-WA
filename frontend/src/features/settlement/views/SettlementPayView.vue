<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import StateError from '@/shared/ui/StateError.vue'

import { settlementGateway } from '../api/settlementGateway'
import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import {
  clearSettlementPaymentIdempotencyKey,
  resolveSettlementPaymentIdempotencyKey,
} from '../model/settlementIdempotency'
import { settlementKeys, useSettlementDetail } from '../model/settlementQueries'

/**
 * 결제를 실제로 보내는 화면.
 *
 * 상세에서 결제를 누르면 이 화면으로 넘어와 요청을 보낸다. 다만 주소로 직접 들어오거나
 * 이미 낸 정산으로 되돌아오는 경로가 있어서, 서버가 `PAY`를 허용한 경우에만 보낸다.
 * 상세를 거쳐 왔다면 이 조회는 캐시에서 즉시 끝난다.
 *
 * 새로고침으로 다시 들어와도 멱등키가 세션에 남아 있어 같은 시도로 취급된다.
 */
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const started = ref(false)

function goToDetail(): void {
  void router.replace({ name: 'settlement-detail', params: { settlementId: settlementId.value } })
}

const paymentMutation = useMutation({
  mutationFn: async () =>
    settlementGateway.pay(
      settlementId.value,
      resolveSettlementPaymentIdempotencyKey(settlementId.value),
    ),
  onSuccess: async () => {
    clearSettlementPaymentIdempotencyKey(settlementId.value)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: settlementKeys.lists() }),
      queryClient.invalidateQueries({ queryKey: settlementKeys.candidates() }),
      queryClient.invalidateQueries({ queryKey: settlementKeys.detail(settlementId.value) }),
    ])
    await router.replace({
      name: 'settlement-pay-complete',
      params: { settlementId: settlementId.value },
    })
  },
})

const failed = computed(() => paymentMutation.isError.value || detailQuery.isError.value)
const errorKey = computed(
  () => resolveSettlementError(paymentMutation.error.value ?? detailQuery.error.value).messageKey,
)

function recover(): void {
  if (detailQuery.isError.value) {
    void detailQuery.refetch()
    return
  }

  const recovery = resolveSettlementError(paymentMutation.error.value).recovery
  if (recovery === 'BACK_TO_LIST') {
    void router.replace({ name: 'settlements' })
    return
  }
  if (recovery === 'REFETCH_DETAIL') {
    // 이미 처리된 결제는 다시 보내지 않는다. 서버 상태를 새로 읽도록 상세로 돌려보낸다.
    void queryClient.invalidateQueries({ queryKey: settlementKeys.detail(settlementId.value) })
    goToDetail()
    return
  }
  // 서버가 키를 거부했다면 같은 키로 다시 보내도 같은 오류다. 버리고 새로 만든다.
  if (recovery === 'RETRY_NEW_KEY') clearSettlementPaymentIdempotencyKey(settlementId.value)

  paymentMutation.mutate()
}

// 서버가 결제를 허용한 경우에만, 그리고 한 번만 보낸다.
watchEffect(() => {
  if (started.value) return
  const detail = detailQuery.data.value
  if (detail === undefined) return

  started.value = true
  if (detail.viewer.allowedActions.includes('PAY')) paymentMutation.mutate()
  else goToDetail()
})
</script>

<template>
  <section
    v-if="failed"
    class="flex min-h-dvh flex-col px-screen pt-8 pb-32"
  >
    <StateError
      class="my-auto"
      :title="t(errorKey)"
      @retry="recover"
    />
  </section>
  <SettlementStatusScreen
    v-else
    state="processing"
    :title="t('settlement.pay.processing')"
    :description="t('settlement.pay.processingHint')"
  />
</template>
