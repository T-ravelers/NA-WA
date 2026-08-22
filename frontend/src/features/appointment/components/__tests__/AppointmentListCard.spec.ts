import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import AppointmentListCard from '../AppointmentListCard.vue'

const appointment = {
  appointmentId: 7,
  itemId: 42,
  itemType: 'EVENT' as const,
  appointmentName: 'Seongsu K-Beauty Tour',
  languageCode: 'en' as const,
  maxMembers: 4,
  currentMemberCount: 2,
  depositAmount: '10000',
  appointmentStatus: 'RECRUITING' as const,
  meetingPlace: 'Seongsu Beauty Lab',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  hostDisplayName: 'Mina Park',
}

async function mountCard(overrides: Partial<typeof appointment> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
    ],
  })
  await router.push('/')
  await router.isReady()

  const wrapper = mount(AppointmentListCard, {
    props: { appointment: { ...appointment, ...overrides } },
    global: { plugins: [i18n, router] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentListCard', () => {
  it('shows the headline numbers without their labels', async () => {
    const { wrapper } = await mountCard()

    // 인원수와 보증금은 카드에서 가장 먼저 읽히는 값이라 숫자만 둔다.
    expect(wrapper.text()).toContain('2/4')
    expect(wrapper.text()).toContain('10,000P')
    expect(wrapper.text()).not.toContain('members')
    expect(wrapper.text()).not.toContain('Deposit')
  })

  it('splits the schedule into a date line and a time line', async () => {
    const { wrapper } = await mountCard()
    const scheduleLines = wrapper.findAll('dd')[0]?.findAll('span')

    expect(scheduleLines?.map((line) => line.text())).toEqual(['8/8', '6:30 PM ~ 10:00 PM'])
  })

  // 날짜를 넘기는 약속은 날짜 줄만으로는 끝나는 날을 알 수 없다.
  it('keeps both dates when the activity runs past midnight', async () => {
    const { wrapper } = await mountCard({ activityEndAt: '2026-08-09T01:00:00' })
    const scheduleLines = wrapper.findAll('dd')[0]?.findAll('span')

    expect(scheduleLines?.[0]?.text()).toBe('8/8 ~ 8/9')
  })

  it('opens the detail when the card itself is pressed', async () => {
    const { wrapper, router } = await mountCard()

    await wrapper.get('[role="link"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
  })

  // Join은 카드를 여는 것이 아니라 참여를 시작한다. 버튼이 카드 클릭까지 함께
  // 일으키면 시트를 열자마자 상세로 넘어가 버린다.
  it('asks the list to start joining without opening the detail', async () => {
    const { wrapper, router } = await mountCard()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('join')?.length).toBe(1)
    expect(router.currentRoute.value.name).toBe('home')
  })
})
