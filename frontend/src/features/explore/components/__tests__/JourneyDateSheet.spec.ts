import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneyDateSheet from '../JourneyDateSheet.vue'

const props = {
  eventTitle: 'DDP Architecture Tour',
  eventLocation: 'Seoul · Dongdaemun·DDP',
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  isPermanent: false,
  initialDate: '2026-08-08',
}

describe('JourneyDateSheet', () => {
  it('emits the selected date when the journey action is confirmed', async () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    const applyButton = wrapper.findAll('button').find((button) => button.text().includes('Add to'))

    await applyButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['2026-08-08']])
  })

  it('closes when the scrim is pressed', async () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Close date picker"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
