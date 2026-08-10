import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

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
  },
  {
    tripId: 7,
    title: 'Busan Weekender',
    startDate: '2020-08-10',
    endDate: '2020-08-12',
  },
]

let queryClient: QueryClient
const queryClients: QueryClient[] = []
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
    },
  })

  await flushPromises()
  mountedWrappers.push(wrapper)

  return { wrapper, router }
}

describe('JourneyListView', () => {
  beforeEach(() => {
    fetchJourneys.mockReset()
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
    expect(wrapper.text()).toContain('2098.08.10')
    expect(wrapper.text()).not.toContain('Busan Weekender')
    expect(wrapper.find('ul[aria-live]').exists()).toBe(false)

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    expect(wrapper.text()).toContain('Busan Weekender')
    expect(wrapper.text()).toContain('2020.08.12')
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
})
