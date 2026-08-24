import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import JourneyDateRangePicker from '../../components/JourneyDateRangePicker.vue'

const { fetchJourney, fetchJourneyTimeline, updateJourney, deleteJourney } = vi.hoisted(() => ({
  fetchJourney: vi.fn(),
  fetchJourneyTimeline: vi.fn(),
  updateJourney: vi.fn(),
  deleteJourney: vi.fn(),
}))

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  fetchJourney,
  fetchJourneyTimeline,
  updateJourney,
  deleteJourney,
}))

const JourneySettingsView = (await import('../JourneySettingsView.vue')).default

const journey = {
  tripId: 7,
  title: 'Seoul and Busan',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: 1_500_000,
  companionPreference: '2-4',
  regions: [{ regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 }],
}

const timeline = {
  tripId: 7,
  timeline: [
    {
      visitDate: '2026-08-10',
      items: [
        {
          tripItemId: 31,
          itemId: 91,
          status: 'ADDED',
          displayOrder: 0,
          note: null,
          exploreItem: {
            itemType: 'EVENT',
            title: 'Nanta Theatre',
            thumbnailUrl: null,
            imageUrls: [],
            location: {
              region1: 'Seoul',
              region2: null,
              region3: null,
              addressRoad: null,
              addressDetail: null,
              latitude: null,
              longitude: null,
            },
          },
        },
      ],
    },
  ],
}

async function mountView() {
  fetchJourney.mockResolvedValue(journey)
  fetchJourneyTimeline.mockResolvedValue(timeline)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys', name: 'journey-list', component: { template: '<div>Journeys</div>' } },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Journey</div>' },
      },
      {
        path: '/journeys/:tripId/settings',
        name: 'journey-settings',
        component: JourneySettingsView,
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/journeys/7/settings')
  await router.isReady()
  const wrapper = mount(JourneySettingsView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('JourneySettingsView', () => {
  beforeEach(() => {
    fetchJourney.mockReset()
    fetchJourneyTimeline.mockReset()
    updateJourney.mockReset()
    deleteJourney.mockReset()
  })

  it('separates basics and preferences, hides regions, and preserves them on save', async () => {
    updateJourney.mockResolvedValue({ ...journey, title: 'Summer route' })
    const { wrapper, router } = await mountView()

    expect(wrapper.text()).toContain('Journey basics')
    expect(wrapper.text()).toContain('Preferences')
    expect(wrapper.text()).not.toContain('Regions')

    await wrapper.get('input[type="text"]').setValue('Summer route')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updateJourney).toHaveBeenCalledWith(7, {
      title: 'Summer route',
      startDate: '2026-08-10',
      endDate: '2026-08-12',
      budgetAmount: 1_500_000,
      companionPreference: '2-4',
      regions: [{ regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 }],
    })
    expect(router.currentRoute.value.name).toBe('journey-detail')
  })

  it('warns and disables save when new dates exclude an itinerary item', async () => {
    const { wrapper } = await mountView()
    wrapper.getComponent(JourneyDateRangePicker).vm.$emit('update:startDate', '2026-08-11')
    await wrapper.vm.$nextTick()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'Some itinerary items fall outside the new dates',
    )
    const save = wrapper.findAll('button').find((button) => button.text() === 'Save changes')
    expect(save?.attributes('disabled')).toBeDefined()
  })

  it('turns the server host conflict into the blocked delete dialog', async () => {
    deleteJourney.mockRejectedValue(new NormalizedApiError('JOURNEY-011', 409, 'host conflict'))
    const { wrapper } = await mountView()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Delete journey')
      ?.trigger('click')
    await wrapper.get('#delete-journey-dialog button.bg-danger').trigger('click')
    await flushPromises()

    expect(deleteJourney).toHaveBeenCalledWith(7)
    expect(wrapper.get('#blocked-journey-dialog').text()).toContain('Journey can’t be deleted')
  })
})
