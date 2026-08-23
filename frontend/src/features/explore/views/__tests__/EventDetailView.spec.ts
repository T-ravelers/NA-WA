import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

const { fetchEventDetail, addJourneyItem, showToast } = vi.hoisted(() => ({
  fetchEventDetail: vi.fn(),
  addJourneyItem: vi.fn(),
  showToast: vi.fn(),
}))

vi.mock('@/shared/ui/toast', () => ({ showToast }))

vi.mock('../../api/exploreApi', () => ({
  fetchEventDetail: (eventId: number | string, language: string) =>
    fetchEventDetail(eventId, language),
}))

const EventDetailView = (await import('../EventDetailView.vue')).default
const MapLinkButtons = (await import('../../components/MapLinkButtons.vue')).default

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

/**
 * 앱이 주입하는 `parseJourneyRouteQuery`와 같게 동작하는 스텁.
 *
 * journey feature를 직접 import할 수 없어(`architecture/no-cross-feature-imports`)
 * 규칙만 여기에 옮겨 둔다. 예전처럼 `() => null`로 막아 두면 **복귀 진입이 새 여정
 * id를 읽는 경로가 통째로 실행되지 않아**, 받는 쪽이 비어 있어도 테스트가 초록으로
 * 남는다. 실제 구현(`journey/model/journeyRouteQuery`)이 바뀌면 여기도 맞춰야 한다.
 */
function parseJourneyRouteQuery(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' && typeof raw !== 'number') return null

  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function createJourneyIntegration(list: ExploreJourneySummary[]): ExploreJourneyIntegration {
  const query: ExploreJourneyListQuery = {
    data: ref<ExploreJourneySummary[] | undefined>(list),
    isPending: ref(false),
    isError: ref(false),
  }

  return {
    addJourneyItem,
    parseJourneyRouteQuery,
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
    showToast.mockReset()
    fetchEventDetail.mockResolvedValue(event)
    addJourneyItem.mockResolvedValue({})
  })

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'share')
    Reflect.deleteProperty(navigator, 'clipboard')
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

  // 버튼 네 개의 URL 단언은 `MapLinkButtons.spec.ts`가 소유한다. 여기서는 상세 화면이
  // **어떤 값을 넘기는가**만 본다 — Event는 `title`, Place는 `name`으로 필드가 갈려서
  // 잘못 넘겨도 URL 단언에는 걸리지 않는다.
  it('passes the Event coordinates and title to the map buttons', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findComponent(MapLinkButtons).props()).toMatchObject({
      latitude: 37.48,
      longitude: 127.01,
      name: 'Seoul concert',
    })
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

  it('falls back to copying the Event link when native sharing is blocked', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'share', {
      value: vi.fn().mockRejectedValue(new DOMException('blocked', 'NotAllowedError')),
      configurable: true,
    })
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share event"]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith(window.location.href)
    expect(wrapper.text()).toContain('Event link copied.')
  })

  it('tells the user when Event sharing and copying are unavailable', async () => {
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true })
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share event"]').trigger('click')
    await flushPromises()

    expect(showToast).toHaveBeenCalledWith('We could not share this event. Please try again.')
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
    // 다른 화면에 들렀다 돌아오는 query 규약. 이 화면은 route param을 쓰므로
    // returnParams도 함께 싣는다. openJourneySelect는 그대로 돌아와 하던 일을 잇는다.
    // 이벤트 기간도 함께 간다. 이 버튼을 누른 사람은 겹치는 여정이 하나도 없는
    // 사람이라, 빈 폼으로 보내면 안 겹치는 기간으로 또 만들고 돌아온다.
    expect(router.currentRoute.value.query).toEqual({
      returnRouteName: 'explore-event-detail',
      returnParams: 'eventId:42',
      openJourneySelect: '1',
      itemStartDate: '2026-08-10',
      itemEndDate: '2026-08-12',
    })
  })

  /*
   * 여정을 만들고 돌아온 진입. 하던 일이 그대로 이어져야 하고, 표시는 주소에서 지워야
   * 새로고침이나 뒤로 가기에서 시트가 되살아나지 않는다.
   */
  it('여정을 만들고 돌아오면 그 여정이 골라진 채 시트가 열리고 규약 key가 지워진다', async () => {
    // 보낼 때 실은 항목 기간도 규약대로 그대로 돌아온다. 진입 주소에 함께 넣어야
    // 아래 `toEqual({})`가 그 key를 지우는 줄까지 지킨다.
    const { wrapper, router } = await mountView(
      '/explore/events/42?tripId=7&openJourneySelect=1&itemStartDate=2026-08-10&itemEndDate=2026-08-12',
    )

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')

    // 방금 만든 여정이 실제로 골라져 있어야 한다. 이 단언이 없으면 받는 쪽이 통째로
    // 비어 있어도(=돌아와서 아무것도 골라지지 않아도) 테스트가 통과한다.
    const pressed = wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.attributes('aria-pressed') === 'true')
    expect(pressed?.text()).toContain('Seoul weekend')

    // 남겨두면 시트를 다시 열 때마다 그 뒤에 고른 여정을 덮어쓴다.
    expect(router.currentRoute.value.query).toEqual({})
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
