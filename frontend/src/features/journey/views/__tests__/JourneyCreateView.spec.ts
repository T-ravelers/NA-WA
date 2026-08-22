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
   * Discover 상세는 route param(:eventId)을 쓴다. query만으로는 주소를 만들 수 없어
   * 규약의 `returnParams`로 param을 함께 받는다. 나머지 query(`openJourneySelect`)는
   * 그대로 돌려주고 결과 key만 더한다.
   */
  it('Discover에서 온 생성은 returnParams로 params를 복원해 상세로 돌아간다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=explore-event-detail&returnParams=eventId%3A301&openJourneySelect=1',
    )

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('explore-event-detail')
    expect(router.currentRoute.value.params).toEqual({ eventId: '301' })
    // 규약 key는 돌려주지 않는다. 호출자 화면의 것이 아니다.
    expect(router.currentRoute.value.query).toEqual({
      openJourneySelect: '1',
      tripId: '42',
    })
  })

  /*
   * 제출이 끝난 화면은 자리를 내준다. push였다면 여기서 뒤로 갈 때 이미 제출한 폼이
   * 다시 떠서, 한 번 더 제출하면 여정이 하나 더 생긴다.
   */
  it('Discover 복귀도 자리를 돌려주므로 뒤로 가도 이 폼으로 오지 않는다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=explore-event-detail&returnParams=eventId%3A301',
    )

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).not.toBe('journey-create')
  })

  it('returnParams가 없는 호출자는 params 없이 그대로 돌아간다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&itemId=9&itemType=EVENT',
    )

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '9',
      itemType: 'EVENT',
      tripId: '42',
    })
  })

  it('쓸 수 없는 returnParams는 무시하고 주소만 만든다', async () => {
    createJourney.mockResolvedValue({ ...input, tripId: 42 })
    const { wrapper, router } = await mountView(
      '/journeys/new?returnRouteName=appointment-create&returnParams=%3A42%2Cnope',
    )

    wrapper.findComponent(JourneyCreateForm).vm.$emit('submit', input)
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
  })

  /*
   * 보낸 화면이 실어 준 항목 기간이 폼 기본값이 된다. 없으면 그 사람은 무엇과 겹쳐야
   * 하는지 모른 채 폼을 채우고, 또 안 겹치는 여정을 만들어 같은 자리로 돌아온다.
   */
  it('보낸 화면이 실어 준 항목 기간이 폼 기본값이 된다', async () => {
    const { wrapper } = await mountView(
      '/journeys/new?returnRouteName=explore-event-detail&itemStartDate=2026-08-10&itemEndDate=2026-08-12',
    )

    const form = wrapper.findComponent(JourneyCreateForm)

    expect(form.props('initialStartDate')).toBe('2026-08-10')
    expect(form.props('initialEndDate')).toBe('2026-08-12')
  })

  it('형식이 어긋난 기간은 무시하고 빈 폼으로 둔다', async () => {
    const { wrapper } = await mountView('/journeys/new?itemStartDate=2026-8-1&itemEndDate=nope')

    const form = wrapper.findComponent(JourneyCreateForm)

    // prop 기본값이 빈 문자열이라 폼이 빈 채로 열린다.
    expect(form.props('initialStartDate')).toBe('')
    expect(form.props('initialEndDate')).toBe('')
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
