<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import SettlementTransactionCard from '../components/SettlementTransactionCard.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
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
      />

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
        TODO: 참여자별 납부 현황. 상세 응답에 참여자 배열이 없어 아직 채울 수 없다.
        서버가 참여자와 납부 상태를 내려주면 이 자리에 목록을 그린다.
      -->
      <AppCard
        v-if="isCreator"
        class="mt-4"
      >
        <p class="text-caption text-ink-3">{{ t('settlement.detail.participantStatus') }}</p>
        <p
          class="mt-2 text-body-sm text-ink-3"
          data-testid="participant-status-placeholder"
        >
          {{ t('settlement.detail.participantStatusPending') }}
        </p>
      </AppCard>

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
  </section>
</template>
