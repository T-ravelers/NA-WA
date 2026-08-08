import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneySelectSheet from '../JourneySelectSheet.vue'

const journeys = [
  {
    tripId: 12,
    title: 'Seoul Foodie Week',
    startDate: '2026-03-28',
    endDate: '2026-04-01',
  },
]

describe('JourneySelectSheet', () => {
  it('emits the selected journey', async () => {
    const wrapper = mount(JourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie'))
      ?.trigger('click')

    expect(wrapper.emitted('select')).toEqual([[12]])
  })

  it('closes when the scrim is pressed', async () => {
    const wrapper = mount(JourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys },
    })

    await wrapper.get('button[aria-label="Close journey selector"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
