import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentJourneySelectSheet from '../AppointmentJourneySelectSheet.vue'

const journeys = [
  {
    tripId: 12,
    title: 'Seoul Foodie Week',
    startDate: '2026-03-28',
    endDate: '2026-04-01',
  },
]

describe('AppointmentJourneySelectSheet', () => {
  it('emits the selected journey', async () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie'))
      ?.trigger('click')

    expect(wrapper.emitted('select')).toEqual([[12]])
  })

  it('emits close when the scrim is pressed', async () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys },
    })

    await wrapper.get('button[aria-label="Close journey selector"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the back arrow is pressed', async () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys },
    })

    await wrapper.get('button[aria-label="Go back"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('shows an empty state with a create-journey action when there are no journeys', async () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys: [] },
    })

    expect(wrapper.text()).toContain('Create a journey before creating this appointment.')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Create a journey'))
      ?.trigger('click')

    expect(wrapper.emitted('createJourney')).toHaveLength(1)
  })

  it('shows a loading state instead of the list', () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys, loading: true },
    })

    expect(wrapper.text()).toContain('Loading your journeys…')
    expect(wrapper.text()).not.toContain('Seoul Foodie Week')
  })

  it('shows an error message instead of the list', () => {
    const wrapper = mount(AppointmentJourneySelectSheet, {
      global: { plugins: [i18n] },
      props: { journeys, errorMessage: 'We could not load your journeys. Please try again.' },
    })

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'We could not load your journeys. Please try again.',
    )
  })
})
