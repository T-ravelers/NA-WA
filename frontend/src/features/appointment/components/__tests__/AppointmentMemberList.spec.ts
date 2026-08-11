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
  })
})
