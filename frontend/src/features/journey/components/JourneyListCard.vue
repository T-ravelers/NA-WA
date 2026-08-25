<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { IconChevronRight } from '@tabler/icons-vue'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'
import TicketStamp from '@/shared/ui/TicketStamp.vue'

import type { JourneySummary } from '../api/journeyApi'
import { formatJourneyDate, type JourneyListStatus } from '../model/journeyStatus'

interface Props {
  journey: JourneySummary
  status: JourneyListStatus
  /**
   * 지금 실제로 여행 중인가. 도장을 찍을지 정한다.
   *
   * 🔴 `status`로 대신하지 않는다. 그쪽은 탭 구분이라 `ongoing`에 **예정 여정도 들어간다**
   * (`getJourneyStatus`가 `endDate`만 본다). 시작 전인 여정에 `ON TRIP`이 찍히면 사용자에게
   * 사실이 아닌 상태를 말하게 된다.
   */
  onTrip?: boolean
  /**
   * 이 여정의 최종 리포트 id. 없으면 `null`이다.
   *
   * 목록 응답에는 리포트 정보가 없어서 화면이 report feature에서 받아 내려준다
   * (`model/reportIntegration.ts`). 카드는 값만 받고 어디서 왔는지 알지 않는다.
   */
  reportId?: number | null
}

const { journey, status, onTrip = false, reportId = null } = defineProps<Props>()

const { locale, t } = useI18n()

/*
 * `status`는 탭 구분이라 `ongoing`에 예정 여정도 들어간다. 도장과 같은 실제 기간 판정을
 * 함께 써야 시안의 `In progress` / `Scheduled`를 정확히 구분할 수 있다.
 */
const statusTone = computed(() => {
  if (status === 'past') return 'neutral'
  return onTrip ? 'ongoing' : 'scheduled'
})
const statusLabel = computed(() => {
  if (status === 'past') return t('journey.list.past')
  return onTrip ? t('journey.list.inProgress') : t('journey.list.scheduled')
})

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
      class="h-full"
    >
      <template #body>
        <!--
          `size-full`로 칸을 채운다. 커버 칸은 `body-size`가 154px로 고정돼 있어 사진
          비율이 카드마다 달라질 일이 없다.

          빈 문자열과 `undefined`도 `AppImage`가 자리표시로 보낸다. 형제인 `EventCard`의
          썸네일과 같은 규칙이다.
        -->
        <AppImage
          :src="journey.coverImageUrl"
          alt=""
          class="size-full object-cover"
          loading="lazy"
        />

        <div class="absolute top-3 left-3">
          <AppBadge
            :tone="statusTone"
            dot
          >
            {{ statusLabel }}
          </AppBadge>
        </div>
      </template>

      <template #stub>
        <!--
          바깥을 통째로 링크로 감싸지 않는다. `View report`가 그 안에 들어가면 중첩
          인터랙티브가 된다. 제목·날짜·항목 수까지만 상세로 가는 링크이고, 리포트 링크와
          도장은 형제로 둔다.
        -->
        <div class="flex min-h-40 flex-col gap-2.5 p-4">
          <RouterLink
            :to="{ name: 'journey-detail', params: { tripId: journey.tripId } }"
            class="flex flex-col gap-2.5 transition-transform motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-on-paper active:scale-[0.98] motion-reduce:active:scale-100"
          >
            <h3 class="break-words font-display text-trip-ticket-title uppercase">
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

          <!--
            리포트 링크와 도장은 카드 바닥에 붙인다. 둘 다 없는 여정이 있으므로 행 자체는
            비어 있을 수 있고, 그때도 `mt-auto`가 위 내용을 위로 밀어 카드 높이가 흔들리지
            않는다.
          -->
          <div class="mt-auto flex min-h-11 items-center justify-between gap-2">
            <RouterLink
              v-if="reportId !== null"
              :to="{ name: 'report-detail', params: { reportId } }"
              class="inline-flex min-h-11 min-w-0 items-center gap-1 rounded-sm px-0.5 text-title-sm text-on-paper transition-transform motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-on-paper active:scale-[0.98] motion-reduce:active:scale-100"
            >
              <span class="truncate">{{ t('journey.list.viewReport') }}</span>
              <IconChevronRight
                :size="18"
                :stroke-width="2"
                class="shrink-0"
                aria-hidden="true"
              />
            </RouterLink>
            <span v-else></span>

            <TicketStamp
              v-if="onTrip"
              :label="t('journey.list.onTrip')"
            />
          </div>
        </div>
      </template>
    </AppTicket>
  </li>
</template>
