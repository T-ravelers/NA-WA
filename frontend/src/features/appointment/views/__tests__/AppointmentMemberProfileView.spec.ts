import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { appointmentMemberIntegrationKey } from '../../model/memberIntegration'

const fetchAppointmentMembers = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
}))

const AppointmentMemberProfileView = (await import('../AppointmentMemberProfileView.vue')).default

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
]

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/:appointmentId/members/:memberId',
        name: 'appointment-member-profile',
        component: AppointmentMemberProfileView,
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/7/members/11')
  await router.isReady()

  const wrapper = mount(AppointmentMemberProfileView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [appointmentMemberIntegrationKey as symbol]: {
          useMemberStats: () => ({
            data: ref({
              completionRate: null,
              noShowCount: 0,
              averageRating: null,
              reviewCount: 0,
            }),
            isPending: ref(false),
            isError: ref(false),
          }),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentMemberProfileView', () => {
  beforeEach(() => {
    fetchAppointmentMembers.mockReset()
    fetchAppointmentMembers.mockResolvedValue(members)
  })

  it('renders the selected participant and unavailable trust actions', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Participant profile')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Trust indicators')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'View reviews')
        ?.attributes('disabled'),
    ).toBeDefined()
  })
})
