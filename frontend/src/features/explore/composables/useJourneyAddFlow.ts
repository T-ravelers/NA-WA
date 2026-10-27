import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { formatCalendarDate } from '@/shared/lib/datetime'
import { intersectItemJourneyPeriod } from '@/shared/lib/journeyPeriod'
import { showToast } from '@/shared/ui/toast'

import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import { journeyAddErrorMessageKey } from '../model/journeyAddErrors'
import { useExploreJourneyIntegration } from '../model/journeyIntegration'

/**
 * 담을 항목. 기간은 담을 수 있는 날의 범위이며, `null`이면 그쪽 경계가 없다.
 *
 * 경계를 어떻게 읽을지는 항목 종류마다 달라 화면이 정해서 넘긴다. 장소는 운영 기간이
 * 없어 둘 다 `null`이고, 종료일 미상 이벤트는 `endDate`가 `null`이다.
 */
export interface JourneyAddItem {
  itemId: number
  startDate: string | null
  endDate: string | null
}

/** 여정을 만들고 돌아올 자리. 복귀 query 규약(`frontend/docs/DEVELOPMENT_CONVENTION.md`)의 값이다. */
export interface JourneyCreateReturn {
  returnRouteName: string
  returnParams?: string
}

interface JourneyAddFlowOptions {
  item: MaybeRefOrGetter<JourneyAddItem | null>
  createJourneyReturn: MaybeRefOrGetter<JourneyCreateReturn>
}

/**
 * 탐색 항목을 여정에 담는 흐름. 여정 선택 시트 → 날짜 시트 → 담기 순서로 간다.
 *
 * 겹치는 여정이 없으면 여정을 만들러 나갔다가 돌아와 하던 담기를 잇는다. 돌아온 화면은
 * `onMounted`에서 `resumeAfterJourneyCreate`를 불러야 한다.
 */
export function useJourneyAddFlow({ item, createJourneyReturn }: JourneyAddFlowOptions) {
  const route = useRoute()
  const router = useRouter()
  const i18n = useI18n()
  const hasMessage = (key: string): boolean => i18n.te(key)
  const { addJourneyItem, parseJourneyRouteQuery, useJourneyListQuery } =
    useExploreJourneyIntegration()
  const returnContext = useExploreReturnContextStore()

  const journeySelectSheetOpen = ref(false)
  const journeyDateSheetOpen = ref(false)
  const journeyDate = ref<string | null>(null)
  const selectedJourneyId = ref<number | null>(null)
  const journeyAddPending = ref(false)
  /** 담기 실패 문구의 i18n key. 원인별로 다른 key가 들어온다. */
  const journeyAddError = ref<string | null>(null)

  const activeJourneyId = computed(
    () => parseJourneyRouteQuery(route.query.journeyId) ?? returnContext.journeyId,
  )
  const journeyListQuery = useJourneyListQuery(journeySelectSheetOpen)
  const journeys = computed(() => journeyListQuery.data.value ?? [])

  const itemPeriod = computed(() => {
    const current = toValue(item)
    return { startDate: current?.startDate ?? null, endDate: current?.endDate ?? null }
  })

  const selectedJourney = computed(
    () => journeys.value.find((journey) => journey.tripId === selectedJourneyId.value) ?? null,
  )

  /**
   * 달력이 열어 줄 구간. 항목 기간과 여정 기간이 겹치는 날만 담을 수 있다.
   *
   * 여정을 아직 고르지 않았거나 겹치는 날이 없으면 `null`이고, 그때는 날짜 시트를 열지
   * 않는다 — 열어 봐야 고를 수 있는 날이 없다.
   */
  const journeyDateRange = computed(() => {
    const journey = selectedJourney.value
    return journey === null ? null : intersectItemJourneyPeriod(itemPeriod.value, journey)
  })

  function openJourneySelectSheet(): void {
    journeyAddError.value = null
    // 이 화면에서 이미 고른 값이 URL의 진입 맥락보다 최신이다. 매번 query로 덮어쓰면
    // 날짜 시트를 닫고 다시 열 때 사용자의 마지막 선택이 사라진다(#390).
    selectedJourneyId.value ??= activeJourneyId.value
    journeySelectSheetOpen.value = true
  }

  function closeJourneySelectSheet(): void {
    journeySelectSheetOpen.value = false
  }

  function selectJourney(journeyId: number): void {
    selectedJourneyId.value = journeyId
    returnContext.setJourneyId(journeyId)
    if (
      route.query.journeyId !== undefined &&
      parseJourneyRouteQuery(route.query.journeyId) !== journeyId
    ) {
      const restQuery = { ...route.query }
      delete restQuery.journeyId
      void router.replace({ query: restQuery })
    }
    journeyDate.value = returnContext.visitDate
    journeySelectSheetOpen.value = false
    // 시트가 고를 수 없는 여정을 막지만, 범위가 없으면 열 것이 없으므로 한 번 더 본다.
    journeyDateSheetOpen.value = journeyDateRange.value !== null
  }

  /**
   * 담을 여정이 없을 때 이 자리에서 만들러 나간다.
   *
   * 돌아올 위치를 먼저 심는다. 상세 화면은 route param을 실어야 해서 query만 나르는
   * `returnRouteName`만으로는 돌아올 수 없고 `returnParams`가 함께 필요하다.
   */
  function goToCreateJourney(): void {
    journeySelectSheetOpen.value = false
    /*
     * 다른 화면에 들렀다 돌아오는 query 규약을 쓴다(`frontend/docs/DEVELOPMENT_CONVENTION.md`).
     * 두 feature가 서로를 import하지 않고 주소만으로 이어진다.
     *
     * `replace`인 것은 새 여정 id를 돌려받아야 해서다 — 받는 쪽이 이 자리를 넘겨받았다가
     * 일을 마치든 그냥 나가든 돌려준다. 그래서 왕복이 히스토리에 한 자리만 차지한다.
     *
     * `openJourneySelect`는 규약이 "나머지 query"로 그대로 돌려주므로, 돌아왔을 때 하던
     * 일을 이어 갈 표시가 된다.
     */
    /*
     * 항목 기간이 있으면 함께 보낸다. 이 버튼을 누른 사람은 정의상 **겹치는 여정이 하나도
     * 없는** 사람인데, 빈 폼에는 무엇과 겹쳐야 하는지가 없다. 안 겹치는 기간으로 또
     * 만들고 돌아오면 없애려던 막다른 길이 한 바퀴 뒤로 옮겨질 뿐이다.
     *
     * 경계가 없는 쪽(장소, 종료일 미상 이벤트)은 싣지 않는다.
     */
    const { returnRouteName, returnParams } = toValue(createJourneyReturn)
    const { startDate, endDate } = itemPeriod.value
    void router.replace({
      name: 'journey-create',
      query: {
        returnRouteName,
        ...(returnParams !== undefined ? { returnParams } : {}),
        openJourneySelect: '1',
        ...(startDate !== null ? { itemStartDate: startDate } : {}),
        ...(endDate !== null ? { itemEndDate: endDate } : {}),
      },
    })
  }

  function closeJourneyDateSheet(): void {
    journeyDateSheetOpen.value = false
  }

  async function confirmJourneyDate(date: string): Promise<void> {
    if (journeyAddPending.value) return

    const current = toValue(item)
    const journeyId = selectedJourneyId.value ?? activeJourneyId.value
    if (!current || journeyId === null) {
      journeyAddError.value = 'explore.journeyDate.selectItemFirst'
      return
    }

    journeyAddPending.value = true
    journeyAddError.value = null

    try {
      await addJourneyItem(journeyId, {
        itemId: current.itemId,
        visitDate: date,
      })
      returnContext.setJourneyId(journeyId)
      journeyDate.value = date
      journeyDateSheetOpen.value = false
      /*
       * 여정의 그날을 채우러 들어온 사람은 담는 것으로 할 일이 끝나 그 여정으로 돌려보낸다.
       * 하단 탭으로 들어와 둘러보던 사람은 그 자리에 둔다 — 담을 때마다 여정 상세로 보내면
       * 여러 곳을 연달아 담으려고 매번 탐색으로 되돌아와야 한다(#584).
       *
       * 담겼다는 표시를 버튼에 남기지 않고 토스트로만 알린다. 같은 항목을 다른 여정이나
       * 다른 날에도 담을 수 있어서, 버튼이 "담김"이 되면 어디에 담겼다는 뜻인지 모호하다.
       */
      const destination = returnContext.consumeReturn()
      if (destination !== null) {
        await router.push(destination)
        return
      }
      showAddedToast(journeyId, date)
    } catch (error) {
      journeyAddError.value = journeyAddErrorMessageKey(error, hasMessage)
    } finally {
      journeyAddPending.value = false
    }
  }

  function showAddedToast(journeyId: number, date: string): void {
    const formattedDate = formatCalendarDate(date, i18n.locale.value)
    const journey = journeys.value.find((candidate) => candidate.tripId === journeyId)
    showToast(
      journey
        ? i18n.t('explore.journeyDate.added', { journey: journey.title, date: formattedDate })
        : i18n.t('explore.journeyDate.addedWithoutJourney', { date: formattedDate }),
    )
  }

  /**
   * 여정을 만들고 돌아온 진입. 하던 일을 그대로 이어 담기 시트를 다시 연다.
   *
   * `openJourneySelect` 표시를 남겨 두면 새로고침이나 뒤로 가기에서도 시트가 다시
   * 열리므로 읽자마자 주소에서 지운다. 새 여정 id를 어떻게 받는지는 아래 블록 주석에
   * 적었다.
   */
  function resumeAfterJourneyCreate(): void {
    if (route.query.openJourneySelect !== '1') return

    openJourneySelectSheet()

    /*
     * 규약이 정한 결과 key는 `tripId`다. 여기서 한 번 읽어 고른 여정으로 삼고 주소에서
     * 지운다 — 남겨두면 시트를 다시 열 때마다 사용자가 그 뒤에 고른 여정을 덮어쓴다.
     */
    const createdJourneyId = parseJourneyRouteQuery(route.query.tripId)
    if (createdJourneyId !== null) {
      selectedJourneyId.value = createdJourneyId
      returnContext.setJourneyId(createdJourneyId)
    }

    const restQuery = { ...route.query }
    delete restQuery.openJourneySelect
    delete restQuery.tripId
    // 규약이 나머지 query를 그대로 돌려주므로 보낼 때 실은 기간도 함께 돌아온다.
    // 이 화면에서는 쓰이지 않으니 주소에 남기지 않는다.
    delete restQuery.itemStartDate
    delete restQuery.itemEndDate
    void router.replace({ query: restQuery })
  }

  return {
    journeySelectSheetOpen,
    journeyDateSheetOpen,
    journeyDate,
    selectedJourneyId,
    journeyAddPending,
    journeyAddError,
    journeyListQuery,
    journeys,
    itemPeriod,
    journeyDateRange,
    openJourneySelectSheet,
    closeJourneySelectSheet,
    selectJourney,
    goToCreateJourney,
    closeJourneyDateSheet,
    confirmJourneyDate,
    resumeAfterJourneyCreate,
  }
}
