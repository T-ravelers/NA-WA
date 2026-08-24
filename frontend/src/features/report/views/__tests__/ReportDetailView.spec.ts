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

/**
 * 일곱 카테고리를 전부 쓴 경우(#434). 레이더 축은 6, 타일은 4가 상한이라 각각 하나·셋이
 * 밀린다. 비중은 내림차순이라 밀리는 것이 무엇인지 예측할 수 있다.
 */
const ALL_CATEGORIES = [
  ['FOOD', '30'],
  ['SHOPPING', '25'],
  ['BEAUTY', '15'],
  ['SHOW', '12'],
  ['TRANSPORT', '10'],
  ['STAY', '5'],
  ['OTHER', '3'],
] as const

const crowdedComparison = {
  ...groupComparison,
  me: {
    ...groupComparison.me,
    categoryBreakdown: ALL_CATEGORIES.map(([category, percentage]) => ({
      category,
      amount: percentage,
      percentage,
    })),
  },
  ranks: ALL_CATEGORIES.map(([category], index) => ({
    category,
    rank: index + 1,
    of: 4,
  })),
}

/** 동료 칩 라디오 그룹. 비교 범위 세그먼트도 라디오 그룹이라 이름으로 가른다. */
const MEMBER_CHIPS = '[role="radiogroup"][aria-label="Group members"]'

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
    // 비교 범위 세그먼트(#421). 기본은 Group이고 Similar는 탭으로만 들어간다.
    expect(wrapper.findAll('button').some((button) => button.text() === 'Group')).toBe(true)
    expect(wrapper.find('[data-testid="segment-SIMILAR"]').exists()).toBe(true)
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
  // SIMILAR는 인사이트 문장도 쓰므로 탭을 누르기 전에 같이 받는다.
  it('asks for both comparison scopes when the snapshot has spending', async () => {
    await mountView()

    expect(fetchReportComparison).toHaveBeenCalledWith(100, 'GROUP')
    expect(fetchReportComparison).toHaveBeenCalledWith(100, 'SIMILAR')
    expect(fetchReportComparison).toHaveBeenCalledTimes(2)
  })

  it('explains that there is nobody to compare with when the group is empty', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('No group members yet')
    // 세그먼트도 라디오 그룹이라 동료 칩 그룹은 이름으로 고른다.
    expect(wrapper.find(MEMBER_CHIPS).exists()).toBe(false)
  })

  it('compares total spend, category balance and ranks against group members', async () => {
    fetchReportComparison.mockResolvedValueOnce(groupComparison)
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Total spend')
    expect(wrapper.findAll(`${MEMBER_CHIPS} [role="radio"]`).map((chip) => chip.text())).toEqual([
      'MMina',
    ])
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

  // ── Similar 탭 + 인사이트(#421) ──
  const similarComparison = {
    ...emptyComparison,
    scope: 'SIMILAR' as const,
    basis: 'SNAPSHOT' as const,
    me: {
      ...emptyComparison.me,
      categoryBreakdown: [
        { category: 'FOOD', amount: '770700', percentage: '60' },
        { category: 'SHOPPING', amount: '513800', percentage: '40' },
      ],
    },
    cohort: {
      size: 12,
      avgTotalSpent: '1052000',
      avgDailyAverage: '105200',
      categoryBreakdown: [
        { category: 'FOOD', amount: '504960', percentage: '48' },
        { category: 'SHOPPING', amount: '378720', percentage: '36' },
        { category: 'SHOW', amount: '168320', percentage: '16' },
      ],
    },
  }

  function mockScopes(group: Record<string, unknown>, similar: Record<string, unknown>): void {
    fetchReportComparison.mockImplementation((_reportId: number, scope: string) =>
      Promise.resolve(scope === 'SIMILAR' ? similar : group),
    )
  }

  function withCohortFood(percentage: string) {
    return {
      ...similarComparison,
      cohort: {
        ...similarComparison.cohort,
        categoryBreakdown: [{ category: 'FOOD', amount: '1', percentage }],
      },
    }
  }

  it('switches to similar travelers: one average bar, signed share tiles, no member chips', async () => {
    mockScopes(groupComparison, similarComparison)
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="segment-SIMILAR"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('h2').map((heading) => heading.text())).toContain(
      'Vs. similar travelers',
    )
    expect(wrapper.find(MEMBER_CHIPS).exists()).toBe(false)
    // KPI 카드도 dt를 쓰므로 막대 라벨만 본다. 48px 칸에 잘리지 않게 `AVG`로 적는다.
    const barLabels = wrapper.findAll('dt').map((cell) => cell.text())
    expect(barLabels).toContain('You')
    expect(barLabels).toContain('AVG')
    expect(barLabels).not.toContain('Travelers avg')
    expect(wrapper.text()).toContain('1,052,000 P')
    const radarList = wrapper
      .findAll('ul.sr-only')
      .find((list) => list.text().includes('Travelers avg'))
    expect(radarList?.text()).toContain('Food: You 60%, Travelers avg 48%')
    // 내 비중 − 코호트 비중: Food +12, Shopping +4(±5 안 → AVG), Shows −16
    expect(wrapper.findAll('li.rounded-card').map((tile) => tile.text())).toEqual([
      '# Food+12%',
      '# ShoppingAVG',
      '# Shows-16%',
    ])
  })

  it('explains when there are no similar travelers yet', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="segment-SIMILAR"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No similar travelers yet')
    expect(wrapper.find('li.rounded-card').exists()).toBe(false)
  })

  // 칭호의 1위 카테고리(FOOD 78%)를 같은 국적 코호트와 견준다. 단어는 그 카테고리 색으로 강조한다.
  it.each([
    ['48', 'well above travelers like you (48%)'],
    ['75', 'about the same as travelers like you (75%)'],
    ['90', 'below travelers like you (90%)'],
  ])(
    'compares the persona category with similar travelers in one sentence (cohort %s%%)',
    async (cohortShare, expected) => {
      mockScopes(emptyComparison, withCohortFood(cohortShare))
      const { wrapper } = await mountView()

      expect(wrapper.text()).toContain(`You leaned into food — 78% of this journey, ${expected}.`)
      expect(wrapper.get('p span.font-semibold').text()).toBe('food')
      expect(wrapper.get('p span.font-semibold').classes()).toContain(seriesInkClass('FOOD'))
    },
  )

  /*
   * 계열색을 글자로 쓰는 두 자리는 카드 위에 두지 않는다(#476).
   *
   * `AppCard`의 면은 `surface-1`(#262626)이고 그 위에서 `text-shopping`·`text-show`가
   * 4.21:1로 AA에 못 미친다. canvas(#171717) 위에서는 4.99부터라 통과한다. 값 자체는
   * `app/styles/__tests__/tokens.spec.ts`가 지키고, **어느 면 위에 놓였는지는 여기서**
   * 지킨다 — 카드로 되돌리면 토큰 테스트는 초록인 채 대비만 무너진다.
   */
  function hasCardAncestor(element: Element): boolean {
    for (let node = element.parentElement; node !== null; node = node.parentElement) {
      if (node.classList.contains('bg-surface-1')) return true
    }

    return false
  }

  it('keeps the insight sentence off the card surface', async () => {
    mockScopes(emptyComparison, withCohortFood('48'))
    const { wrapper } = await mountView()

    const category = wrapper.get('p span.font-semibold')

    expect(category.classes()).toContain(seriesInkClass('FOOD'))
    expect(hasCardAncestor(category.element)).toBe(false)
  })

  it('keeps the radar axis labels off the card surface', async () => {
    // 레이더는 비교할 동료가 있을 때만 그려진다.
    fetchReportComparison.mockResolvedValueOnce(groupComparison)
    const { wrapper } = await mountView()

    const labels = wrapper
      .findAll('span')
      .filter((node) => node.classes().includes(seriesInkClass('FOOD')) && node.text() === 'Food')

    expect(labels.length).toBeGreaterThan(0)
    labels.forEach((label) => {
      expect(hasCardAncestor(label.element)).toBe(false)
    })
  })

  // 비교 없는 문장은 바로 위 칭호 티켓의 되풀이라 카드를 그리지 않는다. 질의가 실패해도 같다 —
  // 「비교할 사람이 없다」로 읽히면 안 된다.
  it.each([
    ['코호트가 비었을 때', () => mockScopes(emptyComparison, emptyComparison)],
    [
      'SIMILAR 질의가 실패했을 때',
      () =>
        fetchReportComparison.mockImplementation((_reportId: number, scope: string) =>
          scope === 'SIMILAR'
            ? Promise.reject(new NormalizedApiError('UNKNOWN', 500, 'boom'))
            : Promise.resolve(groupComparison),
        ),
    ],
  ])('hides the insight card — %s', async (_name, arrange) => {
    arrange()
    const { wrapper } = await mountView()

    expect(wrapper.text()).not.toContain('You leaned into')
    // 나머지 리포트와 GROUP 비교는 그대로 남는다.
    expect(wrapper.text()).toContain('Travel spending type')
  })

  // 화면은 비중을 정수로 반올림해 찍는다. 판정도 반올림 뒤에 해야 보이는 숫자와 말이 맞는다.
  it('judges the insight on the rounded shares, not the raw ones', async () => {
    // 78% 대 73%(원값 77.85 대 72.6) — 보이는 차이는 5라 `about the same`이다.
    mockScopes(emptyComparison, withCohortFood('72.6'))
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('about the same as travelers like you (73%)')
    expect(wrapper.text()).not.toContain('well above')
  })

  // 레이더는 다각형을 만들려고 축을 셋까지 채운다. 그 패딩이 타일로 오면 안 쓴 카테고리에 `AVG`가 찍힌다.
  /*
   * #434 — 비교 섹션이 「받은 것을 안 쓰거나 조용히 버리는」 세 자리.
   */

  it('says how many people the rank is measured against', async () => {
    // ranks[].of는 나를 포함한 인원이다. 버리면 동료가 한 명인 그룹의 `# Food 1ST`가
    // 사실상 「둘 중 하나」인데 화면만 보면 대단해 보인다.
    fetchReportComparison.mockResolvedValueOnce(groupComparison)
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Ranked among 2 members')
  })

  it('does not claim a rank basis on the similar scope', async () => {
    // SIMILAR 타일은 순위가 아니라 코호트 대비 비중이라 모수가 뜻을 갖지 않는다.
    mockScopes(emptyComparison, withCohortFood('48'))
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="segment-SIMILAR"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Ranked among')
  })

  it('leaves the axis that does not fit the radar readable', async () => {
    // 축 상한은 6이고 소비 카테고리는 7종이라 하나가 밀린다. 잘린 것이 아무 데도 안 남으면
    // 「쓰지 않은 것」과 구별되지 않는다.
    fetchReportComparison.mockResolvedValueOnce(crowdedComparison)
    const { wrapper } = await mountView()

    const omitted = wrapper.findAll('p.sr-only').map((node) => node.text())

    expect(omitted).toContain('Not shown here: Other')
  })

  it('leaves the categories that do not fit the tiles readable', async () => {
    // 타일 상한은 4라 셋이 밀린다.
    fetchReportComparison.mockResolvedValueOnce(crowdedComparison)
    const { wrapper } = await mountView()

    // 🔴 보이는 타일이 상한을 지키는지 함께 본다. 이것 없이 문구만 단언하면, 상한이
    // 풀려 일곱 개가 다 보이는데도 "여기 없다"고 말하는 상태를 잡지 못한다.
    expect(wrapper.findAll('li.rounded-card').map((tile) => tile.text())).toEqual([
      '# Food1st',
      '# Shopping2nd',
      '# Beauty3rd',
      '# Shows4th',
    ])

    const omitted = wrapper.findAll('p.sr-only').map((node) => node.text())

    expect(omitted).toContain('Not shown here: Transport, Stay, Other')
  })

  it('keeps radar padding axes out of the similar tiles', async () => {
    const onlyFood = {
      ...similarComparison,
      me: {
        ...similarComparison.me,
        categoryBreakdown: [{ category: 'FOOD', amount: '1284500', percentage: '100' }],
      },
      cohort: {
        ...similarComparison.cohort,
        categoryBreakdown: [{ category: 'FOOD', amount: '1052000', percentage: '100' }],
      },
    }
    mockScopes(groupComparison, onlyFood)
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="segment-SIMILAR"]').trigger('click')
    await flushPromises()

    // 레이더는 세 축을 그대로 쓰고, 타일만 실제로 쓴 카테고리 하나로 줄어든다.
    expect(wrapper.findAll('li.rounded-card').map((tile) => tile.text())).toEqual(['# FoodAVG'])
  })

  it('says what the tiles are measured against, per scope', async () => {
    mockScopes(groupComparison, similarComparison)
    const { wrapper } = await mountView()

    expect(wrapper.get('ul.grid').attributes('aria-label')).toBe(
      'Category rank among group members',
    )

    await wrapper.get('[data-testid="segment-SIMILAR"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('ul.grid').attributes('aria-label')).toBe(
      'Your category share compared with travelers like you',
    )
  })
})
