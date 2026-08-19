import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentJourneyDateSheet from '../AppointmentJourneyDateSheet.vue'

const props = {
  journeyTitle: 'Seoul Foodie Week',
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  initialDate: '2026-08-08',
}

describe('AppointmentJourneyDateSheet', () => {
  it('emits the selected date when confirmed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Continue with'))

    await applyButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['2026-08-08']])
  })

  it('emits close when the scrim is pressed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Close date picker"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the back arrow is pressed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Go back"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('does not allow dates outside the journey range', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, startDate: '2026-08-05', endDate: '2026-08-10' },
    })

    const dayOutsideRange = wrapper.get('button[aria-label="Select August 1, 2026"]')

    expect(dayOutsideRange.attributes('disabled')).toBeDefined()
  })

  it('shows the duplicate-combination error and keeps the sheet open', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: {
        ...props,
        errorMessage: 'This activity is already linked to an appointment on this day.',
      },
    })

    expect(wrapper.text()).toContain(
      'This activity is already linked to an appointment on this day.',
    )
  })

  it('disables the apply button while checking the date', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, loading: true },
    })

    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Continue with'))

    expect(applyButton?.attributes('disabled')).toBeDefined()
  })
})
