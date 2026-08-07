import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type { JourneyCreateInput } from '../../api/journeyApi'
import JourneyCreateForm from '../../components/JourneyCreateForm.vue'

const createJourney = vi.fn()

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  createJourney: (input: JourneyCreateInput) => createJourney(input),
}))

const JourneyCreateView = (await import('../JourneyCreateView.vue')).default

const input: JourneyCreateInput = {
  title: 'Seoul Foodie Week',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: null,
  companionPreference: '2-4',
  regions: [],
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys/new', name: 'journey-create', component: JourneyCreateView },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Journey detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  await router.push('/journeys/new')
  await router.isReady()

  const wrapper = mount(JourneyCreateView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })

  return { wrapper, router, queryClient }
}

describe('JourneyCreateView', () => {
  beforeEach(() => {
    createJourney.mockReset()
  })

  it('creates a journey once and moves to the returned detail route', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router, queryClient } = await mountView()

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(createJourney).toHaveBeenCalledTimes(1)
    expect(createJourney).toHaveBeenCalledWith(input)
    expect(router.currentRoute.value.fullPath).toBe('/journeys/42')
    expect(queryClient.getQueryData(['journeys', 'detail', 42])).toMatchObject({ tripId: 42 })
  })

  it('shows the normalized creation error without navigating', async () => {
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    createJourney.mockRejectedValue(new NormalizedApiError('JOURNEY-003', 400, 'invalid journey'))
    const { wrapper, router } = await mountView()

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(wrapper.text()).toContain('Check the journey details and try again.')
    expect(router.currentRoute.value.fullPath).toBe('/journeys/new')
  })
})
