<script setup lang="ts">
import { IconPlus } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import IconOrb from '@/shared/ui/IconOrb.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import JourneyListCard from '../components/JourneyListCard.vue'
import { useJourneyListQuery } from '../composables/useJourneyListQuery'
import { useJourneyReportIntegration } from '../model/reportIntegration'
import { journeyErrorMessageKey } from '../model/journeyErrors'
import { filterJourneysByStatus, type JourneyListTab, useKoreaToday } from '../model/journeyStatus'

const i18n = useI18n()
const { t } = i18n
const router = useRouter()
const activeTab = ref<JourneyListTab>('ongoing')
const today = useKoreaToday()
const hasMessage = (key: string): boolean => i18n.te(key)
const journeyQuery = useJourneyListQuery(true)

/*
 * 여정 목록 응답에는 리포트 정보가 없다(#424). 카드의 `View report`는 report feature가
 * 가진 요약 목록에서 온다 — feature끼리 직접 import하지 않도록 `main.ts`가 주입한
 * 통로를 쓴다(`model/reportIntegration.ts`).
 *
 * 같은 쿼리 키를 리포트 화면과 공유하므로, 그쪽을 다녀왔다면 요청이 새로 나가지 않는다.
 * 실패해도 카드의 나머지는 그대로 그린다 — 리포트 링크만 빠진다.
 */
const { useReportSummariesQuery } = useJourneyReportIntegration()
const reportSummariesQuery = useReportSummariesQuery()
const reportIdByTripId = computed(() => {
  const map = new Map<number, number>()
  for (const summary of reportSummariesQuery.data.value ?? []) {
    map.set(summary.tripId, summary.reportId)
  }
  return map
})

const tabOptions = computed(() => [
  { value: 'ongoing', label: t('journey.list.ongoing') },
  { value: 'past', label: t('journey.list.past') },
])

const journeys = computed(() => journeyQuery.data.value ?? [])
const visibleJourneys = computed(() =>
  filterJourneysByStatus(journeys.value, activeTab.value, today.value),
)
const activeTabLabel = computed(() => t(`journey.list.${activeTab.value}`))
const requestErrorDescription = computed(() =>
  t(journeyErrorMessageKey(journeyQuery.error.value, hasMessage)),
)

function setActiveTab(value: string): void {
  if (value === 'ongoing' || value === 'past') {
    activeTab.value = value
  }
}

function goToCreate(): void {
  void router.push({ name: 'journey-create' })
}

function retry(): void {
  void journeyQuery.refetch()
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen flex-1 pt-6 pb-8">
    <ScreenHeader
      variant="root"
      :title="t('journey.list.title')"
    >
      <template #action>
        <!--
          시안(Figma `1532:727`)은 글자 없는 원형 버튼이다. 글자를 함께 두면 제목이 시안 폭
          (34px 기준 256px)으로 커졌을 때 버튼이 화면 밖으로 밀린다.
          같은 구조의 `ExploreView` 헤더와 크기·모양을 맞춘다.
        -->
        <IconOrb
          :label="t('journey.list.add')"
          size="lg"
          variant="surface"
          @click="goToCreate"
        >
          <IconPlus
            :size="24"
            aria-hidden="true"
          />
        </IconOrb>
      </template>
    </ScreenHeader>

    <SegmentedControl
      :model-value="activeTab"
      :options="tabOptions"
      :label="t('journey.list.tabsLabel')"
      @update:model-value="setActiveTab"
    />

    <section aria-labelledby="journey-list-section-title">
      <h2
        id="journey-list-section-title"
        class="font-display text-section-header uppercase text-ink-display"
      >
        {{ activeTab === 'ongoing' ? t('journey.list.ongoingTitle') : t('journey.list.pastTitle') }}
      </h2>

      <div class="mt-4">
        <StateLoading
          v-if="journeyQuery.isPending.value"
          :label="t('state.loading')"
        />

        <StateError
          v-else-if="journeyQuery.isError.value"
          :title="t('journey.list.loadFailed')"
          :description="requestErrorDescription"
          :action-label="t('action.retry')"
          @retry="retry"
        />

        <StateEmpty
          v-else-if="journeys.length === 0"
          :title="t('journey.list.fullEmptyTitle')"
          :description="t('journey.list.fullEmptyDescription')"
          :action-label="t('journey.list.add')"
          @action="goToCreate"
        />

        <StateEmpty
          v-else-if="visibleJourneys.length === 0"
          :title="t('journey.list.tabEmptyTitle', { status: activeTabLabel })"
          :description="t('journey.list.tabEmptyDescription', { status: activeTabLabel })"
        />

        <!--
          시안 J1의 273px 고정폭 가로 스냅 캐러셀이다.

          폭: 시안은 273px이지만 대괄호 임의 값은 쓸 수 없고 토큰도 새로 만들지 않는다.
          스페이싱 스케일에서 가장 가까운 `w-68`(0.25rem × 68 = 272px)을 쓴다. 1px 차이다.

          full-bleed: `ExploreFilterBar`·`PlaceFilterBar`와 같은 패턴이다. `main`의 20px
          여백을 음수 마진으로 뚫고 나갔다가 같은 값의 패딩으로 되돌린다. **이 클래스들은
          `ul`에만 건다** — 위의 `div`는 빈·오류 상태까지 감싸므로 거기 걸면 그 여백이
          무너진다.

          `scroll-ps-screen`이 없으면 스냅이 저 20px 패딩을 무시하고 첫 카드를 화면 왼쪽
          끝에 붙인다(첫 카드의 스냅 위치가 scrollLeft=20px이 되기 때문이다). 스냅포트
          시작점을 패딩만큼 밀어 첫 카드의 스냅 위치를 scrollLeft=0으로 되돌린다.
          선례인 `ExploreFilterBar`에는 스냅이 없어서 이 줄이 없다. 복사하면 놓친다.

          `:key="activeTab"`: 탭이 바뀌어도 `v-else` 분기가 유지돼 Vue가 같은 `ul` DOM을
          재사용하고, 살아 있는 엘리먼트의 `scrollLeft`도 남는다. 오른쪽 끝까지 민 뒤
          탭을 바꾸면 첫 카드가 아니라 마지막 카드부터 보인다. 세로 목록에는 없던
          문제다 — 그때는 스크롤을 `ul`이 아니라 페이지가 소유했다.

          `tabindex`를 주지 않는다. 카드마다 `RouterLink`가 있어 키보드로 이미 전 구간을
          지나가며, 컨테이너를 초점 대상으로 만들면 첫 카드 앞에 이름 없는 탭 스톱이
          하나 생겨 카드의 초점 순서가 바뀐다.

          Tailwind preflight가 `ul`의 `list-style`을 지워 Safari·VoiceOver에서 목록
          시맨틱이 사라진다. `role="list"`로 되살리고, 이름은 위의 `h2`를 그대로 참조해
          탭에 따라 함께 바뀌게 한다.

          시안 말단의 180px 점선 `Add journey` 버튼은 넣지 않는다. 헤더 `IconOrb`와
          어포던스가 중복되고, 버튼만 담은 항목이 끼어들어 목록 항목 수를 왜곡한다.
        -->
        <ul
          v-else
          :key="activeTab"
          role="list"
          aria-labelledby="journey-list-section-title"
          class="scrollbar-hidden -mx-screen flex snap-x snap-mandatory scroll-ps-screen gap-3 overflow-x-auto px-screen pb-3 motion-safe:scroll-smooth"
        >
          <JourneyListCard
            v-for="journey in visibleJourneys"
            :key="journey.tripId"
            :journey="journey"
            :status="activeTab"
            :status-label="activeTabLabel"
            :report-id="reportIdByTripId.get(journey.tripId) ?? null"
            class="w-68 shrink-0 snap-start"
          />
        </ul>
      </div>
    </section>
  </main>
</template>
