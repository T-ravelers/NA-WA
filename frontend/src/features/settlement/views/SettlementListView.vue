<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateError from '@/shared/ui/StateError.vue'

import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementInlineLoading from '../components/SettlementInlineLoading.vue'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import { formatSettlementAmount } from '../model/settlementPresentation'
import { useSettlements } from '../model/settlementQueries'

const router = useRouter()
const { t } = useI18n()
const tab = ref<'received' | 'sent'>('received')
const settlementQuery = useSettlements()
const items = computed(() => settlementQuery.data.value?.[tab.value] ?? [])
const errorKey = computed(() => resolveSettlementError(settlementQuery.error.value).messageKey)
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      :title="t('settlement.title')"
      :back-label="t('settlement.backToWallet')"
      @back="router.push({ name: 'wallet' })"
    />
    <SegmentedControl
      class="mt-7"
      :model-value="tab"
      :label="t('settlement.title')"
      :options="[
        { value: 'received', label: t('settlement.received') },
        { value: 'sent', label: t('settlement.sent') },
      ]"
      @update:model-value="tab = $event as 'received' | 'sent'"
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
    <SettlementEmptyState
      v-else-if="items.length === 0"
      class="flex-1"
      :title="
        t(
          tab === 'received'
            ? 'settlement.list.emptyReceivedTitle'
            : 'settlement.list.emptySentTitle',
        )
      "
      :description="
        t(tab === 'received' ? 'settlement.list.emptyReceived' : 'settlement.list.emptySent')
      "
    />
    <ul
      v-else
      class="mt-6 space-y-3"
    >
      <li
        v-for="item in items"
        :key="item.id"
      >
        <button
          type="button"
          :data-settlement-id="item.id"
          class="w-full rounded-sm bg-surface-1 p-4 text-left"
          @click="router.push({ name: 'settlement-detail', params: { settlementId: item.id } })"
        >
          <span class="flex items-center justify-between"
            ><strong>{{ item.title }}</strong
            ><span class="text-caption text-ink-2">{{
              t(`settlement.status.${item.status}`)
            }}</span></span
          ><span class="mt-3 flex justify-between text-body-sm"
            ><span>{{ t(`settlement.type.${item.type}`) }}</span
            ><strong>{{ formatSettlementAmount(item.totalAmount) }} P</strong></span
          >
        </button>
      </li>
    </ul>
    <AppButton
      class="mt-auto"
      block
      variant="settle"
      @click="router.push({ name: 'settlement-new' })"
      >{{ t('settlement.start') }}</AppButton
    >
  </section>
</template>
