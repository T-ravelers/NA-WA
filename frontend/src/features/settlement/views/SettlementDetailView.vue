<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { settlementGateway } from '../api/settlementGateway'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import {
  clearSettlementPaymentIdempotencyKey,
  resolveSettlementPaymentIdempotencyKey,
} from '../model/settlementIdempotency'
import { resolveSettlementError } from '../model/settlementErrors'
import { formatSettlementAmount } from '../model/settlementPresentation'
import { settlementKeys, useSettlementDetail } from '../model/settlementQueries'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const settlementId = computed(() => String(route.params.settlementId))
const detailQuery = useSettlementDetail(() => settlementId.value)
const detail = computed(() => detailQuery.data.value)
const canPay = computed(() => detail.value?.viewer.allowedActions.includes('PAY') ?? false)
const queryErrorKey = computed(() => resolveSettlementError(detailQuery.error.value).messageKey)

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
  },
})
const paymentErrorKey = computed(
  () => resolveSettlementError(paymentMutation.error.value).messageKey,
)

function recoverPayment(): void {
  const recovery = resolveSettlementError(paymentMutation.error.value).recovery
  if (recovery === 'BACK_TO_LIST') {
    void router.push({ name: 'settlements' })
  } else if (recovery === 'REFETCH_DETAIL') {
    void detailQuery.refetch()
  } else {
    paymentMutation.mutate()
  }
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-8">
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
      <div class="mt-8 flex items-center justify-between">
        <h1 class="text-screen-title">{{ detail.gatheringName }}</h1>
        <span class="text-caption text-ink-2">{{ t(`settlement.status.${detail.status}`) }}</span>
      </div>
      <AppCard class="mt-5"
        ><dl class="space-y-3 text-body-sm">
          <div class="flex justify-between">
            <dt class="text-ink-3">{{ t('settlement.detail.requestedBy') }}</dt>
            <dd>{{ detail.requestedBy }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-ink-3">{{ t('settlement.total') }}</dt>
            <dd>{{ formatSettlementAmount(detail.totalAmount) }} P</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-ink-3">{{ t('settlement.detail.yourShare') }}</dt>
            <dd>{{ formatSettlementAmount(detail.viewer.shareAmount) }} P</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-ink-3">{{ t('settlement.detail.payable') }}</dt>
            <dd>{{ formatSettlementAmount(detail.viewer.payableAmount) }} P</dd>
          </div>
        </dl></AppCard
      >
      <AppCard
        v-if="detail.type === 'ITEMIZED'"
        class="mt-4"
        ><p class="text-caption text-ink-3">{{ t('settlement.detail.yourItems') }}</p>
        <ul class="mt-3 space-y-3">
          <li
            v-for="item in detail.viewerItems"
            :key="item.id"
            class="flex justify-between text-body-sm"
          >
            <span>{{ item.name }} · {{ item.allocatedQuantity }}</span
            ><strong>{{ formatSettlementAmount(item.allocatedAmount) }} P</strong>
          </li>
        </ul></AppCard
      >
      <StateError
        v-if="paymentMutation.isError.value"
        class="py-4"
        :title="t(paymentErrorKey)"
        @retry="recoverPayment"
      />
      <AppButton
        v-if="canPay"
        data-action="pay"
        class="mt-auto"
        block
        variant="settle"
        :loading="paymentMutation.isPending.value"
        @click="paymentMutation.mutate()"
        >{{ t('settlement.completePayment') }}</AppButton
      >
      <AppButton
        v-else
        class="mt-auto"
        block
        variant="secondary"
        @click="router.push({ name: 'settlements' })"
        >{{ t('settlement.backToList') }}</AppButton
      >
    </template>
  </section>
</template>
