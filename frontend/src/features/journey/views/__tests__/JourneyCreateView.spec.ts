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

async function mountView(initialPath = '/journeys/new') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys/new', name: 'journey-create', component: JourneyCreateView },
      {
        path: '/journeys',
        name: 'journey-list',
        component: { template: '<div>Journey list</div>' },
      },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Journey detail</div>' },
      },
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: { template: '<div>Appointment create</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  await router.push(initialPath)
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

  it('invalidates the cached journey list so the new journey shows up elsewhere', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, queryClient } = await mountView()
    // 다른 화면(예: 약속 생성의 여정 선택 시트)이 이미 조회해둔 빈 목록 캐시를 흉내낸다.
    queryClient.setQueryData(['journeys', 'list'], [])

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(queryClient.getQueryState(['journeys', 'list'])?.isInvalidated).toBe(true)
  })

  it('returns to the requesting route with the new tripId when returnRouteName is set', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&itemId=100&itemType=EVENT',
    )

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '100',
      itemType: 'EVENT',
      tripId: '42',
    })
  })

  it('goes back the way you came when there is history', async () => {
    // 목적지를 push하면 이 화면이 히스토리에 남아, 돌아간 약속 생성 화면에서
    // 시트를 닫을 때 목록이 아니라 이 화면으로 다시 튄다. 되감아야 빠진다.
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&itemId=100&itemType=EVENT',
    )
    const back = vi.spyOn(router, 'back').mockImplementation(() => {})
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(back).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('journey-create')

    historyLength.mockRestore()
    back.mockRestore()
  })

  it('falls back to the journey list when opened directly without history', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-list')
  })

  it('falls back to the requesting route, keeping its query, without creating anything', async () => {
    // 히스토리가 없는데 약속 생성이 보낸 query가 있는 경우(딥링크). 되돌아갈 때
    // itemId·itemType은 그대로 들고 가되, 만든 여정이 없으니 tripId는 붙이지 않는다.
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&itemId=100&itemType=EVENT',
    )

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({ itemId: '100', itemType: 'EVENT' })
    expect(createJourney).not.toHaveBeenCalled()
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

  it('falls back to the common error message for an untranslated API code', async () => {
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    createJourney.mockRejectedValue(new NormalizedApiError('COMMON-001', 400, 'invalid input'))
    const { wrapper } = await mountView()

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(wrapper.text()).toContain('Something went wrong. Please try again.')
    expect(wrapper.text()).not.toContain('common.errorCode.COMMON-001')
  })
})
