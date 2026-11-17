import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import {
  appointmentMemberIntegrationKey,
  type AppointmentMemberStats,
} from '../../model/memberIntegration'

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

interface StatsState {
  data: AppointmentMemberStats | undefined
  isError?: boolean
}

async function mountView(stats: StatsState = { data: { averageRating: null, reviewCount: 0 } }) {
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
            data: ref(stats.data),
            isPending: ref(false),
            isError: ref(stats.isError ?? false),
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

  it('renders the selected participant with one trust line', async () => {
    const { wrapper } = await mountView({ data: { averageRating: 4.666, reviewCount: 6 } })

    expect(wrapper.text()).toContain('Participant profile')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.get('[data-testid="member-trust-line"]').text()).toBe('★ 4.7 · 6 reviews')
  })

  it('uses the singular form for a single review', async () => {
    const { wrapper } = await mountView({ data: { averageRating: 5, reviewCount: 1 } })

    expect(wrapper.get('[data-testid="member-trust-line"]').text()).toBe('★ 5.0 · 1 review')
  })

  // 0건을 0점으로 그리면 평가가 나쁜 사람처럼 보인다.
  it('says there are no reviews yet instead of showing a zero score', async () => {
    const { wrapper } = await mountView({ data: { averageRating: null, reviewCount: 0 } })

    const line = wrapper.get('[data-testid="member-trust-line"]').text()
    expect(line).toBe('No reviews yet')
    expect(line).not.toContain('★')
  })

  // 출석률·노쇼 수는 공개 범위를 정하지 않았다(#173). 본인 조회 응답에는 값이 와도 그리지 않는다.
  it('does not show attendance metrics even when the response has them', async () => {
    const { wrapper } = await mountView({
      data: { completionRate: 83, noShowCount: 2, averageRating: 4.2, reviewCount: 3 },
    })

    const text = wrapper.text()
    expect(text).not.toContain('83')
    expect(text).not.toMatch(/no-show|completion|trust indicators/i)
  })

  // 열람 범위 밖이면 서버가 403을 준다. 점수 줄만 빼고 프로필은 그대로 보인다.
  it('hides the trust line when the score cannot be loaded', async () => {
    const { wrapper } = await mountView({ data: undefined, isError: true })

    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.find('[data-testid="member-trust-line"]').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/unavailable|no ratings yet/i)
  })

  // 후기 보기·신고는 연결할 곳이 없어 비활성으로만 놓여 있었다. 누를 수 없는 버튼은
  // 화면에서 할 수 있는 일을 잘못 알린다.
  it('does not offer actions that go nowhere', async () => {
    const { wrapper } = await mountView()

    const labels = wrapper.findAll('button').map((button) => button.text())
    expect(labels).not.toContain('View reviews')
    expect(labels).not.toContain('Report member')
  })
})
