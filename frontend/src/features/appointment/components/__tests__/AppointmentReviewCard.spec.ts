import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentReviewCard from '../AppointmentReviewCard.vue'

const member = {
  appointmentMemberId: 2,
  memberId: 12,
  displayName: 'Alex Kim',
  profileImageUrl: null,
  preferredLanguage: 'en' as const,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'ATTENDED' as const,
  isHost: false,
}

describe('AppointmentReviewCard', () => {
  it('requires all three scores before saving', () => {
    const wrapper = mount(AppointmentReviewCard, {
      props: { member },
      global: { plugins: [i18n] },
    })

    expect(wrapper.find('button').exists()).toBe(true)
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Save review')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('emits three scores and optional keywords', async () => {
    const wrapper = mount(AppointmentReviewCard, {
      props: { member },
      global: { plugins: [i18n] },
    })
    const scoreButtons = wrapper.findAll('button').filter((button) => button.text() === '★')

    await scoreButtons[4]?.trigger('click')
    await scoreButtons[9]?.trigger('click')
    await scoreButtons[14]?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Friendly')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Save review')
      ?.trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      reviewedAppointmentMemberId: 2,
      scores: { PUNCTUALITY: 5, MANNERS: 5, COMMUNICATION: 5 },
      keywordCodes: ['FRIENDLY'],
    })
  })
})
