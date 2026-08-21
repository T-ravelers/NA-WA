import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type { JourneyCreateInput } from '../../api/journeyApi'
import JourneyCreateForm from '../../components/JourneyCreateForm.vue'
import { journeyExploreIntegrationKey } from '../../model/exploreIntegration'

const createJourney = vi.fn()
const consumeReturn = vi.fn()

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
      {
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: { template: '<div>Event detail</div>' },
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
      provide: {
        [journeyExploreIntegrationKey as symbol]: { consumeReturn },
      },
    },
  })

  return { wrapper, router, queryClient }
}

describe('JourneyCreateView', () => {
  beforeEach(() => {
    createJourney.mockReset()
    consumeReturn.mockReset()
    consumeReturn.mockReturnValue(null)
  })

  it('creates a journey once and moves to the returned detail route', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router, queryClient } = await mountView()
    const replace = vi.spyOn(router, 'replace')
    const push = vi.spyOn(router, 'push')

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(createJourney).toHaveBeenCalledTimes(1)
    expect(createJourney).toHaveBeenCalledWith(input)
    expect(router.currentRoute.value.fullPath).toBe('/journeys/42')
    expect(queryClient.getQueryData(['journeys', 'detail', 42])).toMatchObject({ tripId: 42 })
    // 제출이 끝난 폼은 히스토리에 남기지 않는다. push하면 상세에서 뒤로 갈 때 방금
    // 제출한 폼이 다시 뜬다.
    expect(replace).toHaveBeenCalledOnce()
    expect(push).not.toHaveBeenCalled()

    replace.mockRestore()
    push.mockRestore()
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
    const replace = vi.spyOn(router, 'replace')
    const push = vi.spyOn(router, 'push')

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '100',
      itemType: 'EVENT',
      tripId: '42',
    })
    // 제출이 끝난 이 화면은 히스토리에 남기지 않는다. push로 돌아가면 돌아간
    // 화면에서 흐름을 포기할 때 되감기가 이미 제출한 폼으로 다시 튄다.
    expect(replace).toHaveBeenCalledOnce()
    expect(push).not.toHaveBeenCalled()

    replace.mockRestore()
    push.mockRestore()
  })

  it('goes back the way you came when entered directly with history', async () => {
    // 여정 목록에서 곧장 들어온 경우. 목적지를 push하면 이 화면이 히스토리에 남아,
    // 돌아간 화면에서 뒤로 갈 때 이미 제출한 이 폼이 다시 튄다. 되감아야 빠진다.
    const { wrapper, router } = await mountView()
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

  it('gives the entry back to the caller instead of rewinding past the flow', async () => {
    // 약속 생성은 자기 자리를 이 화면에 내주고 보냈다(replace로 진입). 그래서 되감으면
    // 흐름 이전 화면까지 빠져 버린다 — 자리를 돌려주는 replace로 나간다. 되감을 히스토리가
    // 있어도 마찬가지다. itemId·itemType은 그대로 들고 가되, 만든 여정이 없으니 tripId는
    // 붙이지 않는다.
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&itemId=100&itemType=EVENT',
    )
    const back = vi.spyOn(router, 'back')
    const push = vi.spyOn(router, 'push')
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({ itemId: '100', itemType: 'EVENT' })
    expect(back).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(createJourney).not.toHaveBeenCalled()

    historyLength.mockRestore()
    push.mockRestore()
    back.mockRestore()
  })

  /*
   * Discover 상세는 route param(:eventId)을 쓴다. query만 나르는 returnRouteName으로는
   * 돌아갈 수 없어 explore가 sessionStorage에 심어 둔 복귀 위치를 소비한다.
   */
  it('Discover에서 온 생성은 params와 새 여정 id를 싣고 상세로 돌아간다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    consumeReturn.mockReturnValue({
      name: 'explore-event-detail',
      params: { eventId: '301' },
    })
    const { wrapper, router } = await mountView('/journeys/new?returnToExplore=1')

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(consumeReturn).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('explore-event-detail')
    expect(router.currentRoute.value.params).toEqual({ eventId: '301' })
    expect(router.currentRoute.value.query).toEqual({
      journeyId: '42',
      openJourneySelect: '1',
    })
  })

  it('복귀 위치가 사라졌으면 여정 상세로 떨어진다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    consumeReturn.mockReturnValue(null)
    const { wrapper, router } = await mountView('/journeys/new?returnToExplore=1')

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/journeys/42')
  })

  /*
   * 표시 없이 소비하면, 여정을 만들다 뒤로 나간 뒤 남은 맥락이 여정 목록에서 들어온
   * 다음 생성까지 Discover 상세로 납치한다.
   */
  it('표시가 없는 생성은 복귀 맥락을 건드리지 않는다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    consumeReturn.mockReturnValue({
      name: 'explore-event-detail',
      params: { eventId: '301' },
    })
    const { wrapper, router } = await mountView()

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(consumeReturn).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/journeys/42')
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
