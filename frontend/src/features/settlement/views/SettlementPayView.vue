<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { serializeReturnParams } from '@/shared/lib/returnRoute'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import InsufficientBalanceDialog from '@/shared/ui/InsufficientBalanceDialog.vue'
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
 *
 * 잔액이 모자라 거절당하면 충전 화면으로 데려갔다가 이 화면으로 돌아온다. 돌아온 자리는
 * 확인 단계이고, 결제는 사용자가 다시 눌러야 나간다.
 */
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const points = useSettlementPoints()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const started = ref(false)
/**
 * 자동 전송해도 되는가.
 *
 * 상세의 Pay 버튼이 히스토리 상태로 진입 의사를 실어 보낸 경우에만 참이다. `resume=1`은
 * 충전을 다녀왔다는 표시이므로 여기서 다시 거짓이 된다 — **돌아오자마자 돈이 나가면
 * 안 된다.** 충전을 마쳤든 도중에 그만뒀든 사용자가 금액을 보고 다시 누르게 한다.
 */
const confirmed = ref(router.options.history.state.confirmed === true && route.query.resume !== '1')
const topupPromptOpen = ref(false)

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

// 충전 팝업이 떠 있는 동안에는 오류 화면을 내지 않는다. 두 겹으로 쌓이면 무엇을
// 눌러야 할지 흐려진다 — 보증금 흐름도 같은 규칙이다.
//
// 지급 실패만이 아니라 조회 실패까지 함께 가린다. 화면을 잠시 떠났다 돌아오면 상세를 다시
// 읽는데, 그때 실패하면 팝업 뒤로 오류 화면이 깔려 두 겹이 된다. 팝업을 닫으면 드러난다.
const failed = computed(
  () => !topupPromptOpen.value && (paymentMutation.isError.value || detailQuery.isError.value),
)
/** 확인을 받아야 하는 동안에만 값이 있다. 확인 화면은 이 정산 정보를 그대로 보여준다. */
const awaitingConfirmation = computed(() => {
  const detail = detailQuery.data.value
  if (confirmed.value || detail === undefined) return null
  return detail.viewer.allowedActions.includes('PAY') ? detail : null
})
const settlementError = computed(() =>
  resolveSettlementError(paymentMutation.error.value ?? detailQuery.error.value),
)
const errorKey = computed(() => settlementError.value.messageKey)
/**
 * 재시도가 아닌 복구에는 재시도가 아닌 라벨을 준다.
 *
 * 지갑이 없거나 잠긴 실패는 다시 눌러도 결과가 같다. 그 버튼에 "Try again"이라고 적혀
 * 있으면 사용자는 될 때까지 누르게 된다.
 */
const errorActionLabel = computed(() =>
  settlementError.value.recovery === 'GO_TO_WALLET' ? t('settlement.pay.goToWallet') : undefined,
)

/** 팝업 문구와 충전 화면에 실어 보낼 금액. 상세를 받은 뒤에만 값이 있다. */
const payableAmount = computed(() => detailQuery.data.value?.viewer.payableAmount ?? null)

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
  // 지갑이 없거나 잠겼다. 여기서 다시 보내 봐야 같은 답이 온다.
  if (recovery === 'GO_TO_WALLET') {
    void router.replace({ name: 'wallet' })
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

// 잔액이 모자라 거절당했다. 빨간 한 줄과 "Try again" 대신 모자라다는 사실과 다음
// 행동(충전)을 한 번에 묻는다 — 약속 보증금과 같은 규칙이다.
watch(
  () => paymentMutation.error.value,
  (error) => {
    if (!(error instanceof NormalizedApiError)) return
    if (resolveSettlementError(error).recovery !== 'TOP_UP') return
    // 확인 단계로 되돌린다. 팝업 뒤에 "보내는 중"이 남아 있으면 안 되고, 팝업을 그냥
    // 닫았을 때도 금액과 Pay 버튼이 있는 화면에 서게 된다.
    started.value = false
    confirmed.value = false
    topupPromptOpen.value = true
  },
)

function closeTopupPrompt(): void {
  topupPromptOpen.value = false
  // 팝업을 닫았으면 그 오류는 다 본 것이다. 남겨두면 오류 화면으로 다시 나타난다.
  paymentMutation.reset()
}

/**
 * 충전 화면으로 간다.
 *
 * **떠나기 전에 내 자리를 `resume=1`로 바꿔 둔다.** 충전 화면은 일을 마치든 그냥 나가든
 * `router.back()`으로 자기 엔트리를 소비하므로, 왕복이 히스토리 한 자리만 쓴다. 순서를
 * 뒤집으면 replace가 충전 화면 위에서 일어난다.
 *
 * 금액은 어림한 힌트일 뿐이다. 서버는 얼마가 모자란지 알려주지 않으므로 보증금과 마찬가지로
 * 부담금 전액을 채워 보내고, 소수점이 있으면 올린다 — 충전 화면이 정수만 받아들이는 데다
 * 내림하면 채우고도 다시 모자란다. 실제 판정은 결제할 때 서버가 다시 한다.
 */
function goToTopup(): void {
  /*
   * 팝업을 닫지 않고 떠난다.
   *
   * 여기서 닫으면 화면을 떠나기까지의 한 프레임 동안 이 화면이 다시 그려진다 — 오류를
   * 그대로 두면 오류 화면이, 지우면 확인 카드가 스쳐 지나간다. 어느 쪽도 사용자가 방금
   * 누른 "충전하기"와 이어지지 않는다. 컴포넌트는 곧 사라지므로 켜 둔 채 두는 편이 맞다.
   */
  const amount = payableAmount.value === null ? Number.NaN : Math.ceil(Number(payableAmount.value))
  const prefill = Number.isSafeInteger(amount) && amount > 0 ? { amount: String(amount) } : {}

  void router.replace({ path: route.path, query: { ...route.query, resume: '1' } }).then(() =>
    router.push({
      name: 'wallet-top-up',
      query: {
        ...prefill,
        returnRouteName: 'settlement-pay',
        returnParams: serializeReturnParams({ settlementId: settlementId.value }),
        side: resolveSide(route.query.side),
      },
    }),
  )
}
</script>

<template>
  <section
    v-if="failed"
    class="flex min-h-dvh flex-col px-screen pt-8 pb-32"
  >
    <StateError
      class="my-auto"
      :title="t(errorKey)"
      :action-label="errorActionLabel"
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

  <InsufficientBalanceDialog
    v-if="topupPromptOpen"
    :title="t('settlement.pay.insufficientTitle')"
    :description="
      t('settlement.pay.insufficientDescription', {
        amount: points(payableAmount ?? '0'),
      })
    "
    :later-label="t('settlement.pay.insufficientLater')"
    :topup-label="t('settlement.pay.insufficientTopup')"
    @close="closeTopupPrompt"
    @topup="goToTopup"
  />
</template>
