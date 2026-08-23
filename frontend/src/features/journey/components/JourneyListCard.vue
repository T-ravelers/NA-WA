<script setup lang="ts">
import { computed } from 'vue'
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

const { locale, t } = useI18n()

/** 값이 0인 종류는 빼고 담는다. 둘 다 0이면 빈 배열이 되어 줄이 통째로 사라진다. */
const itemCounts = computed(() => {
  const counts: string[] = []

  if (journey.eventCount > 0) {
    counts.push(t('journey.list.eventCount', { count: journey.eventCount }))
  }

  if (journey.placeCount > 0) {
    counts.push(t('journey.list.placeCount', { count: journey.placeCount }))
  }

  return counts
})
</script>

<template>
  <li>
    <!--
      시안 J1의 여정 카드는 상단 커버 154px + 하단 종이 스텁으로 나뉜 티켓이다.

      커버는 목록 응답의 `coverImageUrl`을 쓴다(#424). 타임라인에서 가장 먼저 나오는,
      썸네일이 있는 항목의 사진이다. 담긴 항목이 없거나 모두 썸네일이 없으면 `null`로
      오고 그때만 `ImagePlaceholder`로 채운다. 커버는 썸네일이 있는 항목까지 건너뛰며
      찾으므로 자리표시가 자주 나오지는 않지만, 아직 아무것도 담지 않은 여정에서는
      반드시 나온다.

      시안의 인원수·소비영역 칩·`View report`는 아직 목록 API에 받쳐 줄 값이 없어 넣지
      않는다.

      항목 수는 시안이 `12 events`로 통칭했지만 API가 EVENT와 PLACE를 따로 주므로
      분리해서 적는다. 0인 쪽은 숨긴다 — 장소만 담은 여정에 `0 events`가 붙으면
      비어 있다는 인상을 준다. 둘 다 0이면 줄 자체를 감춘다.

      티켓 바깥을 링크로 감싸지 않는다(`shared/ui/README.md`). 중첩 인터랙티브가 되지
      않도록 탭 동작은 스텁 안에 둔다.
    -->
    <AppTicket
      :body-size="154"
      tone="paper"
    >
      <template #body>
        <!--
          `size-full`로 칸을 채운다. 커버 칸은 `body-size`가 154px로 고정돼 있어 사진
          비율이 카드마다 달라질 일이 없다.

          조건은 `!== null`이 아니라 truthy다. 엄격 비교로 두면 빈 문자열과 `undefined`가
          사진 갈래로 새어 들어가 `src` 없는 `<img>`가 그려지고, 자리표시로 떨어지지
          않는다. 형제인 `EventCard`의 썸네일 조건과 같은 방식이다.
        -->
        <img
          v-if="journey.coverImageUrl"
          :src="journey.coverImageUrl"
          alt=""
          class="size-full object-cover"
          loading="lazy"
        />
        <ImagePlaceholder v-else />

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
          <p
            v-if="itemCounts.length > 0"
            class="text-caption font-semibold tabular-nums text-on-paper/65"
          >
            {{ itemCounts.join(' · ') }}
          </p>
        </RouterLink>
      </template>
    </AppTicket>
  </li>
</template>
