import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

const { fetchReport } = vi.hoisted(() => ({ fetchReport: vi.fn() }))

vi.mock('../../api/reportApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reportApi')>()),
  fetchReport,
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
    expect(wrapper.findAll('button').some((button) => button.text() === 'Group')).toBe(false)
    expect(wrapper.text()).not.toContain('similar travelers')
    expect(wrapper.findAll('h2').map((heading) => heading.text())).toEqual([
      'Analysis',
      'Flavor Seeker',
      'By category',
      'Spending trend',
      'Journey snapshot',
      'Saved itinerary',
    ])
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
    expect(wrapper.text()).not.toContain('Free Spender')
  })

  it('names a spending persona from the top category and fills in its share', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Flavor Seeker')
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

    expect(wrapper.text()).toContain('Slow Traveler')
    expect(wrapper.text()).not.toContain('Flavor Seeker')
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
})
