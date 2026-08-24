<script setup lang="ts">
import { computed, ref, useTemplateRef, watch } from 'vue'
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
const submitting = ref(false)
const createView = useTemplateRef('createView')
const errorKey = computed(() => resolveSettlementError(candidatesQuery.error.value).messageKey)

/**
 * 위저드를 한 번이라도 열었는지 기억한다.
 *
 * 후보 목록은 창 포커스만 돌아와도 다시 조회된다. 그 결과로 로딩·오류·빈 화면을 다시 띄우면
 * 작성 중이던 입력과 사용자가 읽어야 할 실패 이유가 함께 사라진다. 그래서 이 세 화면은
 * 처음 열 때만 쓰고, 그 뒤에는 위저드를 그대로 둔다.
 */
const wizardOpened = ref(false)
watch(
  () => candidatesQuery.data.value,
  (candidates) => {
    if (candidates !== undefined && candidates.length > 0) wizardOpened.value = true
  },
  { immediate: true },
)

async function handleComplete(settlementId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: settlementKeys.candidates() }),
    queryClient.invalidateQueries({ queryKey: settlementKeys.lists() }),
  ])
  // 요청서를 다시 열 수 없도록 되돌아가기 대상에서 지운다.
  await router.replace({ name: 'settlement-requested', params: { settlementId } })
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
  <section
    class="flex flex-col flex-1 pt-6 pb-8"
    :class="submitting ? '' : 'px-screen pt-14 pb-32'"
  >
    <SettlementFlowHeader
      v-if="!submitting"
      :current="step"
      :title="t('settlement.create.title')"
      :back-label="t('settlement.back')"
      @back="back"
    />
    <SettlementInlineLoading
      v-if="!wizardOpened && candidatesQuery.isPending.value"
      class="mt-8"
      :label="t('settlement.create.loadingCandidates')"
    />
    <StateError
      v-else-if="!wizardOpened && candidatesQuery.isError.value"
      class="my-auto"
      :title="t(errorKey)"
      @retry="candidatesQuery.refetch()"
    />
    <SettlementEmptyState
      v-else-if="!wizardOpened && candidatesQuery.data.value?.length === 0"
      class="flex-1"
      data-testid="settlement-no-payments"
      :title="t('settlement.create.noPaymentsTitle')"
      :description="t('settlement.create.noPaymentsDescription')"
    />
    <SettlementCreateView
      v-else
      ref="createView"
      v-model:step="step"
      :candidates="candidatesQuery.data.value ?? []"
      @submitting-change="submitting = $event"
      @complete="handleComplete"
      @cancel="cancel"
      @refresh-candidates="candidatesQuery.refetch()"
    />
  </section>
</template>
