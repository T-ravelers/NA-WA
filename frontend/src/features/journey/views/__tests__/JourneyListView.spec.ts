import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'
import { journeyReportIntegrationKey } from '../../model/reportIntegration'

const fetchJourneys = vi.fn()

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  fetchJourneys: () => fetchJourneys(),
}))

const JourneyListView = (await import('../JourneyListView.vue')).default

const journeys = [
  {
    tripId: 42,
    title: 'Seoul Foodie Week',
    startDate: '2098-08-10',
    endDate: '2098-08-12',
    eventCount: 8,
    placeCount: 4,
    coverImageUrl: 'https://cdn.test/seoul.jpg',
  },
  {
    // 장소만 담은 여정. `0 events`가 붙지 않는지 확인하는 데 쓴다.
    tripId: 7,
    title: 'Busan Weekender',
    startDate: '2020-08-10',
    endDate: '2020-08-12',
    eventCount: 0,
    placeCount: 3,
    coverImageUrl: null,
  },
]

let queryClient: QueryClient
const queryClients: QueryClient[] = []
/** 기본은 리포트 없음. 링크를 보는 테스트가 이 배열을 바꾼다. */
let reportSummaries: { tripId: number; reportId: number }[] = []

const mountedWrappers: Array<{ unmount: () => void }> = []

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys', name: 'journey-list', component: JourneyListView },
      { path: '/journeys/new', name: 'journey-create', component: { template: '<div>New</div>' } },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Detail</div>' },
      },
    ],
  })
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClients.push(queryClient)

  await router.push('/journeys')
  await router.isReady()

  const wrapper = mount(JourneyListView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        /*
         * 카드의 `View report`는 report feature가 가진 요약에서 온다. feature끼리 직접
         * import하지 않도록 `main.ts`가 주입하는 통로라, 테스트도 같은 자리에 스텁을 준다.
         */
        [journeyReportIntegrationKey as symbol]: {
          useReportSummariesQuery: () => ({
            data: ref(reportSummaries),
            isPending: ref(false),
            isError: ref(false),
            refetch: vi.fn(),
          }),
        },
      },
    },
  })

  await flushPromises()
  mountedWrappers.push(wrapper)

  return { wrapper, router }
}

describe('JourneyListView', () => {
  beforeEach(() => {
    fetchJourneys.mockReset()
    reportSummaries = []
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    queryClients.splice(0).forEach((client) => client.clear())
    vi.useRealTimers()
  })

  it('loads ongoing journeys first and switches to past journeys', async () => {
    fetchJourneys.mockResolvedValue(journeys)
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('Journeys')
    expect(wrapper.text()).toContain('Seoul Foodie Week')
    expect(wrapper.text()).toContain('Aug 10, 2098')
    expect(wrapper.text()).not.toContain('Busan Weekender')
    expect(wrapper.find('ul[aria-live]').exists()).toBe(false)

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    expect(wrapper.text()).toContain('Busan Weekender')
    expect(wrapper.text()).toContain('Aug 12, 2020')
    expect(wrapper.text()).not.toContain('Seoul Foodie Week')
  })

  it('navigates to the selected journey and new journey screen', async () => {
    fetchJourneys.mockResolvedValue(journeys)
    const { wrapper, router } = await mountView()

    await wrapper.get('a[href="/journeys/42"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/journeys/42')

    await router.push('/journeys')
    await flushPromises()
    await wrapper.get('button[aria-label="Add journey"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/journeys/new')
  })

  it('shows the full empty state with a working create action', async () => {
    fetchJourneys.mockResolvedValue([])
    const { wrapper, router } = await mountView()

    expect(wrapper.text()).toContain('No journeys yet')
    expect(wrapper.text()).toContain('Create your first journey to start planning.')

    const emptyAction = wrapper
      .findAll('button')
      .find(
        (button) =>
          button.text() === 'Add journey' && button.attributes('aria-label') === undefined,
      )
    await emptyAction?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/journeys/new')
  })

  it('shows a selected-tab empty state without hiding the add action', async () => {
    fetchJourneys.mockResolvedValue([journeys[0]])
    const { wrapper } = await mountView()

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    expect(wrapper.text()).toContain('No Past journeys')
    expect(wrapper.find('button[aria-label="Add journey"]').exists()).toBe(true)
  })

  it('shows loading and supports retrying a failed request', async () => {
    let resolveRequest: ((value: typeof journeys) => void) | undefined
    fetchJourneys.mockImplementationOnce(
      () =>
        new Promise<typeof journeys>((resolve) => {
          resolveRequest = resolve
        }),
    )
    const pending = await mountView()

    expect(pending.wrapper.find('[role="status"]').exists()).toBe(true)
    resolveRequest?.(journeys)
    await flushPromises()
    expect(pending.wrapper.text()).toContain('Seoul Foodie Week')

    fetchJourneys.mockReset()
    fetchJourneys.mockRejectedValueOnce(new NormalizedApiError('NETWORK', null, 'offline'))
    const failed = await mountView()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain(
      'We could not load your journeys. Please try again.',
    )

    fetchJourneys.mockResolvedValueOnce(journeys)
    const retryButton = failed.wrapper
      .findAll('button')
      .find((button) => button.text() === 'Try again')
    await retryButton?.trigger('click')
    await flushPromises()
    expect(failed.wrapper.text()).toContain('Seoul Foodie Week')
  })

  it('refreshes the Korea date after midnight while the screen remains mounted', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-09T14:59:59.000Z'))
    fetchJourneys.mockResolvedValue([
      {
        tripId: 42,
        title: 'Seoul Foodie Week',
        startDate: '2026-08-08',
        endDate: '2026-08-09',
      },
    ])

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul Foodie Week')

    await vi.advanceTimersByTimeAsync(1_000)
    await flushPromises()

    expect(wrapper.text()).toContain('No Ongoing journeys')
  })

  it('lays the journey list out as a labelled horizontal snap carousel', async () => {
    fetchJourneys.mockResolvedValue(journeys)
    const { wrapper } = await mountView()

    const carousel = wrapper.get('ul')

    // 스냅 캐러셀 셋: 축·강도·스냅포트 시작점(화면 20px 여백).
    expect(carousel.classes()).toEqual(
      expect.arrayContaining([
        'flex',
        'overflow-x-auto',
        'scrollbar-hidden',
        'snap-x',
        'snap-mandatory',
        'scroll-ps-screen',
      ]),
    )

    // full-bleed는 목록에만 건다. 빈·오류 상태를 감싸는 래퍼의 여백을 건드리면 안 된다.
    expect(carousel.classes()).toEqual(expect.arrayContaining(['-mx-screen', 'px-screen']))

    // 동작 줄이기에서는 부드러운 스크롤만 끄고 스냅은 남긴다.
    expect(carousel.classes()).toContain('motion-safe:scroll-smooth')
    expect(carousel.classes()).not.toContain('scroll-smooth')

    // preflight가 list-style을 지워 목록 시맨틱이 사라지므로 role을 명시한다.
    expect(carousel.attributes('role')).toBe('list')
    expect(carousel.attributes('aria-labelledby')).toBe('journey-list-section-title')
    expect(wrapper.get('#journey-list-section-title').text()).toBe('Ongoing journeys')

    // 스크롤 컨테이너 자체는 탭 스톱이 되면 안 된다. 카드 링크가 이미 초점을 받는다.
    expect(carousel.attributes('tabindex')).toBeUndefined()

    // 카드는 고정폭 스냅 아이템이다.
    const cards = wrapper.findAll('li')
    expect(cards).toHaveLength(1)
    expect(cards[0]?.classes()).toEqual(expect.arrayContaining(['w-68', 'shrink-0', 'snap-start']))

    // 초점 순서는 카드당 링크 하나 그대로다. 말단에 버튼을 덧붙이지 않았다.
    expect(carousel.findAll('a')).toHaveLength(1)
    expect(carousel.findAll('button')).toHaveLength(0)
  })

  it('restarts the carousel at the first card when the tab changes', async () => {
    fetchJourneys.mockResolvedValue(journeys)
    const { wrapper } = await mountView()

    // jsdom에는 레이아웃이 없어 scrollLeft로는 검증할 수 없다(항상 0이다). 실제 보증은
    // 탭이 바뀔 때 `ul`이 재생성되어 이전 가로 스크롤 위치가 남지 않는다는 것이다.
    const before = wrapper.get('ul').element

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    const after = wrapper.get('ul').element

    expect(after).not.toBe(before)
    expect(wrapper.get('#journey-list-section-title').text()).toBe('Past journeys')
    expect(wrapper.text()).toContain('Busan Weekender')
  })

  it('shows EVENT and PLACE counts separately and hides the zero side', async () => {
    fetchJourneys.mockResolvedValue(journeys)
    const { wrapper } = await mountView()

    // 둘 다 있으면 가운뎃점으로 잇는다. 시안은 `12 events`로 통칭했지만 API가 두 종류를
    // 따로 주므로 분리한다.
    expect(wrapper.text()).toContain('8 events · 4 places')

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    // 장소만 담은 여정에 `0 events`가 붙으면 비어 있다는 인상을 준다.
    expect(wrapper.text()).toContain('3 places')
    expect(wrapper.text()).not.toContain('0 events')
  })

  it('hides the count line entirely when a journey holds nothing yet', async () => {
    fetchJourneys.mockResolvedValue([
      {
        tripId: 99,
        title: 'Empty Draft',
        startDate: '2098-08-10',
        endDate: '2098-08-12',
        eventCount: 0,
        placeCount: 0,
        coverImageUrl: null,
      },
    ])
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Empty Draft')
    expect(wrapper.text()).not.toContain('events')
    expect(wrapper.text()).not.toContain('places')
  })
})
