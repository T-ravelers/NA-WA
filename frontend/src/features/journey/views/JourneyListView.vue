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

import JourneyListCard from '../components/JourneyListCard.vue'
import { useJourneyListQuery } from '../composables/useJourneyListQuery'
import { journeyErrorMessageKey } from '../model/journeyErrors'
import { filterJourneysByStatus, type JourneyListTab, useKoreaToday } from '../model/journeyStatus'

const i18n = useI18n()
const { t } = i18n
const router = useRouter()
const activeTab = ref<JourneyListTab>('ongoing')
const today = useKoreaToday()
const hasMessage = (key: string): boolean => i18n.te(key)
const journeyQuery = useJourneyListQuery(true)

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
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <header class="flex items-center justify-between gap-4">
      <h1 class="font-display text-screen-title uppercase text-ink-display">
        {{ t('journey.list.title') }}
      </h1>
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
    </header>

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
          시안 J1은 273px 고정폭 가로 스냅 캐러셀이다. 현재 목록은 세로 스크롤이고
          스크롤·포커스 동작을 바꾸지 않기로 했으므로 세로 배치를 유지한다.
        -->
        <ul
          v-else
          class="flex flex-col gap-4"
        >
          <JourneyListCard
            v-for="journey in visibleJourneys"
            :key="journey.tripId"
            :journey="journey"
            :status="activeTab"
            :status-label="activeTabLabel"
          />
        </ul>
      </div>
    </section>
  </main>
</template>
