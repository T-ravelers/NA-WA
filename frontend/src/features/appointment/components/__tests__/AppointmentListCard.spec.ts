import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type { AppointmentSummary } from '../../api/appointmentApi'
import AppointmentListCard from '../AppointmentListCard.vue'

const appointment: AppointmentSummary = {
  appointmentId: 7,
  itemId: 42,
  itemType: 'EVENT',
  appointmentName: 'Seongsu K-Beauty Tour',
  languageCode: 'en',
  maxMembers: 4,
  currentMemberCount: 2,
  depositAmount: '10000',
  appointmentStatus: 'RECRUITING',
  meetingPlace: 'Seongsu Beauty Lab',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  hostDisplayName: 'Mina Park',
}

async function mountCard(overrides: Partial<AppointmentSummary> = {}) {
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
  it('shows press feedback on the clickable card unless reduced motion is requested', async () => {
    const { wrapper } = await mountCard()

    expect(wrapper.get('article').classes()).toEqual(
      expect.arrayContaining([
        'transition-transform',
        'motion-reduce:transition-none',
        'active:scale-[0.98]',
        'motion-reduce:active:scale-100',
      ]),
    )
  })

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

    await wrapper.get('article').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
  })

  // 카드 탭은 마우스·터치 전용 지름길이다. 카드를 링크로 선언하면 스크린 리더가
  // 카드 전체를 링크 하나로 읽어 안쪽 버튼 두 개를 놓친다.
  it('leaves the card out of the tab order so the buttons stay reachable', async () => {
    const { wrapper } = await mountCard()
    const card = wrapper.get('article')

    expect(card.attributes('role')).toBeUndefined()
    expect(card.attributes('tabindex')).toBeUndefined()
  })

  // 카드가 키를 가로채면 Join에 포커스를 두고 누른 Enter가 카드까지 올라가고,
  // preventDefault가 버튼의 click 생성을 취소해 참여 대신 상세로 넘어간다.
  it('does not hijack keys pressed on the buttons inside it', async () => {
    const { wrapper, router } = await mountCard()
    const join = wrapper.findAll('button').find((button) => button.text() === 'Join')

    await join?.trigger('keydown', { key: 'Enter' })
    await join?.trigger('keydown', { key: ' ' })
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('home')
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

  // 정원이 찼거나 이미 시작된 약속은 서버가 참여를 거절한다. 상세와 같은 기준으로
  // 미리 막아, 여정을 고르고 보증금까지 확인한 뒤에야 거절을 보는 일이 없게 한다.
  it('disables Join when the appointment is no longer recruiting', async () => {
    const { wrapper } = await mountCard({ appointmentStatus: 'FULL' })
    const join = wrapper.findAll('button').find((button) => button.text() === 'Join')

    expect(join?.attributes('disabled')).toBeDefined()

    await join?.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('join')).toBeUndefined()
  })
})
