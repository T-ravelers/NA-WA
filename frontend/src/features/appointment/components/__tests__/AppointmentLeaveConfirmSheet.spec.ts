import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentLeaveConfirmSheet from '../AppointmentLeaveConfirmSheet.vue'

function mountSheet(noShow = false) {
  return mount(AppointmentLeaveConfirmSheet, {
    props: {
      appointmentName: 'Seongsu Night Walk',
      depositAmount: '10000',
      noShow,
    },
    global: { plugins: [i18n] },
  })
}

describe('AppointmentLeaveConfirmSheet', () => {
  it('keeps the refundable leave confirmation neutral', () => {
    const wrapper = mountSheet()
    const refundCopy = wrapper
      .findAll('p')
      .find((paragraph) => paragraph.text().includes('refunded'))
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave group')

    expect(refundCopy?.classes()).toContain('text-on-paper/70')
    expect(refundCopy?.classes()).not.toContain('bg-danger/10')
    expect(confirmButton?.classes()).toContain('bg-paper-fill')
  })

  it('shows the C-2 warning surface and destructive action for a no-show forfeiture', () => {
    const wrapper = mountSheet(true)
    const warningCopy = wrapper
      .findAll('p')
      .find((paragraph) => paragraph.text().includes('will not be refunded'))
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave and forfeit')

    expect(warningCopy?.classes()).toEqual(
      expect.arrayContaining(['border-danger/40', 'bg-danger/10', 'text-danger']),
    )
    expect(confirmButton?.classes()).toEqual(
      expect.arrayContaining(['bg-danger', 'text-on-category']),
    )
  })

  it('emits confirm from the destructive action', async () => {
    const wrapper = mountSheet(true)
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave and forfeit')

    await confirmButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })
})
