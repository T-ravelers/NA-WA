<script setup lang="ts">
import { IconArrowsExchange } from '@tabler/icons-vue'
import { computed } from 'vue'

import AppTicket from '@/shared/ui/AppTicket.vue'

import { formatMoney } from './format'
import type { ReportKpiCardProps } from './types'

/**
 * 총 지출·일 평균 KPI.
 *
 * 시안 R4의 `Analysis` 블록이다. 절취선과 노치가 있는 종이톤 조형이므로 `AppCard`가
 * 아니라 `AppTicket`을 쓴다. 노치와 절취선은 `AppTicket`이 소유하므로 여기서 다시
 * 그리지 않는다.
 *
 * **시안과 좌우가 뒤집혀 있다.** 시안은 금액이 왼쪽, 아이콘 칸이 오른쪽인데 `AppTicket`의
 * 가로형은 `bodySize`가 **왼쪽** 고정 폭이고 stub이 남은 폭을 먹는다. 금액을 고정 폭에
 * 넣으면 자릿수가 늘어날 때 절취선이 금액 위로 올라오므로, 고정 폭 쪽에 아이콘을 두고
 * 금액을 가변 폭에 둔다. 좌우를 시안대로 되돌리려면 `AppTicket`에 stub 고정 폭 prop이
 * 필요한데 그것은 `shared/ui` 변경이라 이 이슈 범위 밖이다.
 *
 * 표시 문자열은 전부 props로 받는다. `useI18n`을 쓰면 상위(#153)의 i18n 등록 없이는
 * 렌더되지 않아 props-only 계약이 깨진다.
 */
const { data, heading = undefined, locale = 'en' } = defineProps<ReportKpiCardProps>()

/** 아이콘 칸 폭. 시안 실측 64px이다. 노치 지름 12도 시안 실측값이다. */
const ICON_COLUMN_WIDTH = 64

const totalText = computed(() => formatMoney(data.totalSpent, data.currency, locale))
const dailyAverageText = computed(() => formatMoney(data.dailyAverage, data.currency, locale))
</script>

<template>
  <section class="flex flex-col gap-3">
    <h2
      v-if="heading !== undefined"
      class="font-display text-section-header uppercase text-ink"
    >
      {{ heading }}
    </h2>

    <AppTicket
      orientation="horizontal"
      :body-size="ICON_COLUMN_WIDTH"
      :notch-size="12"
      tone="paper"
    >
      <template #body>
        <!-- 아이콘은 장식이다. 값은 모두 stub 쪽 텍스트로 읽힌다. -->
        <div
          aria-hidden="true"
          class="flex size-full items-center justify-center"
        >
          <span class="flex size-11 items-center justify-center rounded-pill border-2">
            <IconArrowsExchange
              :size="20"
              :stroke-width="1.75"
            />
          </span>
        </div>
      </template>

      <template #stub>
        <dl class="flex flex-col gap-3 p-5">
          <div class="flex flex-col gap-1">
            <dt class="text-micro uppercase text-on-paper/65">{{ totalLabel }}</dt>
            <dd class="font-display text-data-xl tabular-nums text-on-paper">{{ totalText }}</dd>
          </div>
          <div class="flex flex-col gap-1">
            <dt class="text-micro uppercase text-on-paper/65">{{ dailyAverageLabel }}</dt>
            <dd class="text-title tabular-nums text-on-paper">{{ dailyAverageText }}</dd>
          </div>
        </dl>
      </template>
    </AppTicket>
  </section>
</template>
