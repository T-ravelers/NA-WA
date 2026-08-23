import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import ReportPersonaTicket from '../../components/presentation/ReportPersonaTicket.vue'
import { seriesInkClass } from '../../components/presentation/seriesPalette'

const { fetchReport, fetchReportComparison, showToast } = vi.hoisted(() => ({
  fetchReport: vi.fn(),
  fetchReportComparison: vi.fn(),
  showToast: vi.fn(),
}))

vi.mock('../../api/reportApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reportApi')>()),
  fetchReport,
  fetchReportComparison,
}))

vi.mock('@/shared/ui/toast', () => ({ showToast }))

const ReportDetailView = (await import('../ReportDetailView.vue')).default

const detail = {
  reportId: 100,
  tripId: 7,
  title: 'Jeju Island',
  startDate: '2026-07-18',
  endDate: '2026-07-27',
  generationStatus: 'COMPLETED',
  locale: 'en',
  generatedAt: '2026-07-28T09:00:00',
  createdAt: '2026-07-28T09:00:00',
  reportContent: {
    journey: {
      tripId: 7,
      title: 'Jeju Island',
      startDate: '2026-07-18',
      endDate: '2026-07-27',
    },
    days: [
      {
        visitDate: '2026-07-18',
        items: [
          {
            tripItemId: 1,
            itemId: 101,
            itemType: 'EVENT',
            title: 'Jeju Night Market',
            status: 'ADDED',
          },
        ],
      },
    ],
  },
  analytics: {
    totalSpent: '1284500.0000',
    dailyAverage: '128450.0000',
    categoryBreakdown: [
      { category: 'FOOD', amount: '1000000.0000', percentage: '77.85' },
      { category: 'OTHER', amount: '284500.0000', percentage: '22.15' },
    ],
    dailyTrend: [
      { date: '2026-07-18', amount: '1284500.0000' },
      { date: '2026-07-19', amount: '0.0000' },
    ],
  },
}

const emptyComparison = {
  scope: 'GROUP' as const,
  basis: 'LIVE' as const,
  me: {
    memberId: 1,
    displayName: 'Me',
    profileImageUrl: null,
    totalSpent: '1284500',
    dailyAverage: '128450',
    categoryBreakdown: [{ category: 'FOOD', amount: '1000000', percentage: '77.85' }],
  },
  peers: [],
  cohort: { size: 0, avgTotalSpent: '0', avgDailyAverage: '0', categoryBreakdown: [] },
  ranks: [],
}

const groupComparison = {
  ...emptyComparison,
  peers: [
    {
      memberId: 2,
      displayName: 'Mina',
      profileImageUrl: null,
      totalSpent: '978400',
      dailyAverage: '97840',
      categoryBreakdown: [{ category: 'SHOPPING', amount: '978400', percentage: '100' }],
    },
  ],
  cohort: {
    size: 1,
    avgTotalSpent: '978400',
    avgDailyAverage: '97840',
    categoryBreakdown: [{ category: 'SHOPPING', amount: '978400', percentage: '100' }],
  },
  ranks: [
    { category: 'FOOD', rank: 1, of: 2 },
    { category: 'OTHER', rank: 1, of: 2 },
  ],
}

const mountedWrappers: VueWrapper[] = []
const queryClients: QueryClient[] = []

async function mountView(path = '/reports/100', reuseClient?: QueryClient) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reports', name: 'report-list', component: { template: '<div>List</div>' } },
      { path: '/reports/:reportId', name: 'report-detail', component: ReportDetailView },
    ],
  })
  const queryClient =
    reuseClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })

  if (reuseClient === undefined) {
    queryClients.push(queryClient)
  }

  await router.push(path)
  await router.isReady()

  const wrapper = mount(ReportDetailView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })
  mountedWrappers.push(wrapper)
  await flushPromises()

  return { router, wrapper, queryClient }
}

describe('ReportDetailView', () => {
  beforeEach(() => {
    fetchReport.mockReset()
    fetchReport.mockResolvedValue(detail)
    fetchReportComparison.mockReset()
    fetchReportComparison.mockResolvedValue(emptyComparison)
    showToast.mockReset()
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    queryClients.splice(0).forEach((client) => client.clear())
    Reflect.deleteProperty(navigator, 'share')
    Reflect.deleteProperty(navigator, 'clipboard')
  })

  it('renders the immutable snapshot and accessible dashboard analytics without excluded controls', async () => {
    const { wrapper } = await mountView()

    expect(fetchReport).toHaveBeenCalledWith(100)
    expect(wrapper.get('h1').text()).toBe('Report')
    expect(wrapper.text()).toContain('Jeju Night Market · EVENT · ADDED')
    expect(wrapper.text()).toContain('1,284,500 P')
    expect(wrapper.text()).toContain('78%')
    expect(wrapper.text()).toContain('2026.07.19')
    expect(wrapper.text()).toContain('0 P')
    expect(wrapper.find('polyline').exists()).toBe(true)
    expect(wrapper.findAll('table')).toHaveLength(0)
    expect(wrapper.findAll('button').some((button) => button.text() === 'Group')).toBe(false)
    expect(wrapper.text()).not.toContain('similar travelers')
    expect(wrapper.text()).toContain('Travel spending type')
    expect(wrapper.text()).toContain('events')
    expect(wrapper.findAll('h2').map((heading) => heading.text())).toEqual([
      'Your spending type',
      'Analysis',
      'By category',
      'Spending trend',
      'Vs. group members',
      'Journey snapshot',
      'Saved itinerary',
    ])
    // 칭호는 섹션 제목 아래에 놓인다. 제목만 훑는 사용자가 맥락 없이 해시태그부터 만나지 않는다.
    expect(wrapper.findAll('h3').map((heading) => heading.text())).toContain('#FLAVORSEEKER')
    expect(wrapper.get('button[aria-label="Back to reports"]').text()).toBe('')
  })

  it('keeps legacy reports readable when analytics are absent', async () => {
    fetchReport.mockResolvedValueOnce({ ...detail, analytics: null })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Jeju Island')
    expect(wrapper.text()).toContain('Spending analysis unavailable')
    expect(wrapper.text()).toContain('created before spending analytics were available')
    expect(wrapper.findAll('h2').map((heading) => heading.text())).toEqual([
      'Analysis',
      'Journey snapshot',
      'Saved itinerary',
    ])
  })

  it('shows the explicit zero-spending branch', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      analytics: {
        totalSpent: '0.0000',
        dailyAverage: '0.0000',
        categoryBreakdown: [],
        dailyTrend: [{ date: '2026-07-18', amount: '0.0000' }],
      },
    })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('No spending selected')
    expect(wrapper.text()).toContain('No category spending was recorded.')
    expect(wrapper.text()).not.toContain('#FREESPENDER')
  })

  // 목록 카드가 `5 events · 9 places`로 나눠 보여주므로, 상세가 합계를 세면 숫자가 어긋난다.
  it('counts only events in the donut centre, not saved places', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      reportContent: {
        ...detail.reportContent,
        days: [
          {
            visitDate: '2026-07-18',
            items: [
              {
                tripItemId: 1,
                itemId: 101,
                itemType: 'EVENT',
                title: 'Night Market',
                status: 'ADDED',
              },
              { tripItemId: 2, itemId: 102, itemType: 'PLACE', title: 'Seongsan', status: 'ADDED' },
              { tripItemId: 3, itemId: 103, itemType: 'PLACE', title: 'Hallasan', status: 'ADDED' },
            ],
          },
          {
            visitDate: '2026-07-19',
            items: [
              {
                tripItemId: 4,
                itemId: 104,
                itemType: 'EVENT',
                title: 'Fireworks',
                status: 'ADDED',
              },
            ],
          },
        ],
      },
    })
    const { wrapper } = await mountView()

    // 도넛 가운데 블록. 티켓 절취선도 `absolute`라 `inset-0`까지 짚는다.
    const centre = wrapper.get('.absolute.inset-0')

    expect(centre.text()).toBe('2events')
  })

  // 도넛 가운데 라벨은 숫자와 따로 그려서 개수를 받지 못하면 늘 복수였다(#412).
  it('writes the donut centre label in the singular when the journey has one event', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      reportContent: {
        ...detail.reportContent,
        days: [
          {
            visitDate: '2026-07-18',
            items: [
              {
                tripItemId: 1,
                itemId: 101,
                itemType: 'EVENT',
                title: 'Night Market',
                status: 'ADDED',
              },
              { tripItemId: 2, itemId: 102, itemType: 'PLACE', title: 'Seongsan', status: 'ADDED' },
            ],
          },
        ],
      },
    })
    const { wrapper } = await mountView()

    expect(wrapper.get('.absolute.inset-0').text()).toBe('1event')
  })

  // 티켓과 도넛이 같은 카테고리에 같은 색을 줘야 한다(시안 R4).
  it('gives the ticket and the donut the same colour for the leading category', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      analytics: {
        ...detail.analytics,
        categoryBreakdown: [
          { category: 'SHOPPING', amount: '900000.0000', percentage: '70.07' },
          { category: 'FOOD', amount: '384500.0000', percentage: '29.93' },
        ],
      },
    })
    const { wrapper } = await mountView()

    // 티켓 배경과 도넛 조각·범례 표식이 모두 shopping 색이다.
    expect(wrapper.getComponent(ReportPersonaTicket).props('tone')).toBe('shopping')
    expect(seriesInkClass('SHOPPING')).toBe('text-shopping')
    expect(wrapper.findAll('.text-shopping').length).toBeGreaterThan(0)
  })

  // 코어색이 없는 세 카테고리는 티켓을 종이톤으로 둔다. 도넛에는 색이 있다.
  it('falls back to the paper ticket when the leading category has no core colour', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      analytics: {
        ...detail.analytics,
        categoryBreakdown: [
          { category: 'TRANSPORT', amount: '900000.0000', percentage: '70.07' },
          { category: 'FOOD', amount: '384500.0000', percentage: '29.93' },
        ],
      },
    })
    const { wrapper } = await mountView()

    expect(wrapper.getComponent(ReportPersonaTicket).props('tone')).toBe('paper')
    expect(seriesInkClass('TRANSPORT')).toBe('text-status-ongoing')
    expect(wrapper.findAll('.text-status-ongoing').length).toBeGreaterThan(0)
  })

  it('names a spending persona from the top category and fills in its share', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('#FLAVORSEEKER')
    expect(wrapper.text()).toContain(
      'You followed your appetite — 78% of this journey went to food.',
    )
  })

  // 백엔드가 금액 내림차순으로 정렬해 주므로 첫 항목이 1위다.
  it('follows the breakdown order when another category leads', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      analytics: {
        ...detail.analytics,
        categoryBreakdown: [
          { category: 'STAY', amount: '900000.0000', percentage: '70.07' },
          { category: 'FOOD', amount: '384500.0000', percentage: '29.93' },
        ],
      },
    })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('#SLOWTRAVELER')
    expect(wrapper.text()).not.toContain('#FLAVORSEEKER')
  })

  it('translates category codes instead of printing them raw', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Food')
    expect(wrapper.text()).toContain('Other')
    expect(wrapper.text()).not.toContain('FOOD')
  })

  it('does not request an invalid route id and navigates back', async () => {
    const { router, wrapper } = await mountView('/reports/not-a-number')

    expect(fetchReport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Invalid report link')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/reports')
  })

  it('renders forbidden and not-found states distinctly', async () => {
    fetchReport.mockRejectedValueOnce(new NormalizedApiError('REPORT-002', 403, 'forbidden'))
    const forbidden = await mountView()
    expect(forbidden.wrapper.text()).toContain('This report is private')

    fetchReport.mockReset()
    fetchReport.mockRejectedValueOnce(new NormalizedApiError('REPORT-001', 404, 'missing'))
    const missing = await mountView()
    expect(missing.wrapper.text()).toContain('Report not found')
  })
  it('asks for the group comparison only when the snapshot has spending', async () => {
    await mountView()

    expect(fetchReportComparison).toHaveBeenCalledWith(100, 'GROUP')
    expect(fetchReportComparison).toHaveBeenCalledTimes(1)
  })

  it('explains that there is nobody to compare with when the group is empty', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('No group members yet')
    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
  })

  it('compares total spend, category balance and ranks against group members', async () => {
    fetchReportComparison.mockResolvedValueOnce(groupComparison)
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Total spend')
    expect(wrapper.findAll('[role="radio"]').map((chip) => chip.text())).toEqual(['MMina'])
    expect(wrapper.text()).toContain('978,400 P')
    expect(wrapper.text()).toContain('Category balance')
    // 레이더 축: 내 FOOD·OTHER + 코호트 SHOPPING — 세 축
    // 추이 차트도 sr-only 목록을 가지므로 레이더 것만 고른다.
    const radarList = wrapper
      .findAll('ul.sr-only')
      .find((list) => list.text().includes('Group avg'))
    expect(radarList?.text()).toContain('Food: You 78%, Group avg 0%')
    expect(radarList?.text()).toContain('Shopping: You 0%, Group avg 100%')
    expect(wrapper.findAll('li.rounded-card').map((tile) => tile.text())).toEqual([
      '# Food1st',
      '# Other1st',
    ])
  })

  it('warns that the comparison total is recalculated, but only when the basis is live', async () => {
    fetchReportComparison.mockResolvedValueOnce(groupComparison)
    const live = await mountView()

    expect(live.wrapper.text()).toContain('We recalculate this from the payments made')

    fetchReportComparison.mockReset()
    fetchReportComparison.mockResolvedValue({ ...groupComparison, basis: 'SNAPSHOT' as const })
    const snapshot = await mountView()

    expect(snapshot.wrapper.text()).not.toContain('We recalculate this from the payments made')
  })

  it('says only the comparison failed, not the whole screen', async () => {
    fetchReportComparison.mockReset()
    fetchReportComparison.mockRejectedValue(new NormalizedApiError('UNKNOWN', 500, 'boom'))
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('We could not load the comparison.')
    expect(wrapper.text()).toContain('The rest of this report is unaffected.')
    expect(wrapper.text()).not.toContain('We could not load this screen.')
    // 나머지 리포트는 그대로 남는다.
    expect(wrapper.text()).toContain('Jeju Night Market')
  })

  it('skips the comparison for zero-spending reports', async () => {
    fetchReport.mockResolvedValueOnce({
      ...detail,
      analytics: {
        totalSpent: '0.0000',
        dailyAverage: '0.0000',
        categoryBreakdown: [],
        dailyTrend: [{ date: '2026-07-18', amount: '0.0000' }],
      },
    })
    const { wrapper } = await mountView()

    expect(fetchReportComparison).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Vs. group members')
  })

  // ── 공유(#417) — 시안 R4의 헤더 아이콘·티켓 `Share ticket`·하단 `Confirm & Share` ──
  // jsdom에는 공유 시트도 클립보드도 없다. 기기별 분기를 그대로 흉내 낸다.
  function stubNavigator(share: unknown, clipboard: unknown): void {
    Object.defineProperty(navigator, 'share', { value: share, configurable: true })
    Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true })
  }

  function buttonByText(wrapper: VueWrapper, text: string) {
    const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)

    if (button === undefined) {
      throw new Error(`button "${text}" not found`)
    }

    return button
  }

  // 리포트 상세는 작성자만 열 수 있으므로 링크가 아니라 문장을 보낸다.
  it('shares the ticket and the report summary as text, not as a link', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    stubNavigator(share, undefined)
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Share ticket').trigger('click')
    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await buttonByText(wrapper, 'Confirm & Share').trigger('click')
    await flushPromises()

    expect(share).toHaveBeenCalledTimes(3)
    expect(share.mock.calls[0]?.[0]).toEqual({
      title: 'My travel spending type',
      text: '#FLAVORSEEKER\nYou followed your appetite — 78% of this journey went to food.',
    })
    const summary = share.mock.calls[1]?.[0] as { title: string; text: string; url?: string }
    expect(summary.title).toBe('My travel report')
    expect(summary.text).toContain('Jeju Island')
    expect(summary.text).toContain('#FLAVORSEEKER')
    expect(summary.text).toContain('1,284,500 P')
    expect(summary.text).toContain('78% on Food')
    expect(summary.url).toBeUndefined()
    expect(share.mock.calls[2]?.[0]).toEqual(summary)
    expect(showToast).not.toHaveBeenCalled()
  })

  it('copies the text and says so when there is no share sheet', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubNavigator(undefined, { writeText })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(writeText.mock.calls[0]?.[0]).toContain('#FLAVORSEEKER')
    expect(showToast).toHaveBeenCalledWith('Report text copied.')
  })

  // 복사한 것이 티켓이면 티켓이라고 말한다. 두 자리가 같은 문구를 쓰면 무엇을 복사했는지 어긋난다.
  it('names the ticket when the ticket text is the thing copied', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubNavigator(undefined, { writeText })
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Share ticket').trigger('click')
    await flushPromises()

    expect(writeText.mock.calls[0]?.[0]).toBe(
      '#FLAVORSEEKER\nYou followed your appetite — 78% of this journey went to food.',
    )
    expect(showToast).toHaveBeenCalledWith('Ticket text copied.')
  })

  // 취소가 아닌 거절은 실패다. `web-share` 권한이 없는 iframe과 인앱 브라우저가 여기에 걸린다.
  // 이때 폴백까지 막으면 시트도 토스트도 없이 끝나 버튼이 고장 난 것처럼 보인다.
  it('falls back to the clipboard when the share sheet rejects for a reason other than dismissal', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubNavigator(vi.fn().mockRejectedValue(new DOMException('blocked', 'NotAllowedError')), {
      writeText,
    })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(writeText.mock.calls[0]?.[0]).toContain('#FLAVORSEEKER')
    expect(showToast).toHaveBeenCalledWith('Report text copied.')
  })

  // 시트가 거절되고 복사까지 실패하면 그때는 안내가 있어야 한다.
  it('tells the user when both the share sheet and the clipboard fail', async () => {
    stubNavigator(vi.fn().mockRejectedValue(new DOMException('blocked', 'NotAllowedError')), {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    // 기기가 못 하는 것이 아니라 쓰기가 실패한 것이므로 문구가 다르다.
    expect(showToast).toHaveBeenCalledWith('We could not copy the text. Please try again.')
  })

  it('tells the user when neither the share sheet nor the clipboard exists', async () => {
    stubNavigator(undefined, undefined)
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Share ticket').trigger('click')
    await flushPromises()

    expect(showToast).toHaveBeenCalledWith('Sharing is not available on this device.')
  })

  // 시트를 닫아 취소한 것은 실패가 아니다. 안내가 뜨면 취소할 때마다 오류처럼 보인다.
  it('stays quiet when the share sheet is dismissed', async () => {
    stubNavigator(vi.fn().mockRejectedValue(new DOMException('dismissed', 'AbortError')), undefined)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    expect(showToast).not.toHaveBeenCalled()
  })

  // 시트가 이미 열려 있는데 한 번 더 누른 것도 실패가 아니다. 클립보드로 떨어지면 네이티브
  // 시트 위에 「복사했다」 토스트가 겹치고, 누르지도 않은 복사가 끝나 있다.
  it('stays quiet when the share sheet is already open', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubNavigator(vi.fn().mockRejectedValue(new DOMException('in progress', 'InvalidStateError')), {
      writeText,
    })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    expect(writeText).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })

  // 취소를 `instanceof`로 판정하면 안 된다 — jsdom에서 `DOMException`은 `Error`를 상속하지
  // 않고, `navigator.share`를 JS 브리지로 얹는 인앱 브라우저는 이름만 가진 값을 던진다.
  it('treats a cancellation as a cancellation even when it is not a DOMException', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const dismissed = new Error('user canceled')
    dismissed.name = 'AbortError'
    stubNavigator(vi.fn().mockRejectedValue(dismissed), { writeText })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    expect(writeText).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })

  // 칭호가 없으면 티켓도 없으니 티켓 공유도 없다. 리포트 요약은 여정과 기간만으로 보낸다.
  it('keeps report sharing without the ticket button when there is no persona', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    stubNavigator(share, undefined)
    fetchReport.mockResolvedValueOnce({ ...detail, analytics: null })
    const { wrapper } = await mountView()

    expect(wrapper.findAll('button').some((button) => button.text() === 'Share ticket')).toBe(false)
    await wrapper.get('button[aria-label="Share report"]').trigger('click')
    await flushPromises()

    const summary = share.mock.calls[0]?.[0] as { text: string }
    expect(summary.text).toContain('Jeju Island')
    expect(summary.text).toContain('final travel report')
    expect(summary.text).not.toContain('#')
  })

  // 캐시가 있는 재방문에서 재요청이 실패하면 `data`는 남고 `isError`만 켜진다. 본문이 오류
  // 화면인데 헤더에 공유 아이콘만 남으면, 화면이 못 보여 준 리포트를 공유하게 된다.
  it('hides the header share icon when a refetch fails on a cached report', async () => {
    const { wrapper: cached, queryClient } = await mountView()
    expect(cached.find('button[aria-label="Share report"]').exists()).toBe(true)

    fetchReport.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountView('/reports/100', queryClient)

    expect(wrapper.text()).toContain('We could not load this report.')
    expect(wrapper.find('button[aria-label="Share report"]').exists()).toBe(false)
  })
})
