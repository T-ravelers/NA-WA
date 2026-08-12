<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'

import type { JourneySummary } from '../api/journeyApi'
import { formatJourneyDate, type JourneyListStatus } from '../model/journeyStatus'

interface Props {
  journey: JourneySummary
  status: JourneyListStatus
  statusLabel: string
}

const { journey, status, statusLabel } = defineProps<Props>()

const { locale } = useI18n()
</script>

<template>
  <li>
    <!--
      시안 J1의 여정 카드는 상단 커버 154px + 하단 종이 스텁으로 나뉜 티켓이다.
      목록 API(`JourneySummary`)는 `tripId·title·startDate·endDate`만 준다. 시안의 커버
      사진·인원수·소비영역 칩·이벤트 수·`View report`는 받쳐 줄 값이 없으므로 넣지 않고,
      커버 자리만 `ImagePlaceholder`로 채운다.

      티켓 바깥을 링크로 감싸지 않는다(`shared/ui/README.md`). 중첩 인터랙티브가 되지
      않도록 탭 동작은 스텁 안에 둔다.
    -->
    <AppTicket
      :body-size="154"
      tone="paper"
    >
      <template #body>
        <ImagePlaceholder />

        <div class="absolute top-3 left-3">
          <AppBadge
            :tone="status === 'ongoing' ? 'ongoing' : 'neutral'"
            dot
          >
            {{ statusLabel }}
          </AppBadge>
        </div>
      </template>

      <template #stub>
        <RouterLink
          :to="{ name: 'journey-detail', params: { tripId: journey.tripId } }"
          class="flex flex-col gap-2.5 p-4 focus-visible:outline-2 focus-visible:outline-on-paper"
        >
          <h3 class="truncate font-display text-trip-ticket-title uppercase">
            {{ journey.title }}
          </h3>
          <p class="text-body-sm font-medium tabular-nums text-on-paper">
            <time :datetime="journey.startDate">{{
              formatJourneyDate(journey.startDate, locale)
            }}</time>
            <span aria-hidden="true"> – </span>
            <time :datetime="journey.endDate">{{
              formatJourneyDate(journey.endDate, locale)
            }}</time>
          </p>
        </RouterLink>
      </template>
    </AppTicket>
  </li>
</template>
