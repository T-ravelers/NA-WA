import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import type {
  ExploreJourneyIntegration,
  ExploreJourneyListQuery,
  ExploreJourneySummary,
} from '../../model/journeyIntegration'
import { exploreJourneyIntegrationKey } from '../../model/journeyIntegration'
import type { EventDetail } from '../../model/eventDetail'

const fetchEventDetail = vi.fn()
const addJourneyItem = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  fetchEventDetail: (eventId: number | string, language: string) =>
    fetchEventDetail(eventId, language),
}))

const openMapAppUrl = vi.fn()

// 앱 스킴 진입은 현재 문서를 이동시킨다. jsdom의 window.location을 직접 건드리면
// navigation 경고가 다른 테스트로 새므로 이 함수만 부분 모킹한다.
vi.mock('@/shared/lib/mapLink', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/lib/mapLink')>()),
  openMapAppUrl: (url: string | null) => openMapAppUrl(url),
}))

const EventDetailView = (await import('../EventDetailView.vue')).default

const event: EventDetail = {
  eventId: 42,
  eventType: null,
  eventKind: 'CONCERT',
  title: 'Seoul concert',
  subtitle: null,
  description: 'A test event',
  programText: null,
  thumbnailUrl: null,
  imageUrls: [],
  links: {},
  reservationUrl: null,
  preReservation: null,
  status: 'ONGOING',
  isPermanent: false,
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  operatingHours: { hours: '19:00 – 21:00' },
  openDays: [],
  openWeekend: true,
  opensLate: false,
  venueName: 'Seoul Arts Center',
  region1: 'Seoul',
  region2: 'Seocho-gu',
  region3: null,
  addressRoad: '2406 Nambu-sunhwan-ro',
  latitude: 37.48,
  longitude: 127.01,
  hasPhotoZone: false,
  isExperience: false,
  ageLimit: null,
  isFree: false,
  priceText: '30,000 KRW',
  hasBenefit: false,
  reservable: true,
  contact: null,
  organizer: 'NA-WA',
  saved: false,
  activities: [],
}

const journeys: ExploreJourneySummary[] = [
  {
    tripId: 7,
    title: 'Seoul weekend',
    startDate: '2026-08-10',
    endDate: '2026-08-12',
  },
]

function createJourneyIntegration(list: ExploreJourneySummary[]): ExploreJourneyIntegration {
  const query: ExploreJourneyListQuery = {
    data: ref<ExploreJourneySummary[] | undefined>(list),
    isPending: ref(false),
    isError: ref(false),
  }

  return {
    addJourneyItem,
    parseJourneyRouteQuery: () => null,
    useJourneyListQuery: () => query,
  }
}

async function mountView(path = '/explore/events/42', journeyList = journeys) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/explore',
        name: 'explore',
        component: { template: '<div>Explore</div>' },
      },
      {
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: EventDetailView,
      },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Journey detail</div>' },
      },
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>Appointments</div>' },
      },
      {
        path: '/journeys/new',
        name: 'journey-create',
        component: { template: '<div>Journey create</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(EventDetailView, {
    global: {
      plugins: [i18n, router, createPinia(), [VueQueryPlugin, { queryClient }]],
      provide: {
        [exploreJourneyIntegrationKey as symbol]: createJourneyIntegration(journeyList),
      },
    },
  })

  await flushPromises()
  return { wrapper, router }
}

const RETURN_CONTEXT_KEY = 'nawa.explore.returnContext'
/* Event 개최 기간(2026-08-10 ~ 2026-08-12) 안이어야 담기 시트가 그대로 프리필한다. */
const CARRIED_DATE = '2026-08-11'

/** Journey 날짜에서 넘어온 복귀 맥락을 심는다. store가 마운트 시점에 읽어간다. */
function seedReturnContext(): void {
  sessionStorage.setItem(
    RETURN_CONTEXT_KEY,
    JSON.stringify({
      journeyId: 7,
      visitDate: CARRIED_DATE,
      returnTo: { name: 'journey-detail', params: { tripId: '7' } },
    }),
  )
}

function readReturnContext(): unknown {
  const stored = sessionStorage.getItem(RETURN_CONTEXT_KEY)
  return stored === null ? null : JSON.parse(stored)
}

/** 담기 버튼 → 여정 선택 → 날짜 확인까지 한 번에 진행한다. */
async function runAddToJourney(
  wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'],
): Promise<void> {
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Add to journey')
    ?.trigger('click')
  await flushPromises()

  await wrapper
    .get('[role="dialog"]')
    .findAll('button')
    .find((button) => button.text().includes('Seoul weekend'))
    ?.trigger('click')
  await flushPromises()

  await wrapper
    .get('[role="dialog"]')
    .findAll('button')
    .find((button) => button.text().includes('Add to'))
    ?.trigger('click')
  await flushPromises()
}

describe('EventDetailView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    fetchEventDetail.mockReset()
    addJourneyItem.mockReset()
    fetchEventDetail.mockResolvedValue(event)
    addJourneyItem.mockResolvedValue({})
    openMapAppUrl.mockReset()
  })

  it('mounts with the app-provided Journey integration', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul concert')
    expect(wrapper.text()).toContain('Seoul · Seocho-gu')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Google Maps')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('opens Google Maps for the Event coordinates', async () => {
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Google Maps')
      ?.trigger('click')
    expect(openSpy).toHaveBeenCalledWith(
      'https://www.google.com/maps/search/?api=1&query=37.48%2C127.01',
      '_blank',
      'noopener,noreferrer',
    )

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Google transit')
      ?.trigger('click')
    expect(openSpy).toHaveBeenCalledWith(
      'https://www.google.com/maps/dir/?api=1&destination=37.48%2C127.01&travelmode=transit',
      '_blank',
      'noopener,noreferrer',
    )

    openSpy.mockRestore()
  })

  it('opens the Naver Map app scheme for the Event coordinates', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Naver Map')
      ?.trigger('click')
    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://place?lat=37.48&lng=127.01&name=Seoul%20concert&appname=NA-WA',
    )

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Naver transit')
      ?.trigger('click')
    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://route/public?dlat=37.48&dlng=127.01&dname=Seoul%20concert&appname=NA-WA',
    )
  })

  it('hides map buttons when the Event has no coordinates', async () => {
    fetchEventDetail.mockResolvedValue({ ...event, latitude: null, longitude: null })

    const { wrapper } = await mountView()

    const buttonLabels = wrapper.findAll('button').map((button) => button.text())
    expect(buttonLabels).not.toContain('Google Maps')
    expect(buttonLabels).not.toContain('Google transit')
    expect(buttonLabels).not.toContain('Naver Map')
    expect(buttonLabels).not.toContain('Naver transit')
  })

  it('translates operational region values and hides the raw hours key', async () => {
    fetchEventDetail.mockResolvedValue({
      ...event,
      region1: '서울',
      region2: '성수',
      operatingHours: { raw: 'Every day 10:00 – 20:00' },
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Every day 10:00 – 20:00')
    expect(wrapper.text()).not.toContain('raw:')
  })

  it('returns to the Event list from the detail screen', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Back to events"]').trigger('click')
    await flushPromises()
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('explore')
    expect(router.currentRoute.value.query).toEqual({ tab: 'events' })
  })

  it('adds the Event to the selected journey for the chosen date', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seoul weekend'))
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Add to'))
      ?.trigger('click')
    await flushPromises()

    expect(addJourneyItem).toHaveBeenCalledWith(7, {
      itemId: 42,
      visitDate: expect.any(String),
    })
    expect(router.currentRoute.value.name).toBe('journey-detail')
  })

  it('adds the Event on the date the Journey entry carried and returns there', async () => {
    seedReturnContext()
    const { wrapper, router } = await mountView()

    await runAddToJourney(wrapper)

    expect(addJourneyItem).toHaveBeenCalledWith(7, { itemId: 42, visitDate: CARRIED_DATE })
    expect(router.currentRoute.value.name).toBe('journey-detail')
    expect(router.currentRoute.value.params.tripId).toBe('7')
  })

  it('spends the carried date once so a later direct entry does not reuse it', async () => {
    seedReturnContext()
    const first = await mountView()
    await runAddToJourney(first.wrapper)

    expect(readReturnContext()).toEqual({ journeyId: 7, visitDate: null, returnTo: null })

    addJourneyItem.mockClear()
    const second = await mountView()
    await runAddToJourney(second.wrapper)

    expect(addJourneyItem).toHaveBeenCalledTimes(1)
    expect(addJourneyItem.mock.calls[0]?.[1].visitDate).not.toBe(CARRIED_DATE)
  })

  /*
   * 서버는 이미 정확한 이유를 준다. 전부 "다시 시도해 주세요"로 뭉개면 **다시 시도해도
   * 절대 성공하지 않는 실패에 다시 시도하라고 말하게 된다.**
   */
  it('담기 실패를 오류 코드별로 다르게 안내한다', async () => {
    addJourneyItem.mockRejectedValue(
      new NormalizedApiError('JOURNEY-012', 400, '방문 날짜가 항목의 운영 기간을 벗어났습니다.'),
    )
    const { wrapper } = await mountView()

    await runAddToJourney(wrapper)

    expect(wrapper.text()).toContain('That date falls outside this event’s run dates.')
    expect(wrapper.text()).not.toContain('Please try again.')
  })

  it('여정 기간을 벗어난 실패는 여정 쪽 문구로 안내한다', async () => {
    addJourneyItem.mockRejectedValue(
      new NormalizedApiError('JOURNEY-007', 400, '방문 날짜가 Journey 기간을 벗어났습니다.'),
    )
    const { wrapper } = await mountView()

    await runAddToJourney(wrapper)

    expect(wrapper.text()).toContain('That date falls outside your journey’s dates.')
  })

  it('알 수 없는 실패는 기존 문구로 떨어진다', async () => {
    addJourneyItem.mockRejectedValue(new Error('boom'))
    const { wrapper } = await mountView()

    await runAddToJourney(wrapper)

    expect(wrapper.text()).toContain('We could not add this item to your journey.')
  })

  /*
   * 달력이 열어 주는 구간은 이벤트 기간(8/10~8/12)과 여정 기간이 겹치는 날뿐이다.
   * 여정 기간만 보면 8/13이 열려 확정한 뒤에야 JOURNEY-012로 실패한다.
   */
  it('달력을 이벤트 기간과 여정 기간의 교집합으로 좁힌다', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seoul weekend'))
      ?.trigger('click')
    await flushPromises()

    const dayCell = (label: string) =>
      wrapper
        .get('[role="dialog"]')
        .findAll('button')
        .find((button) => button.text().trim() === label)

    expect(dayCell('11')?.attributes('disabled')).toBeUndefined()
    expect(dayCell('9')?.attributes('disabled')).toBeDefined()
    expect(dayCell('13')?.attributes('disabled')).toBeDefined()
  })

  it('담을 여정이 없으면 그 자리에서 여정 만들기로 나가고 돌아올 위치를 남긴다', async () => {
    const { wrapper, router } = await mountView('/explore/events/42', [])

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text() === 'Create a journey')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-create')
    expect(router.currentRoute.value.query).toEqual({ returnToExplore: '1' })
    expect(readReturnContext()).toMatchObject({
      returnTo: { name: 'explore-event-detail', params: { eventId: '42' } },
    })
  })

  /*
   * 여정을 만들고 돌아온 진입. 하던 일이 그대로 이어져야 하고, 표시는 주소에서 지워야
   * 새로고침이나 뒤로 가기에서 시트가 되살아나지 않는다.
   */
  it('여정을 만들고 돌아오면 담기 시트를 다시 열고 표시를 지운다', async () => {
    const { wrapper, router } = await mountView(
      '/explore/events/42?journeyId=7&openJourneySelect=1',
    )

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')
    expect(router.currentRoute.value.query).toEqual({ journeyId: '7' })
  })

  it('표시가 없는 진입에서는 시트를 열지 않는다', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('opens the Event appointment list with the Event filter', async () => {
    const { wrapper, router } = await mountView()

    const button = wrapper.findAll('button').find((button) => button.text() === 'Find companions')

    expect(button?.attributes('disabled')).toBeUndefined()
    await button?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
    expect(router.currentRoute.value.query).toEqual({ itemId: '42', itemType: 'EVENT' })
  })
})
