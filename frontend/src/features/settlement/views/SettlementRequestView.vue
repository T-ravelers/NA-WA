<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import StateError from '@/shared/ui/StateError.vue'

import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementFlowHeader from '../components/SettlementFlowHeader.vue'
import SettlementInlineLoading from '../components/SettlementInlineLoading.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import { settlementKeys, useSettlementCandidates } from '../model/settlementQueries'
import SettlementCreateView from './SettlementCreateView.vue'

const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const candidatesQuery = useSettlementCandidates()
const step = ref(1)
const createView = useTemplateRef('createView')
const errorKey = computed(() => resolveSettlementError(candidatesQuery.error.value).messageKey)

async function handleComplete(settlementId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: settlementKeys.candidates() }),
    queryClient.invalidateQueries({ queryKey: settlementKeys.lists() }),
  ])
  await router.push({ name: 'settlement-detail', params: { settlementId } })
}

function cancel(): void {
  void router.push({ name: 'settlements' })
}
function back(): void {
  if (createView.value !== null) createView.value.back()
  else cancel()
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-14 pb-8">
    <SettlementFlowHeader
      :current="step"
      :back-label="t('settlement.back')"
      @back="back"
    />
    <SettlementInlineLoading
      v-if="candidatesQuery.isPending.value"
      class="mt-8"
      :label="t('settlement.create.loadingCandidates')"
    />
    <StateError
      v-else-if="candidatesQuery.isError.value"
      class="my-auto"
      :title="t(errorKey)"
      @retry="candidatesQuery.refetch()"
    />
    <SettlementEmptyState
      v-else-if="candidatesQuery.data.value?.length === 0"
      class="flex-1"
      :title="t('settlement.create.noPaymentsTitle')"
      :description="t('settlement.create.noPaymentsDescription')"
    />
    <SettlementCreateView
      v-else
      ref="createView"
      v-model:step="step"
      :candidates="candidatesQuery.data.value ?? []"
      @complete="handleComplete"
      @cancel="cancel"
      @refresh-candidates="candidatesQuery.refetch()"
    />
  </section>
</template>
