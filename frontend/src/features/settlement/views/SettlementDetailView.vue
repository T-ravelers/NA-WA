<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import SettlementCollectionCard from '../components/SettlementCollectionCard.vue'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import SettlementReceiptSheet from '../components/SettlementReceiptSheet.vue'
import SettlementTransactionCard from '../components/SettlementTransactionCard.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
import { useSettlementReceiptViewer } from '../composables/useSettlementReceipt'
import { resolveSettlementError } from '../model/settlementErrors'
import { resolveSide } from '../model/settlementList'
import { useSettlementDetail } from '../model/settlementQueries'

/**
 * 정산 상세.
 *
 * 요청자와 참여자가 서로 다른 것을 알아야 한다. 요청자는 누가 냈는지를, 참여자는
 * 누구에게 얼마를 보내야 하는지를 본다. 역할 판정은 서버가 준 `viewer.role`로만 한다.
 * 표시용 이름은 동명이인이 있을 수 있어 본인 판정에 쓰지 않는다.
 *
 * 목록에서 어느 쪽 토글로 들어왔는지는 `query.side`로 받아 되돌려준다. 주소로 바로
 * 들어와 그 값이 없으면 요청자는 받을 목록, 참여자는 낼 목록으로 나간다.
 */
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const points = useSettlementPoints()

const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const detail = computed(() => detailQuery.data.value)
const isCreator = computed(() => detail.value?.viewer.role === 'CREATOR')
/** 결제 가능 여부는 서버가 준 허용 동작으로만 판단한다. 금액으로 추론하지 않는다. */
const canPay = computed(() => detail.value?.viewer.allowedActions.includes('PAY') ?? false)
const hasPaid = computed(() => detail.value?.viewer.requestStatus === 'PAID')
const queryErrorKey = computed(() => resolveSettlementError(detailQuery.error.value).messageKey)
const side = computed(() =>
  route.query.side === undefined && isCreator.value ? 'sent' : resolveSide(route.query.side),
)

const receipt = useSettlementReceiptViewer(() => settlementId.value)
const receiptSheetOpen = ref(false)

/*
 * 정산이 열리면 영수증도 함께 받는다.
 *
 * 영수증이 붙어 있는지는 받아 봐야만 알 수 있어서 예전에는 눌러야 보여줬는데, 그러면
 * 붙어 있는 경우(이 기능을 쓰는 이유)에 매번 한 번 더 두드리게 된다. 정산 상세를 여는
 * 김에 사진 한 장을 같이 받고, 없으면 조용히 없다고 두는 편이 낫다.
 *
 * 정산 자체를 못 읽는 사람에게는 요청조차 보내지 않도록 상세가 온 뒤에 받는다.
 */
watch(
  detail,
  (value) => {
    if (value !== undefined) void receipt.load()
  },
  { immediate: true },
)

/** 사진이 이미 손에 있으면 크게 연다. 아직이면 여기서 받아서 연다. */
async function openReceipt(): Promise<void> {
  await receipt.load()
  if (receipt.url.value !== null) {
    receiptSheetOpen.value = true
  }
}

/*
 * 없다는 것이 확인된 자리는 눌리지 않게 한다. 눌러도 아무 일이 없으면 고장으로 보이고,
 * 다시 눌러 봐야 또 없다는 답만 돌아온다. 저장소 장애처럼 다시 해볼 만한 실패는 그대로
 * 누를 수 있게 둔다.
 */
const receiptUnavailable = computed(
  () =>
    receipt.errorKey.value === 'settlement.receipt.error.missing' ||
    receipt.errorKey.value === 'settlement.receipt.error.expired',
)
const receiptMode = computed(() => (receiptUnavailable.value ? 'empty' : 'view'))
/** 없는 것과 기한이 지나 사라진 것은 다른 일이라, 눌리지 않는 자리에서도 구분해 읽어 준다. */
const receiptEmptyLabel = computed(() =>
  receiptUnavailable.value && receipt.errorKey.value !== null
    ? t(receipt.errorKey.value)
    : undefined,
)

function backToList(): void {
  void router.push({ name: 'settlements', query: { side: side.value } })
}

/**
 * 결제 화면으로 넘어간다.
 *
 * `replace`로 대체해야 결제 뒤 되돌아온 상세가 스택에 두 번 쌓이지 않는다. 진입 의사는
 * 히스토리 상태로 실어 보내, 결제 화면이 주소로 열린 경우와 구분하게 한다.
 */
function startPayment(): void {
  void router.replace({
    name: 'settlement-pay',
    params: { settlementId: settlementId.value },
    query: { side: side.value },
    state: { confirmed: true },
  })
}
</script>

<template>
  <!-- 하단 고정 내비게이션이 CTA를 덮지 않도록 목록 화면과 같은 여백을 둔다. -->
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      :title="t('settlement.title')"
      :back-label="t('settlement.back')"
      @back="backToList"
    />
    <StateLoading
      v-if="detailQuery.isPending.value"
      class="mt-8"
      :label="t('settlement.detail.loading')"
    />
    <StateError
      v-else-if="detailQuery.isError.value || detail === undefined"
      class="my-auto"
      :title="t(queryErrorKey)"
      @retry="detailQuery.refetch()"
    />
    <template v-else>
      <!-- 약속 이름은 바로 아래 거래 카드가 이미 말한다. 여기서 한 번 더 쓰지 않는다. -->
      <div class="mt-8">
        <AppBadge :tone="detail.status === 'COMPLETED' ? 'completed' : 'pending'">
          {{ t(`settlement.status.${detail.status}`) }}
        </AppBadge>
      </div>

      <SettlementTransactionCard
        class="mt-4"
        :gathering-name="detail.gatheringName"
        :amount="detail.totalAmount"
        :payer-name="detail.paidBy"
        :receipt-mode="receiptMode"
        :receipt-empty-label="receiptEmptyLabel"
        :receipt-url="receipt.url.value"
        :receipt-pending="receipt.pending.value"
        @receipt-open="openReceipt"
      />
      <p
        v-if="receipt.errorKey.value !== null"
        class="mt-2 text-caption text-ink-3"
        role="status"
      >
        {{ t(receipt.errorKey.value) }}
      </p>

      <AppCard
        v-if="!isCreator"
        class="mt-4"
      >
        <p class="text-caption text-ink-3">{{ t('settlement.detail.sendTo') }}</p>
        <p class="mt-1 text-title">{{ detail.requestedBy }}</p>
        <!--
          크게 보여주는 값은 지금 내야 할 금액이다. 부담금을 그대로 두면 이미 낸 뒤에도
          전액을 보내라고 말하게 되어 아래 "Pay completed" 버튼과 어긋난다.
        -->
        <p class="mt-4 text-caption text-ink-3">{{ t('settlement.detail.payableNow') }}</p>
        <p class="mt-1 text-data-xl">{{ points(detail.viewer.payableAmount) }}</p>
        <dl class="mt-4 flex justify-between gap-3 text-body-sm">
          <dt class="text-ink-3">{{ t('settlement.detail.yourShare') }}</dt>
          <dd>{{ points(detail.viewer.shareAmount) }}</dd>
        </dl>
      </AppCard>

      <AppCard class="mt-4">
        <dl class="space-y-3 text-body-sm">
          <div
            v-if="isCreator"
            class="flex justify-between gap-3"
          >
            <dt class="text-ink-3">{{ t('settlement.detail.yourShare') }}</dt>
            <dd>{{ points(detail.viewer.shareAmount) }}</dd>
          </div>
          <div class="flex justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.total') }}</dt>
            <dd>{{ points(detail.totalAmount) }}</dd>
          </div>
          <div
            v-if="!isCreator"
            class="flex justify-between gap-3"
          >
            <dt class="text-ink-3">{{ t('settlement.detail.requestedBy') }}</dt>
            <dd>{{ detail.requestedBy }}</dd>
          </div>
          <div
            v-if="detail.transactionId !== undefined"
            class="flex justify-between gap-3"
          >
            <dt class="text-ink-3">{{ t('settlement.detail.transactionId') }}</dt>
            <dd class="truncate">{{ detail.transactionId }}</dd>
          </div>
        </dl>
      </AppCard>

      <!-- 항목별 내역은 서버가 생성자에게도 내려준다. 역할로 가리지 않는다. -->
      <AppCard
        v-if="detail.type === 'ITEMIZED' && detail.viewerItems.length > 0"
        class="mt-4"
      >
        <p class="text-caption text-ink-3">{{ t('settlement.detail.yourItems') }}</p>
        <ul class="mt-3 space-y-3">
          <li
            v-for="item in detail.viewerItems"
            :key="item.id"
            class="flex justify-between gap-3 text-body-sm"
          >
            <span class="min-w-0 truncate">{{ item.name }} · {{ item.allocatedQuantity }}</span>
            <strong class="shrink-0">{{ points(item.allocatedAmount) }}</strong>
          </li>
        </ul>
      </AppCard>

      <!--
        누가 냈는지는 돈을 받을 사람에게만 온다. 낼 사람에게는 아예 오지 않으므로 이
        자리가 비고, 화면이 역할로 다시 가릴 필요가 없다.

        목록이 비었는지는 따로 보지 않는다. 서버가 정산을 만들 때 자기 말고 낼 사람이
        적어도 한 명은 있어야 통과시키므로(EqualSettlementCreator의
        validatePayerAndPendingAmounts, 품목별도 같은 검사를 쓴다) 목록이 빈 채로 오는
        정산은 만들어지지 않는다. 여기서 한 번 더 세면 "볼 수 없다"(오지 않음)와 "받을
        사람이 없다"(빈 목록)가 같은 결과가 되어, 굳이 둘을 갈라 놓은 뜻이 사라진다.
      -->
      <SettlementCollectionCard
        v-if="detail.collection !== undefined"
        class="mt-4"
        :collection="detail.collection"
      />

      <div class="mt-auto pt-8">
        <AppButton
          v-if="canPay"
          data-action="pay"
          block
          variant="settle"
          @click="startPayment"
          >{{
            t('settlement.detail.pay', { amount: points(detail.viewer.payableAmount) })
          }}</AppButton
        >
        <AppButton
          v-else-if="hasPaid"
          data-action="pay-completed"
          block
          disabled
          variant="secondary"
          >{{ t('settlement.detail.payCompleted') }}</AppButton
        >
        <AppButton
          v-else-if="isCreator"
          block
          variant="secondary"
          @click="backToList"
          >{{ t('settlement.detail.backToCollect') }}</AppButton
        >
        <AppButton
          v-else
          block
          variant="secondary"
          @click="backToList"
          >{{ t('settlement.backToList') }}</AppButton
        >
      </div>
    </template>

    <SettlementReceiptSheet
      v-if="receiptSheetOpen && receipt.url.value !== null"
      :url="receipt.url.value"
      @close="receiptSheetOpen = false"
    />
  </section>
</template>
