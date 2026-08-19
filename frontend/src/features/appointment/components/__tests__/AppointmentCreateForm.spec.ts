import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentCreateForm from '../AppointmentCreateForm.vue'

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function fillBasics(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
    .setValue('Seongsu K-Beauty Tour')
  await wrapper.get('form').trigger('submit')
}

async function fillSettings(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.find('input[inputmode="numeric"]').setValue('10000')
  await wrapper.find('input[placeholder="e.g. Seongsu Beauty Lab"]').setValue('Seongsu Beauty Lab')
}

async function fillSchedule(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.find('input[type="time"]').setValue('18:30')
  await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')
  await wrapper.find('input[type="datetime-local"]').setValue('2026-08-08T17:30')
}

describe('AppointmentCreateForm', () => {
  it('offers Traditional Chinese without a Simplified Chinese option', () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain('Chinese (Traditional)')
    expect(wrapper.text()).not.toContain('Chinese (Simplified)')
  })

  it('shows validation errors before opening confirmation', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter an appointment name.')
    expect(wrapper.text()).not.toContain('Choose a deposit between ₩5,000 and ₩50,000.')
    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('moves between the basics and settings steps', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    await fillBasics(wrapper)

    expect(wrapper.text()).toContain('Set your appointment details')
    expect(wrapper.find('input[placeholder="e.g. Seongsu Beauty Lab"]').exists()).toBe(true)

    await buttonByText(wrapper, 'Back').trigger('click')

    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(wrapper.find('input[placeholder="e.g. Seongsu K-Beauty Tour"]').exists()).toBe(true)
  })

  it('emits a normalized request after confirming valid details', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      global: { plugins: [i18n] },
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Set the appointment schedule')
    await fillSchedule(wrapper)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    await buttonByText(wrapper, 'Confirm').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      itemId: 42,
      itemType: 'EVENT',
      tripId: 7,
      visitDate: '2026-08-08',
      languageCode: 'en',
      appointmentName: 'Seongsu K-Beauty Tour',
      maxMembers: 4,
      joinDeadline: '2026-08-08T17:30:00',
      depositAmount: '10000',
      meetingPlace: 'Seongsu Beauty Lab',
      meetingAddress: undefined,
      activityStartTime: '18:30:00',
      activityEndTime: '22:00:00',
    })
  })

  it('clears a basics validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter an appointment name.')

    await wrapper
      .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
      .setValue('Seongsu K-Beauty Tour')

    expect(wrapper.text()).not.toContain('Enter an appointment name.')
  })

  it('clears a settings validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'PLACE' },
      global: { plugins: [i18n] },
    })

    await fillBasics(wrapper)
    await wrapper.find('input[inputmode="numeric"]').setValue('0')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose a deposit between ₩5,000 and ₩50,000.')

    await wrapper.find('input[inputmode="numeric"]').setValue('10000')

    expect(wrapper.text()).not.toContain('Choose a deposit between ₩5,000 and ₩50,000.')
  })

  it('clears a schedule validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      global: { plugins: [i18n] },
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.get('form').trigger('submit')

    await wrapper.find('input[type="time"]').setValue('18:30')
    // 종료가 시작보다 늦지 않아 endAfterStart 에러가 나야 하는 값.
    await wrapper.findAll('input[type="time"]')[1]?.setValue('18:30')
    await wrapper.find('input[type="datetime-local"]').setValue('2026-08-08T17:30')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('The end time must be after the start time.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')

    expect(wrapper.text()).not.toContain('The end time must be after the start time.')
  })

  it('rejects a deposit outside the configured range', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'PLACE' },
      global: { plugins: [i18n] },
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.find('input[inputmode="numeric"]').setValue('0')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose a deposit between ₩5,000 and ₩50,000.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
