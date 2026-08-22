<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'

import { settlementGateway } from '../api/settlementGateway'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
import { resolveSettlementError } from '../model/settlementErrors'
import { resolveSide } from '../model/settlementList'
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
 * 이체를 무클릭으로 실행하는 것은 상세의 Pay 버튼으로 들어온 경우뿐이다. 그 버튼이
 * 히스토리 상태에 진입 의사를 실어 보내므로, 공유된 링크·북마크·주소창 자동완성으로 열면
 * 확인을 한 번 받는다. 히스토리 상태는 새로고침과 뒤로가기에도 남아 정상 흐름은 그대로다.
 *
 * 새로고침으로 다시 들어와도 멱등키가 세션에 남아 있어 같은 시도로 취급된다.
 */
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const points = useSettlementPoints()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const started = ref(false)
const confirmed = ref(router.options.history.state.confirmed === true)

function goToDetail(): void {
  void router.replace({
    name: 'settlement-detail',
    params: { settlementId: settlementId.value },
    query: { side: resolveSide(route.query.side) },
  })
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
      query: { side: resolveSide(route.query.side) },
    })
  },
})

const failed = computed(() => paymentMutation.isError.value || detailQuery.isError.value)
/** 확인을 받아야 하는 동안에만 값이 있다. 확인 화면은 이 정산 정보를 그대로 보여준다. */
const awaitingConfirmation = computed(() => {
  const detail = detailQuery.data.value
  if (confirmed.value || detail === undefined) return null
  return detail.viewer.allowedActions.includes('PAY') ? detail : null
})
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

function confirm(): void {
  confirmed.value = true
}

// 서버가 결제를 허용한 경우에만, 그리고 한 번만 보낸다.
watchEffect(() => {
  if (started.value) return
  const detail = detailQuery.data.value
  if (detail === undefined) return

  if (!detail.viewer.allowedActions.includes('PAY')) {
    started.value = true
    goToDetail()
    return
  }
  if (!confirmed.value) return

  started.value = true
  paymentMutation.mutate()
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
  <section
    v-else-if="awaitingConfirmation !== null"
    class="flex min-h-dvh flex-col px-screen pt-8 pb-32"
  >
    <SettlementPageHeader
      :title="t('settlement.pay.confirmTitle')"
      :back-label="t('settlement.back')"
      @back="goToDetail"
    />
    <AppCard class="mt-8">
      <p class="text-caption text-ink-3">{{ t('settlement.detail.sendTo') }}</p>
      <p class="mt-1 text-title">{{ awaitingConfirmation.requestedBy }}</p>
      <p class="mt-4 text-caption text-ink-3">{{ t('settlement.detail.payableNow') }}</p>
      <p class="mt-1 text-data-xl">{{ points(awaitingConfirmation.viewer.payableAmount) }}</p>
    </AppCard>
    <p class="mt-4 text-body-sm text-ink-2">{{ t('settlement.pay.confirmDescription') }}</p>
    <div class="mt-auto pt-8">
      <AppButton
        data-action="confirm-pay"
        block
        @click="confirm"
        >{{
          t('settlement.detail.pay', { amount: points(awaitingConfirmation.viewer.payableAmount) })
        }}</AppButton
      >
    </div>
  </section>
  <SettlementStatusScreen
    v-else
    state="processing"
    :title="t('settlement.pay.processing')"
    :description="t('settlement.pay.processingHint')"
  />
</template>
