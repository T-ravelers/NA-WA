import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentDepositSheet from '../AppointmentDepositSheet.vue'

describe('AppointmentDepositSheet', () => {
  it('shows the refundable deposit and keeps payment disabled', () => {
    const wrapper = mount(AppointmentDepositSheet, {
      props: {
        appointmentName: 'Seongsu K-Beauty Tour',
        depositAmount: '10000',
        confirmDisabled: true,
      },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain('₩10,000')
    expect(wrapper.text()).toContain('Refundable deposit')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text().includes('Pay'))
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('emits close from the cancel action', async () => {
    const wrapper = mount(AppointmentDepositSheet, {
      props: { appointmentName: 'Seongsu K-Beauty Tour', depositAmount: '10000' },
      global: { plugins: [i18n] },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancel')
      ?.trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
