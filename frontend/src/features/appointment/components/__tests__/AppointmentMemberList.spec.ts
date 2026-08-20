import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentMemberList from '../AppointmentMemberList.vue'

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
