import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const submitAppointmentReview = vi.fn()
const fetchMyAppointmentReviewStatus = vi.fn()
const fetchMyAppointmentParticipation = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  submitAppointmentReview: (appointmentId: number, request: unknown) =>
    submitAppointmentReview(appointmentId, request),
  fetchMyAppointmentReviewStatus: (appointmentId: number) =>
    fetchMyAppointmentReviewStatus(appointmentId),
  fetchMyAppointmentParticipation: (appointmentId: number) =>
    fetchMyAppointmentParticipation(appointmentId),
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

/** 방장(Mina Park, appointmentMemberId 1)으로 로그인한 상태. */
const attendedParticipation = {
  joined: true,
  appointmentMemberId: 1,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'ATTENDED' as const,
  host: true,
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
    fetchMyAppointmentReviewStatus.mockReset()
    fetchMyAppointmentReviewStatus.mockResolvedValue({ reviewedAppointmentMemberIds: [] })
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue(members)
    submitAppointmentReview.mockRejectedValue(new Error('save failed'))
    fetchMyAppointmentParticipation.mockReset()
    fetchMyAppointmentParticipation.mockResolvedValue(attendedParticipation)
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

    expect(wrapper.get('[role="alert"]').text()).toContain('Something went wrong')
    expect(wrapper.text()).toContain('Alex Kim')
  })

  it('blocks reviews for members who are not active participants', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue({
      joined: false,
      appointmentMemberId: null,
      membershipStatus: null,
      attendanceStatus: null,
      host: false,
    })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Participant access required')
    expect(wrapper.text()).not.toContain('Save review')
  })

  it('blocks reviews until the appointment is completed', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'RECRUITING' })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Reviews are not available yet')
    expect(wrapper.text()).not.toContain('Save review')
  })

  it('restores what was already written from the server', async () => {
    // 이게 없으면 재진입할 때마다 전원이 미작성으로 보이고, 다시 내면 REVIEW-002가 난다.
    fetchMyAppointmentReviewStatus.mockResolvedValue({ reviewedAppointmentMemberIds: [2] })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Completed')
    expect(wrapper.text()).not.toContain('Pending')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Finish reviews')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('tells the user a duplicate review apart from a generic failure', async () => {
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    submitAppointmentReview.mockRejectedValue(
      new NormalizedApiError('REVIEW-002', 409, 'duplicate'),
    )
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

    expect(wrapper.get('[role="alert"]').text()).toContain('already reviewed this member')
  })

  it('does not offer reviews for members marked as no-show', async () => {
    fetchAppointmentMembers.mockResolvedValueOnce([
      members[0],
      { ...members[1], attendanceStatus: 'NO_SHOW' as const },
    ])
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('No reviews to write')
    expect(wrapper.text()).not.toContain('Alex Kim')
  })
})
