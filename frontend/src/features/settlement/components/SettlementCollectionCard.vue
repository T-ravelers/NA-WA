<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { useSettlementPoints } from '../composables/useSettlementPoints'
import type { SettlementCollection } from '../model/settlement'

/**
 * 돈을 받을 사람이 보는 "누가 냈나" 카드.
 *
 * 여기 오르는 사람은 낼 사람뿐이다. 원결제자 본인은 자기에게 보낼 돈이 없어 서버가 빼고
 * 내려주므로 화면에서 다시 거르지 않는다. 누구에게 보여줄지도 서버가 준 역할로 이미
 * 정해져 있어서, 이 컴포넌트는 받은 것을 그리기만 한다.
 */
interface Props {
  collection: SettlementCollection
}

const { collection } = defineProps<Props>()
const { t } = useI18n()
const points = useSettlementPoints()
</script>

<template>
  <AppCard>
    <div class="flex items-baseline justify-between gap-3">
      <p class="text-caption text-ink-3">{{ t('settlement.detail.participantStatus') }}</p>
      <p
        class="shrink-0 text-caption text-ink-2"
        data-testid="collection-summary"
      >
        {{
          t('settlement.detail.participantStatusSummary', {
            paid: collection.paidCount,
            total: collection.totalCount,
          })
        }}
      </p>
    </div>

    <ul class="mt-4 flex flex-col gap-3">
      <li
        v-for="participant in collection.participants"
        :key="participant.id"
        :data-collection-for="participant.id"
        class="flex items-center gap-3"
      >
        <!-- 사진을 아직 다루지 않는 자리라 이름 첫 글자를 대신 세운다. -->
        <span
          class="flex size-9 shrink-0 items-center justify-center rounded-pill bg-surface-2 text-body-sm text-ink"
          aria-hidden="true"
          >{{ participant.initials }}</span
        >
        <span class="min-w-0 flex-1 truncate text-body-sm">{{ participant.name }}</span>
        <span class="shrink-0 text-body-sm text-ink-2">{{ points(participant.shareAmount) }}</span>
        <!--
          상태 칸의 너비를 고정한다.

          배지를 제 글자 너비대로 두면 Paid 줄과 Pending 줄의 배지 폭이 달라서, 그 왼쪽에
          있는 금액이 줄마다 다른 자리에 선다. 금액은 위아래로 훑어 비교하는 값이라 몇
          픽셀만 어긋나도 눈에 걸린다. 칸을 고정하면 배지 글자가 바뀌어도 금액 자리는
          그대로다.
        -->
        <span class="flex w-18 shrink-0 justify-end">
          <AppBadge :tone="participant.requestStatus === 'PAID' ? 'completed' : 'pending'">
            {{ t(`settlement.detail.participantRequestStatus.${participant.requestStatus}`) }}
          </AppBadge>
        </span>
      </li>
    </ul>
  </AppCard>
</template>
