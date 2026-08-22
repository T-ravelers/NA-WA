<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateError from '@/shared/ui/StateError.vue'

import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementInlineLoading from '../components/SettlementInlineLoading.vue'
import SettlementListCard from '../components/SettlementListCard.vue'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import {
  COMPLETED_PREVIEW_COUNT,
  resolveSide,
  splitIntoSections,
  type SettlementSide,
} from '../model/settlementList'
import { useSettlements } from '../model/settlementQueries'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
/**
 * 어느 쪽 목록을 보고 있는지는 주소에 남긴다.
 *
 * 요청을 보낸 직후나 상세·전체 내역에서 돌아올 때 열려 있어야 할 쪽이 정해져 있는데,
 * 토글이 컴포넌트 안에만 있으면 돌아오는 화면이 그것을 알 방법이 없다.
 */
const side = computed<SettlementSide>(() => resolveSide(route.query.side))
const settlementQuery = useSettlements()

const sections = computed(() => splitIntoSections(settlementQuery.data.value?.[side.value] ?? []))
const completedPreview = computed(() => sections.value.completed.slice(0, COMPLETED_PREVIEW_COUNT))
const errorKey = computed(() => resolveSettlementError(settlementQuery.error.value).messageKey)
const paying = computed(() => side.value === 'received')

function selectSide(next: SettlementSide): void {
  void router.replace({ query: { ...route.query, side: next } })
}

function open(settlementId: string): void {
  void router.push({
    name: 'settlement-detail',
    params: { settlementId },
    query: { side: side.value },
  })
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      data-testid="settlement-home"
      :title="t('settlement.title')"
      :back-label="t('settlement.backToWallet')"
      @back="router.push({ name: 'wallet' })"
    />
    <SegmentedControl
      class="mt-7"
      :model-value="side"
      :label="t('settlement.title')"
      :options="[
        { value: 'received', label: t('settlement.toPay') },
        { value: 'sent', label: t('settlement.toCollect') },
      ]"
      @update:model-value="selectSide($event as SettlementSide)"
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

    <template v-else>
      <h2 class="mt-8 text-title">{{ t('settlement.section.ongoing') }}</h2>
      <SettlementEmptyState
        v-if="sections.ongoing.length === 0"
        class="mt-2"
        :title="
          t(
            paying
              ? 'settlement.list.emptyOngoingPayTitle'
              : 'settlement.list.emptyOngoingCollectTitle',
          )
        "
        :description="
          t(paying ? 'settlement.list.emptyOngoingPay' : 'settlement.list.emptyOngoingCollect')
        "
      />
      <ul
        v-else
        class="mt-4 space-y-3"
      >
        <li
          v-for="settlement in sections.ongoing"
          :key="settlement.id"
        >
          <SettlementListCard
            :settlement="settlement"
            :side="side"
            @open="open(settlement.id)"
          />
        </li>
      </ul>

      <div class="mt-10 flex items-center justify-between gap-3">
        <h2 class="text-title">{{ t('settlement.section.completed') }}</h2>
        <button
          v-if="sections.completed.length > 0"
          type="button"
          data-action="view-all"
          class="min-h-11 text-body-sm text-ink-2 underline underline-offset-4"
          :aria-label="t('settlement.history.viewAllLabel')"
          @click="router.push({ name: 'settlement-history', query: { side } })"
        >
          {{ t('settlement.viewAll') }}
        </button>
      </div>
      <SettlementEmptyState
        v-if="completedPreview.length === 0"
        class="mt-2"
        :title="
          t(
            paying
              ? 'settlement.list.emptyCompletedPayTitle'
              : 'settlement.list.emptyCompletedCollectTitle',
          )
        "
        :description="
          t(paying ? 'settlement.list.emptyCompletedPay' : 'settlement.list.emptyCompletedCollect')
        "
      />
      <ul
        v-else
        class="mt-4 space-y-2"
      >
        <li
          v-for="settlement in completedPreview"
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
    </template>

    <AppButton
      class="mt-auto"
      block
      variant="settle"
      data-testid="settlement-start"
      @click="router.push({ name: 'settlement-new' })"
      >{{ t('settlement.start') }}</AppButton
    >
  </section>
</template>
