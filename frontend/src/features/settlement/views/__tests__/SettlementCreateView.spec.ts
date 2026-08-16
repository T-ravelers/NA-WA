import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import SettlementCreateView from '../SettlementCreateView.vue'

const { create } = vi.hoisted(() => ({ create: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { create } }))

const candidate = {
  transferId: '7',
  appointmentId: '9',
  payerAppointmentMemberId: '12',
  journeyName: 'Seoul',
  gatheringName: 'Dinner',
  merchantName: 'Cafe',
  amount: '25.00',
  paidAt: 'Aug 12',
  payerName: 'Alex',
  participants: [
    { id: '12', name: 'Alex', initials: 'AL' },
    { id: '19', name: 'Mina', initials: 'MI' },
  ],
}

describe('SettlementCreateView', () => {
  beforeEach(() => create.mockReset().mockResolvedValue({ id: '42' }))

  it('always includes the candidate payer appointment member when creating an equal settlement', async () => {
    const wrapper = mount(SettlementCreateView, {
      props: { candidates: [candidate] },
      global: { plugins: [i18n] },
    })

    await wrapper.get('[data-payment-id="7"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(create).toHaveBeenCalledWith(
      '9',
      expect.any(String),
      expect.objectContaining({
        sourceTransferId: '7',
        type: 'EQUAL',
        participantAppointmentMemberIds: ['12', '19'],
      }),
    )
    expect(wrapper.emitted('complete')).toEqual([['42']])
  })

  it('blocks ITEMIZED creation until each item quantity is allocated', async () => {
    const wrapper = mount(SettlementCreateView, {
      props: { candidates: [candidate] },
      global: { plugins: [i18n] },
    })
    await wrapper.get('[data-payment-id="7"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="add-item"]').trigger('click')

    await wrapper.get('[data-item-name="0"]').setValue('Dinner')
    await wrapper.get('[data-item-unit-price="0"]').setValue('12.50')
    await wrapper.get('[data-item-quantity="0"]').setValue('2')
    await wrapper.get('[data-allocation-quantity="0:12"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Allocate every item quantity before continuing')

    await wrapper.get('[data-allocation-quantity="0:19"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Final review')
  })
})
