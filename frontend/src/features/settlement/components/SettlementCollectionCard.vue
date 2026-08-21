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
        <!--
          사진을 아직 다루지 않는 자리라 이름 첫 글자를 대신 세운다.

          면은 surface-3다. surface-2는 이름이 말하듯 입력칸·세그먼트 트랙의 면이고, 카드
          위에 얹히는 자리는 surface-3다. 값으로도 그렇다 — surface-2는 카드 면과 거의 같은
          값이라 동그라미가 배경에 묻혀 글자만 떠 있는 것처럼 보인다.

          아주 좁은 화면(폴더블 커버 등)에서는 접는다. 한 줄에 이름·금액·상태가 다 들어가야
          하는데 자리가 모자라면 이름부터 잘려서, 정작 "누가 냈나"를 읽을 수 없게 된다. 이
          동그라미는 옆에 그대로 있는 이름의 첫 글자를 되풀이할 뿐이라 접어도 잃는 것이 없다.

          기준을 330px로 잡은 것은 이 줄의 산수에서 나왔다. 이름을 뺀 나머지가 먼저 자리를
          가져간다 — 동그라미 36 + 고정한 상태 칸 72 + 사이 간격 12 셋 = 144px이고, 화면
          좌우 여백 20씩과 카드 안쪽 여백 16씩을 더하면 이름과 금액이 나눠 쓸 폭은 화면
          폭에서 216px을 뺀 만큼이다. 폴더블 커버 폭(280px)에서는 그 나머지가 금액만으로
          거의 차서 이름이 "T.."가 됐다. 330px이 이름이 읽히기 시작하는 폭이고, 그 아래에서는
          동그라미와 그 간격(48px)을 접어 이름에 돌려주고 상태 칸도 배지 제 폭만 쓰게 둔다.

          이 저장소에서 임의 폭을 쓴 첫 자리다. 좁은 화면 기준을 토큰으로 세우는 일은 V2
          트랙(#326)이 잡고 있어 여기서 이름 있는 브레이크포인트를 만들지 않았다. 그 기준이
          정해지면 이 두 곳의 330px을 함께 걷는다.
        -->
        <span
          class="hidden size-9 shrink-0 items-center justify-center rounded-pill bg-surface-3 text-body-sm text-ink min-[330px]:flex"
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

          단 아주 좁은 화면에서는 고정을 푼다. 자리가 없을 때까지 줄맞춤을 지키면 그 대가를
          이름이 치른다. 몇 픽셀의 줄맞춤보다 이름을 읽는 쪽이 먼저다.
        -->
        <span class="flex shrink-0 justify-end min-[330px]:w-18">
          <AppBadge :tone="participant.requestStatus === 'PAID' ? 'completed' : 'pending'">
            {{ t(`settlement.detail.participantRequestStatus.${participant.requestStatus}`) }}
          </AppBadge>
        </span>
      </li>
    </ul>
  </AppCard>
</template>
