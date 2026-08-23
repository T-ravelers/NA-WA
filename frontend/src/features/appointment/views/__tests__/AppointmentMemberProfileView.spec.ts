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
      /*
       * 화면은 더는 이 주입을 쓰지 않는다(#483). 그래도 남기는 이유는 둘이다 —
       * 되살릴 때 그대로 쓰고, **`reviewCount`가 0이 아니어야 아래 뮤테이션이 잡힌다.**
       * 0으로 두면 후기 수 한 줄을 되돌려도 옛 코드가 `ratingUnavailable` 가지를 타서
       * `reviews`라는 글자가 아예 안 나온다(실측: 뮤테이션 3건 전부 통과).
       */
      provide: {
        [appointmentMemberIntegrationKey as symbol]: {
          useMemberStats: () => ({
            data: ref({
              completionRate: null,
              noShowCount: 0,
              averageRating: null,
              reviewCount: 3,
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

  it('renders the selected participant', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Participant profile')
    expect(wrapper.text()).toContain('Mina Park')
  })

  // 신뢰 지표는 집계가 발표 후 트랙이라 값이 비어 있고, 빈 값을 찍던 자리가
  // 하드코딩 영어라 네 로케일 모두 영어로 보였다(#483). 화면에서 뺐다.
  it('does not show trust indicators while the aggregate is unavailable', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).not.toContain('Trust indicators')
    expect(wrapper.text()).not.toContain('Unavailable')
    expect(wrapper.text()).not.toContain('No ratings yet')
    expect(wrapper.text()).not.toContain('reviews')
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
