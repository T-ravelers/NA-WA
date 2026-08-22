import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import ReportPersonaTicket from '../../components/presentation/ReportPersonaTicket.vue'
import { seriesInkClass } from '../../components/presentation/seriesPalette'

const { fetchReport, fetchReportComparison } = vi.hoisted(() => ({
  fetchReport: vi.fn(),
  fetchReportComparison: vi.fn(),
}))

vi.mock('../../api/reportApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reportApi')>()),
  fetchReport,
  fetchReportComparison,
}))

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

/** 동료 칩 라디오 그룹. 비교 범위 세그먼트도 라디오 그룹이라 이름으로 가른다. */
const MEMBER_CHIPS = '[role="radiogroup"][aria-label="Group members"]'

const mountedWrappers: VueWrapper[] = []
const queryClients: QueryClient[] = []

async function mountView(path = '/reports/100') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reports', name: 'report-list', component: { template: '<div>List</div>' } },
      { path: '/reports/:reportId', name: 'report-detail', component: ReportDetailView },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClients.push(queryClient)
  await router.push(path)
  await router.isReady()

  const wrapper = mount(ReportDetailView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })
  mountedWrappers.push(wrapper)
  await flushPromises()

  return { router, wrapper }
}

describe('ReportDetailView', () => {
  beforeEach(() => {
    fetchReport.mockReset()
    fetchReport.mockResolvedValue(detail)
    fetchReportComparison.mockReset()
    fetchReportComparison.mockResolvedValue(emptyComparison)
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    queryClients.splice(0).forEach((client) => client.clear())
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
    expect(wrapper.find('button[aria-label="Share"]').exists()).toBe(false)
    // 비교 범위 세그먼트(#421). 기본은 Group이고 Similar는 탭으로만 들어간다.
    expect(wrapper.findAll('button').some((button) => button.text() === 'Group')).toBe(true)
    expect(wrapper.find('[data-testid="segment-SIMILAR"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Vs. similar travelers')
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

  it('keeps the insight without a comparison when there are no similar travelers', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('You leaned into food — 78% of this journey.')
    expect(wrapper.text()).not.toContain('travelers like you')
  })
})
