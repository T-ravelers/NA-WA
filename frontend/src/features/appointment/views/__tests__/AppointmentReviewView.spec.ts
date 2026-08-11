import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const submitAppointmentReview = vi.fn()
const useAppointmentMemberProfileMock = vi.hoisted(() => vi.fn())

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  submitAppointmentReview: (appointmentId: number, request: unknown) =>
    submitAppointmentReview(appointmentId, request),
}))

vi.mock('../../model/memberIntegration', () => ({
  useAppointmentMemberProfile: () => useAppointmentMemberProfileMock(),
}))

const AppointmentReviewView = (await import('../AppointmentReviewView.vue')).default

const appointment = {
  appointmentId: 7,
  itemId: 42,
  itemType: 'EVENT' as const,
  appointmentName: 'Seongsu K-Beauty Tour',
  languageCode: 'en' as const,
  maxMembers: 4,
  currentMemberCount: 2,
  depositAmount: '10000',
  appointmentStatus: 'COMPLETED' as const,
  meetingPlace: 'Seongsu Beauty Lab',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  joinDeadline: '2026-08-08T17:30:00',
  hostDisplayName: 'Mina Park',
  meetingAddress: null,
  description: null,
  members: [],
}

const members = [
  {
    appointmentMemberId: 1,
    memberId: 11,
    displayName: 'Mina Park',
    profileImageUrl: null,
    preferredLanguage: 'en' as const,
    membershipStatus: 'ACTIVE' as const,
    attendanceStatus: 'ATTENDED' as const,
    isHost: true,
  },
  {
    appointmentMemberId: 2,
    memberId: 12,
    displayName: 'Alex Kim',
    profileImageUrl: null,
    preferredLanguage: 'ja' as const,
    membershipStatus: 'ACTIVE' as const,
    attendanceStatus: 'ATTENDED' as const,
    isHost: false,
  },
]

const profileQuery = {
  data: computed(() => ({ memberId: 11 })),
  isPending: ref(false),
  isError: ref(false),
  refetch: vi.fn().mockResolvedValue(undefined),
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/:appointmentId/reviews',
        name: 'appointment-reviews',
        component: AppointmentReviewView,
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/7/reviews')
  await router.isReady()

  const wrapper = mount(AppointmentReviewView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  await flushPromises()
  return { wrapper }
}

describe('AppointmentReviewView', () => {
  beforeEach(() => {
    fetchAppointment.mockReset()
    fetchAppointmentMembers.mockReset()
    submitAppointmentReview.mockReset()
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue(members)
    submitAppointmentReview.mockRejectedValue(new Error('save failed'))
    useAppointmentMemberProfileMock.mockReset()
    useAppointmentMemberProfileMock.mockReturnValue(profileQuery)
  })

  it('shows a save error for the member whose review failed', async () => {
    const { wrapper } = await mountView()
    const scoreButtons = wrapper.findAll('button').filter((button) => button.text() === '★')

    await scoreButtons[4]?.trigger('click')
    await scoreButtons[9]?.trigger('click')
    await scoreButtons[14]?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Save review')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Review could not be saved')
    expect(wrapper.text()).toContain('Alex Kim')
  })
})
