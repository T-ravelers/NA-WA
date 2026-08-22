import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

const { createReport, fetchReportExpenseCandidates, fetchReportJourneys, fetchReports } =
  vi.hoisted(() => ({
    createReport: vi.fn(),
    fetchReportExpenseCandidates: vi.fn(),
    fetchReportJourneys: vi.fn(),
    fetchReports: vi.fn(),
  }))

vi.mock('../../api/reportApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reportApi')>()),
  createReport,
  fetchReportExpenseCandidates,
  fetchReportJourneys,
  fetchReports,
}))

const ReportsView = (await import('../ReportsView.vue')).default

const journeys = [
  {
    tripId: 42,
    title: 'Future Journey',
    startDate: '2098-08-10',
    endDate: '2098-08-12',
    eventCount: 2,
    placeCount: 1,
  },
  {
    tripId: 9,
    title: 'Jeju Island',
    startDate: '2021-07-18',
    endDate: '2021-07-27',
    eventCount: 5,
    placeCount: 9,
  },
  {
    tripId: 7,
    title: 'Busan Weekender',
    startDate: '2020-08-10',
    endDate: '2020-08-12',
    eventCount: 0,
    placeCount: 0,
  },
  {
    tripId: 8,
    title: 'Gyeongju Day Trip',
    startDate: '2019-05-01',
    endDate: '2019-05-03',
    eventCount: 0,
    placeCount: 3,
  },
]

const summary = {
  reportId: 100,
  tripId: 7,
  title: 'Busan Weekender',
  startDate: '2020-08-10',
  endDate: '2020-08-12',
  generationStatus: 'COMPLETED',
  locale: 'en',
  generatedAt: '2020-08-13T10:00:00',
  createdAt: '2020-08-13T10:00:00',
}

const createdDetail = {
  ...summary,
  reportId: 101,
  tripId: 9,
  title: 'Jeju Island',
  startDate: '2021-07-18',
  endDate: '2021-07-27',
  reportContent: {
    journey: {
      tripId: 9,
      title: 'Jeju Island',
      startDate: '2021-07-18',
      endDate: '2021-07-27',
    },
    days: [],
  },
  analytics: null,
}

const mountedWrappers: VueWrapper[] = []
const queryClients: QueryClient[] = []

async function mountView(path = '/reports') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reports', name: 'report-list', component: ReportsView },
      {
        path: '/reports/:reportId',
        name: 'report-detail',
        component: { template: '<div>Detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  })
  queryClients.push(queryClient)
  await router.push(path)
  await router.isReady()

  const wrapper = mount(ReportsView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })
  mountedWrappers.push(wrapper)
  await flushPromises()

  return { queryClient, router, wrapper }
}

function findButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((button) => button.text() === label)
}

describe('ReportsView', () => {
  beforeEach(() => {
    createReport.mockReset()
    fetchReportExpenseCandidates.mockReset()
    fetchReportJourneys.mockReset()
    fetchReports.mockReset()
    fetchReportJourneys.mockResolvedValue(journeys)
    fetchReports.mockResolvedValue([summary])
    fetchReportExpenseCandidates.mockResolvedValue({ tripId: 9, candidates: [] })
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    queryClients.splice(0).forEach((client) => client.clear())
  })

  it('shows only ended journeys and opens an existing final report', async () => {
    const { router, wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('Reports')
    expect(wrapper.text()).toContain('Jeju Island')
    expect(wrapper.text()).toContain('Busan Weekender')
    expect(wrapper.text()).toContain('5 events · 9 places')
    expect(wrapper.text()).toContain('3 places')
    expect(wrapper.text()).not.toContain('0 events')
    expect(wrapper.text()).not.toContain('0 places')
    expect(wrapper.text()).not.toContain('Future Journey')

    await findButton(wrapper, 'View final report')?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/reports/100')
  })

  it('allows an empty selection, prevents duplicate submit, and navigates on success', async () => {
    fetchReports.mockResolvedValue([])
    let resolveCreate: ((value: typeof createdDetail) => void) | undefined
    createReport.mockImplementation(
      () =>
        new Promise<typeof createdDetail>((resolve) => {
          resolveCreate = resolve
        }),
    )
    const { router, wrapper } = await mountView()

    await findButton(wrapper, 'Choose expenses')?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No eligible expenses')

    const submit = findButton(wrapper, 'Generate zero-spending report')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createReport).toHaveBeenCalledTimes(1)
    expect(createReport.mock.calls[0]?.[0]).toEqual({ tripId: 9, transferIds: [] })
    expect(submit?.attributes('disabled')).toBeDefined()

    await wrapper.get('form').trigger('submit')
    expect(createReport).toHaveBeenCalledTimes(1)

    resolveCreate?.(createdDetail)
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/reports/101')
  })

  it('initializes server-selected candidates and submits their stable transfer ids', async () => {
    fetchReports.mockResolvedValue([])
    fetchReportExpenseCandidates.mockResolvedValue({
      tripId: 9,
      candidates: [
        {
          transferId: 30,
          amount: '18000.0000',
          occurredDate: '2021-07-18',
          category: 'FOOD',
          displayMemo: 'Night market',
          selected: true,
        },
        {
          transferId: 10,
          amount: '5000.0000',
          occurredDate: '2021-07-19',
          category: 'OTHER',
          displayMemo: null,
          selected: true,
        },
      ],
    })
    createReport.mockResolvedValue(createdDetail)
    const { wrapper } = await mountView()

    await findButton(wrapper, 'Choose expenses')?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('18,000 P')
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(2)

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createReport.mock.calls[0]?.[0]).toEqual({ tripId: 9, transferIds: [10, 30] })
  })

  it('refetches and opens the existing report after a generation conflict', async () => {
    fetchReports.mockReset()
    fetchReports
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ ...summary, tripId: 9, title: 'Jeju Island' }])
    createReport.mockRejectedValue(
      new NormalizedApiError('REPORT-005', 409, 'Report already exists'),
    )
    const { router, wrapper } = await mountView()

    await findButton(wrapper, 'Choose expenses')?.trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchReports).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value.fullPath).toBe('/reports/100')
  })

  it('shows the specific message when a selected expense is rejected', async () => {
    fetchReports.mockResolvedValue([])
    createReport.mockRejectedValue(
      new NormalizedApiError('REPORT-007', 400, 'Selected expense is invalid'),
    )
    const { wrapper } = await mountView()

    await findButton(wrapper, 'Choose expenses')?.trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'We could not include one of the selected expenses. Refresh the list and choose again.',
    )
  })

  it('keeps an already linked expense out of the existing-report conflict branch', async () => {
    fetchReports.mockResolvedValue([])
    createReport.mockRejectedValue(
      new NormalizedApiError('REPORT-008', 409, 'Selected expense is already linked to a Journey'),
    )
    const { router, wrapper } = await mountView()

    await findButton(wrapper, 'Choose expenses')?.trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    // REPORT-008도 409지만 기존 Report 충돌(REPORT-005)이 아니다. 목록을 다시 부르거나
    // 상세로 보내지 않고, 이 코드의 문구를 그대로 보여줘야 한다.
    expect(wrapper.get('[role="alert"]').text()).toContain(
      'One of the selected expenses already belongs to another journey. Refresh the list and choose again.',
    )
    expect(wrapper.text()).not.toContain('A final report already exists')
    expect(fetchReports).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.fullPath).toBe('/reports')
  })

  it('renders list error and empty state branches', async () => {
    fetchReportJourneys.mockRejectedValueOnce(new NormalizedApiError('NETWORK', null, 'offline'))
    const failed = await mountView()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain(
      'We could not load your reports. Please try again.',
    )

    fetchReportJourneys.mockReset()
    fetchReportJourneys.mockResolvedValueOnce([])
    fetchReports.mockReset()
    fetchReports.mockResolvedValueOnce([])
    const empty = await mountView()
    expect(empty.wrapper.text()).toContain('No ended journeys yet')
  })

  it('preselects the journey named by a valid ?tripId query param', async () => {
    const { wrapper } = await mountView('/reports?tripId=9')

    expect(wrapper.text()).toContain('Choose report expenses')
    expect(wrapper.text()).toContain('Jeju Island · Select completed expenses')
  })

  it('silently ignores an invalid or unknown ?tripId query param', async () => {
    const missing = await mountView('/reports?tripId=999')
    expect(missing.wrapper.text()).not.toContain('Choose report expenses')

    const malformed = await mountView('/reports?tripId=not-a-number')
    expect(malformed.wrapper.text()).not.toContain('Choose report expenses')
  })

  it('does not preselect an ongoing journey named by ?tripId', async () => {
    const { wrapper } = await mountView('/reports?tripId=42')

    expect(wrapper.text()).not.toContain('Choose report expenses')
  })

  it('sends a ?tripId with an existing report straight to its detail instead of the generate flow', async () => {
    const { router, wrapper } = await mountView('/reports?tripId=7')

    expect(wrapper.text()).not.toContain('Choose report expenses')
    expect(router.currentRoute.value.fullPath).toBe('/reports/100')
  })

  it('recovers the ?tripId auto-selection after retrying a failed initial load', async () => {
    fetchReportJourneys.mockReset()
    fetchReportJourneys.mockRejectedValueOnce(new NormalizedApiError('NETWORK', null, 'offline'))
    fetchReportJourneys.mockResolvedValue(journeys)

    const { wrapper } = await mountView('/reports?tripId=9')

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'We could not load your reports. Please try again.',
    )
    expect(wrapper.text()).not.toContain('Choose report expenses')

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Choose report expenses')
    expect(wrapper.text()).toContain('Jeju Island · Select completed expenses')
  })
})
