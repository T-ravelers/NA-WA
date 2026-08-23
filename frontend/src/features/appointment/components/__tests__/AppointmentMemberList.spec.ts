import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentMemberList from '../AppointmentMemberList.vue'

const host = {
  appointmentMemberId: 1,
  memberId: 11,
  displayName: 'Mina Park',
  profileImageUrl: null,
  preferredLanguage: 'en' as const,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'PENDING' as const,
  isHost: true,
}

const me = {
  appointmentMemberId: 2,
  memberId: 12,
  displayName: 'Alex Kim',
  profileImageUrl: null,
  preferredLanguage: 'ja' as const,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'PENDING' as const,
  isHost: false,
}

describe('AppointmentMemberList', () => {
  it('renders host and member language labels', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: {
        members: [
          {
            appointmentMemberId: 1,
            memberId: 11,
            displayName: 'Mina Park',
            profileImageUrl: null,
            preferredLanguage: 'en',
            membershipStatus: 'ACTIVE',
            attendanceStatus: 'PENDING',
            isHost: true,
          },
        ],
      },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).toContain('English')
    expect(wrapper.text()).not.toContain('Not attended')
    expect(wrapper.text()).toContain('Visit')
  })

  it('marks only the logged-in member with a You badge', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: {
        members: [
          {
            appointmentMemberId: 1,
            memberId: 11,
            displayName: 'Mina Park',
            profileImageUrl: null,
            preferredLanguage: 'en',
            membershipStatus: 'ACTIVE',
            attendanceStatus: 'PENDING',
            isHost: true,
          },
          {
            appointmentMemberId: 2,
            memberId: 12,
            displayName: 'Alex Kim',
            profileImageUrl: null,
            preferredLanguage: 'ja',
            membershipStatus: 'ACTIVE',
            attendanceStatus: 'PENDING',
            isHost: false,
          },
        ],
        currentAppointmentMemberId: 2,
      },
      global: { plugins: [i18n] },
    })

    const cards = wrapper.findAll('li')
    expect(cards[0]?.text()).not.toContain('You')
    expect(cards[1]?.text()).toContain('You')
  })

  it('replaces Visit with a leave button on my row only', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: {
        members: [host, me],
        currentAppointmentMemberId: 2,
      },
      global: { plugins: [i18n] },
    })

    const rows = wrapper.findAll('li')
    expect(rows[0]?.text()).toContain('Visit')
    expect(rows[0]?.text()).not.toContain('Leave')
    expect(rows[1]?.text()).toContain('Leave')
    expect(rows[1]?.text()).not.toContain('Visit')
  })

  // 나가기 버튼은 지금 나갈 수 있는지와 무관하게 언제나 눌린다. 막히는 이유는
  // 누른 뒤 화면이 말한다 — 비활성 버튼은 모바일에서 이유를 말할 자리가 없다.
  it('emits leave instead of select whenever the leave button is pressed', async () => {
    const wrapper = mount(AppointmentMemberList, {
      props: { members: [me], currentAppointmentMemberId: 2 },
      global: { plugins: [i18n] },
    })

    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeUndefined()

    await button.trigger('click')

    expect(wrapper.emitted('leave')).toHaveLength(1)
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  // 폭을 고정하지 않으면 라벨 길이를 따라가 로케일마다 두 버튼 크기가 갈린다.
  it('draws the leave and visit buttons at the same size', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: { members: [host, me], currentAppointmentMemberId: 2 },
      global: { plugins: [i18n] },
    })

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(
      buttons.every(
        (button) => button.classes().includes('w-24') && button.classes().includes('h-11'),
      ),
    ).toBe(true)
  })

  // 목록에서 나를 못 찾으면(참여 조회 실패로 id가 없으면) 붙일 행이 없다.
  it('keeps every row on Visit when the current member is unknown', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: { members: [host, me] },
      global: { plugins: [i18n] },
    })

    expect(wrapper.findAll('button').every((button) => button.text() === 'Visit')).toBe(true)
  })

  // 방장은 자기 참여를 취소할 수 없고(APPOINTMENT-007) 자기 프로필을 방문할 일도
  // 없어, 자기 행에는 놓을 버튼이 없다.
  it('leaves the host their own row without any action', () => {
    const wrapper = mount(AppointmentMemberList, {
      props: { members: [host, me], currentAppointmentMemberId: 1 },
      global: { plugins: [i18n] },
    })

    const rows = wrapper.findAll('li')
    expect(rows[0]?.findAll('button')).toHaveLength(0)
    expect(rows[1]?.text()).toContain('Visit')
  })

  it('emits the selected member when a member card is pressed', async () => {
    const member = {
      appointmentMemberId: 1,
      memberId: 11,
      displayName: 'Mina Park',
      profileImageUrl: null,
      preferredLanguage: 'en' as const,
      membershipStatus: 'ACTIVE' as const,
      attendanceStatus: 'PENDING' as const,
      isHost: true,
    }
    const wrapper = mount(AppointmentMemberList, {
      props: { members: [member] },
      global: { plugins: [i18n] },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([member])
  })
})
