import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import { appointmentExploreIntegrationKey } from '@/features/appointment/model/exploreIntegration'
import { appointmentJourneyIntegrationKey } from '../../model/journeyIntegration'

const createAppointment = vi.fn()
const checkJourneyItemExists = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  createAppointment: (request: unknown) => createAppointment(request),
}))

const AppointmentCreateView = (await import('../AppointmentCreateView.vue')).default

const journeys = [
  { tripId: 7, title: 'Seoul Foodie Week', startDate: '2026-08-01', endDate: '2026-08-31' },
]

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function mountView(query = '?itemId=42&itemType=EVENT') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: AppointmentCreateView,
      },
      {
        path: '/journeys/new',
        name: 'journey-create',
        component: { template: '<div>Journey create</div>' },
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>List</div>' },
      },
      {
        path: '/wallet/top-up',
        name: 'wallet-top-up',
        component: { template: '<div>Top up</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push(`/appointments/new${query}`)
  await router.isReady()

  const wrapper = mount(AppointmentCreateView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [appointmentJourneyIntegrationKey as symbol]: {
          useJourneyListQuery: () => ({
            data: ref(journeys),
            isPending: ref(false),
            isError: ref(false),
          }),
          checkJourneyItemExists,
        },
        [appointmentExploreIntegrationKey as symbol]: {
          useItemLocation: () => ({
            data: ref({ placeName: 'DDP Design Plaza', addressRoad: '281 Eulji-ro, Jung-gu' }),
            isLoading: ref(false),
            isError: ref(false),
          }),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function completeJourneySelection(wrapper: ReturnType<typeof mount>): Promise<void> {
  await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
  await flushPromises()
  // 실제 시각과 무관하게 항상 미래인 날짜를 골라, 활동 시작 시각의 "과거 금지" 검증이
  // 테스트 실행 시각에 따라 흔들리지 않게 한다.
  await wrapper.get('button[aria-label="Select August 31, 2026"]').trigger('click')
  await buttonByText(wrapper, 'Continue with').trigger('click')
  await flushPromises()
}

async function fillAndConfirm(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
    .setValue('Seongsu K-Beauty Tour')
  await wrapper.get('form').trigger('submit')

  await wrapper.find('input[type="time"]').setValue('18:30')
  await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')
  await wrapper.find('input[inputmode="numeric"]').setValue('10000')
  await wrapper.get('form').trigger('submit')
  await buttonByText(wrapper, 'Confirm').trigger('click')
}

describe('AppointmentCreateView', () => {
  beforeEach(() => {
    createAppointment.mockReset()
    checkJourneyItemExists.mockReset()
    checkJourneyItemExists.mockResolvedValue(false)
    sessionStorage.clear()
  })

  it('opens the journey select sheet on entry and hides the form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Choose a journey')
    expect(wrapper.text()).toContain('Seoul Foodie Week')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('moves to the date sheet after selecting a journey, then to the form after choosing a date', async () => {
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    expect(wrapper.text()).toContain('Which day?')

    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(checkJourneyItemExists).toHaveBeenCalledWith(7, 42, expect.any(String))
    expect(wrapper.text()).toContain('Start with your appointment details')
  })

  it('shows an error and keeps the date sheet open when the combination already exists', async () => {
    checkJourneyItemExists.mockResolvedValueOnce(true)
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(
      'This activity is already linked to an appointment on this day.',
    )
    expect(wrapper.text()).toContain('Which day?')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('names the broken entry instead of blaming the date check when the item type is missing', async () => {
    // itemType이 없으면 폼에 들어가도 항목 위치를 읽을 수 없다. 여기서 막지 않으면
    // 사용자는 원인을 알 수 없는 화면에 갇힌다.
    const { wrapper } = await mountView('?itemId=42')

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Open this form from an Event or Place.')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(checkJourneyItemExists).not.toHaveBeenCalled()
  })

  it('returns to the journey select sheet when the date sheet is closed', async () => {
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    expect(wrapper.text()).toContain('Which day?')

    await wrapper.get('button[aria-label="Go back"]').trigger('click')

    expect(wrapper.text()).toContain('Choose a journey')
  })

  it('leaves the flow immediately when the journey select sheet is closed', async () => {
    // 여정 선택 단계에는 아직 적은 것이 없다. 시트 바깥을 누른 것만으로
    // "잃어버린다"는 확인을 띄우지 않고 바로 떠난다.
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Close journey selector"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Leave without creating?')
    expect(router.currentRoute.value.name).toBe('appointment-list')
  })

  it('navigates to journey creation with a return route when there are no journeys', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/appointments/new', name: 'appointment-create', component: AppointmentCreateView },
        { path: '/journeys/new', name: 'journey-create', component: { template: '<div />' } },
      ],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    await router.push('/appointments/new?itemId=42&itemType=EVENT')
    await router.isReady()

    const wrapper = mount(AppointmentCreateView, {
      global: {
        plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
        provide: {
          [appointmentJourneyIntegrationKey as symbol]: {
            useJourneyListQuery: () => ({
              data: ref([]),
              isPending: ref(false),
              isError: ref(false),
            }),
            checkJourneyItemExists,
          },
          [appointmentExploreIntegrationKey as symbol]: {
            useItemLocation: () => ({
              data: ref(undefined),
              isLoading: ref(false),
              isError: ref(false),
            }),
          },
        },
      },
    })
    await flushPromises()

    await buttonByText(wrapper, 'Create a journey').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-create')
    expect(router.currentRoute.value.query).toMatchObject({
      returnRouteName: 'appointment-create',
      itemId: '42',
      itemType: 'EVENT',
    })
  })

  it('moves to the previous form step, then opens an exit confirmation on the first step', async () => {
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

    await wrapper
      .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
      .setValue('Seongsu K-Beauty Tour')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Set your appointment details')

    await wrapper.find('header button').trigger('click')
    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(router.currentRoute.value.name).toBe('appointment-create')

    await wrapper.find('header button').trigger('click')
    expect(wrapper.text()).toContain('Leave without creating?')
  })

  it('creates the appointment and navigates to its detail page on success', async () => {
    createAppointment.mockResolvedValueOnce({
      appointmentId: 42,
      itemId: 42,
      itemType: 'EVENT',
      appointmentName: 'Seongsu K-Beauty Tour',
      languageCode: 'en',
      maxMembers: 4,
      currentMemberCount: 1,
      depositAmount: '10000',
      appointmentStatus: 'RECRUITING',
      meetingPlace: 'DDP Design Plaza',
      activityStartAt: '2026-08-08T18:30:00',
      activityEndAt: '2026-08-08T22:00:00',
      hostDisplayName: 'Mina Park',
      description: null,
      members: [],
    })
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)
    const replace = vi.spyOn(router, 'replace')

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(createAppointment).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('42')
    // 상세의 뒤로 가기가 왔던 길을 되감으므로 폼은 히스토리에 남으면 안 된다.
    expect(replace).toHaveBeenCalledOnce()

    replace.mockRestore()
  })

  it('shows a generic error message and stays on the form when creation fails', async () => {
    createAppointment.mockRejectedValueOnce(new Error('network error'))
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Appointment could not be created')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })

  it('offers to top up the deposit amount when the balance is too low', async () => {
    // 빨간 한 줄 대신 "부족하다 + 그만큼 충전할까"를 한 번에 묻는다. 확인하면 충전
    // 화면으로 가되, 보증금만큼 미리 채워지도록 금액을 query로 넘긴다.
    createAppointment.mockRejectedValueOnce(
      new NormalizedApiError('WALLET-015', 409, '지갑 잔액이 부족합니다.'),
    )
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Not enough balance')
    expect(wrapper.text()).toContain('too low for the 10,000 P deposit')
    expect(wrapper.text()).not.toContain('Your wallet balance is too low for this transfer.')
    expect(router.currentRoute.value.name).toBe('appointment-create')

    await buttonByText(wrapper, 'Top up').trigger('click')
    await flushPromises()

    // 금액과 함께 "어디서 왔는지"와 이 화면의 query를 넘겨, 충전이 끝나면 같은 항목·
    // 여정의 약속 생성으로 돌아올 수 있게 한다.
    expect(router.currentRoute.value.name).toBe('wallet-top-up')
    expect(router.currentRoute.value.query).toEqual({
      amount: '10000',
      returnRouteName: 'appointment-create',
      itemId: '42',
      itemType: 'EVENT',
      tripId: '7',
    })
    // 떠나기 전 폼 초안을 같은 탭에 남겨, 돌아왔을 때 다시 적지 않게 한다.
    const saved = JSON.parse(sessionStorage.getItem('appointment-create:resume') ?? 'null')
    expect(saved).toMatchObject({
      itemId: 42,
      itemType: 'EVENT',
      tripId: 7,
      visitDate: '2026-08-31',
      form: { step: 2, draft: { appointmentName: 'Seongsu K-Beauty Tour', depositAmount: 10000 } },
    })
  })

  it('restores the saved form when the top-up screen sends the host back', async () => {
    sessionStorage.setItem(
      'appointment-create:resume',
      JSON.stringify({
        itemId: 42,
        itemType: 'EVENT',
        tripId: 7,
        visitDate: '2026-08-31',
        form: {
          step: 2,
          customMeetingPlace: '',
          draft: {
            itemId: 42,
            itemType: 'EVENT',
            tripId: 7,
            visitDate: '2026-08-31',
            appointmentName: 'Seongsu K-Beauty Tour',
            maxMembers: 4,
            languageCode: 'en',
            depositAmount: 10000,
            meetingPlaceMode: 'ITEM',
            meetingPlace: 'DDP Design Plaza',
            activityStartTime: '18:30',
            activityEndTime: '22:00',
          },
        },
      }),
    )
    const { wrapper } = await mountView('?itemId=42&itemType=EVENT&tripId=7&resume=1')

    // 여정·날짜 시트를 다시 거치지 않고 2단계 폼이 바로, 적었던 값 그대로 열린다.
    expect(wrapper.text()).not.toContain('Choose a journey')
    expect(wrapper.text()).toContain('Set your appointment details')
    expect(wrapper.find<HTMLInputElement>('input[type="time"]').element.value).toBe('18:30')
    expect(wrapper.find<HTMLInputElement>('input[inputmode="numeric"]').element.value).toBe(
      '10,000',
    )
    // 한 번 되살렸으면 지운다 — 남겨두면 다음에 같은 항목으로 들어올 때 옛 초안이 뜬다.
    expect(sessionStorage.getItem('appointment-create:resume')).toBeNull()
  })

  it('ignores a saved form that belongs to a different item', async () => {
    sessionStorage.setItem(
      'appointment-create:resume',
      JSON.stringify({
        itemId: 999,
        itemType: 'PLACE',
        tripId: 7,
        visitDate: '2026-08-31',
        form: {},
      }),
    )
    const { wrapper } = await mountView('?itemId=42&itemType=EVENT&resume=1')

    expect(wrapper.text()).toContain('Choose a journey')
  })

  it('closes the top-up prompt without leaving a stale error behind', async () => {
    createAppointment.mockRejectedValueOnce(
      new NormalizedApiError('WALLET-015', 409, '지갑 잔액이 부족합니다.'),
    )
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()
    await buttonByText(wrapper, 'Not now').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Not enough balance')
    expect(wrapper.text()).not.toContain('Your wallet balance is too low for this transfer.')
    expect(wrapper.text()).not.toContain('Appointment could not be created')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })

  it('reopens the date sheet with the form preserved when the journey item was already confirmed', async () => {
    createAppointment.mockRejectedValueOnce(
      new NormalizedApiError('JOURNEY-004', 409, 'duplicate journey item'),
    )
    const { wrapper } = await mountView()
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()

    // 날짜 시트가 폼 위에 다시 뜨고, 폼은 여전히(같은 2단계에) 입력값을 유지한 채 있다.
    expect(wrapper.text()).toContain('Which day?')
    expect(wrapper.text()).toContain('Set your appointment details')
    expect(wrapper.find<HTMLInputElement>('input[type="time"]').element.value).toBe('18:30')

    checkJourneyItemExists.mockResolvedValueOnce(false)
    await wrapper.get('button[aria-label="Select August 30, 2026"]').trigger('click')
    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Set your appointment details')
    expect(wrapper.find<HTMLInputElement>('input[type="time"]').element.value).toBe('18:30')
  })
})
