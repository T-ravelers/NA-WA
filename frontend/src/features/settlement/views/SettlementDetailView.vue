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
import { useSettlementDetail } from '../model/settlementQueries'

/**
 * 정산 상세.
 *
 * 요청자와 참여자가 서로 다른 것을 알아야 한다. 요청자는 누가 냈는지를, 참여자는
 * 누구에게 얼마를 보내야 하는지를 본다. 역할 판정은 서버가 준 `viewer.role`로만 한다.
 * 표시용 이름은 동명이인이 있을 수 있어 본인 판정에 쓰지 않는다.
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

function startPayment(): void {
  void router.push({ name: 'settlement-pay', params: { settlementId: settlementId.value } })
}
</script>

<template>
  <!-- 하단 고정 내비게이션이 CTA를 덮지 않도록 목록 화면과 같은 여백을 둔다. -->
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      :title="t('settlement.title')"
      :back-label="t('settlement.back')"
      @back="router.push({ name: 'settlements' })"
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
        <p class="mt-4 text-caption text-ink-3">{{ t('settlement.detail.sendAmount') }}</p>
        <p class="mt-1 text-data-xl">{{ points(detail.viewer.shareAmount) }}</p>
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

      <AppCard
        v-if="!isCreator && detail.type === 'ITEMIZED'"
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
          @click="router.push({ name: 'settlements', query: { side: 'sent' } })"
          >{{ t('settlement.detail.backToCollect') }}</AppButton
        >
        <AppButton
          v-else
          block
          variant="secondary"
          @click="router.push({ name: 'settlements' })"
          >{{ t('settlement.backToList') }}</AppButton
        >
      </div>
    </template>
  </section>
</template>
