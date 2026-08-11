import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentCreateForm from '../AppointmentCreateForm.vue'

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function fillForm(wrapper: ReturnType<typeof mount>): Promise<void> {
  const textInputs = wrapper.findAll('input[type="text"]')
  await textInputs[0]?.setValue('Seongsu K-Beauty Tour')
  await textInputs[2]?.setValue('Seongsu Beauty Lab')
  await wrapper.find('input[type="datetime-local"]').setValue('2026-08-08T18:30')
  await wrapper.findAll('input[type="datetime-local"]')[1]?.setValue('2026-08-08T22:00')
  await wrapper.findAll('input[type="datetime-local"]')[2]?.setValue('2026-08-08T17:30')
  const amountInput = wrapper.find('input[inputmode="numeric"]')
  await amountInput.setValue('10000')
}

describe('AppointmentCreateForm', () => {
  it('shows validation errors before opening confirmation', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter an appointment name.')
    expect(wrapper.text()).toContain('Choose a deposit between ₩5,000 and ₩50,000.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('emits a normalized request after confirming valid details', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      global: { plugins: [i18n] },
    })

    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    await buttonByText(wrapper, 'Confirm').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      itemId: 42,
      itemType: 'EVENT',
      languageCode: 'en',
      appointmentName: 'Seongsu K-Beauty Tour',
      maxMembers: 4,
      joinDeadline: '2026-08-08T17:30:00',
      depositAmount: '10000',
      meetingPlace: 'Seongsu Beauty Lab',
      meetingAddress: undefined,
      activityStartAt: '2026-08-08T18:30:00',
      activityEndAt: '2026-08-08T22:00:00',
    })
  })

  it('rejects a deposit outside the configured range', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'PLACE' },
      global: { plugins: [i18n] },
    })

    await fillForm(wrapper)
    await wrapper.find('input[inputmode="numeric"]').setValue('0')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose a deposit between ₩5,000 and ₩50,000.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
