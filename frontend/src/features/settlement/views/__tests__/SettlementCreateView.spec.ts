import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementCandidate } from '../../model/settlement'
import SettlementCreateView from '../SettlementCreateView.vue'

const { create } = vi.hoisted(() => ({ create: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { create } }))

function candidate(overrides: Partial<SettlementCandidate> = {}): SettlementCandidate {
  return {
    transferId: '7',
    appointmentId: '9',
    payerAppointmentMemberId: '12',
    journeyName: 'Seoul',
    gatheringName: 'Dinner',
    merchantName: 'Dinner',
    amount: '25.00',
    paidAt: 'Aug 12, 2026, 7:30 PM',
    payerName: 'Alex',
    participants: [
      { id: '12', name: 'Alex', initials: 'AL' },
      { id: '19', name: 'Mina', initials: 'MI' },
    ],
    ...overrides,
  }
}

function mountCreate(candidates: SettlementCandidate[] = [candidate()]) {
  return mount(SettlementCreateView, {
    props: { candidates },
    global: { plugins: [i18n] },
  })
}

async function drillDownToTransaction(wrapper: ReturnType<typeof mountCreate>) {
  await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
  await wrapper.get('[data-appointment-id="9"]').trigger('click')
  await wrapper.get('[data-payment-id="7"]').trigger('click')
  await wrapper.get('[data-action="next"]').trigger('click')
}

describe('SettlementCreateView', () => {
  beforeEach(() => create.mockReset().mockResolvedValue({ id: '42' }))

  it('narrows a journey to an appointment before offering its payments', async () => {
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', appointmentId: '10', gatheringName: 'Cafe' }),
      candidate({ transferId: '9', journeyName: 'Busan', appointmentId: '11' }),
    ])
    expect(wrapper.findAll('[data-journey-key]')).toHaveLength(2)
    expect(wrapper.find('[data-payment-id]').exists()).toBe(false)

    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
    expect(wrapper.findAll('[data-appointment-id]')).toHaveLength(2)
    expect(wrapper.find('[data-payment-id]').exists()).toBe(false)

    await wrapper.get('[data-appointment-id="9"]').trigger('click')
    expect(wrapper.findAll('[data-payment-id]')).toHaveLength(1)
  })

  it('counts payments in singular and plural', () => {
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', appointmentId: '10', gatheringName: 'Cafe' }),
      candidate({ transferId: '9', journeyName: 'Busan', appointmentId: '11' }),
    ])

    expect(wrapper.get('[data-journey-key="Seoul"]').text()).toContain('2 payments')
    expect(wrapper.get('[data-journey-key="Busan"]').text()).toContain('1 payment')
    expect(wrapper.get('[data-journey-key="Busan"]').text()).not.toContain('1 payments')
  })

  it('keeps the first step from continuing until a payment is chosen', async () => {
    const wrapper = mountCreate()
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
    await wrapper.get('[data-appointment-id="9"]').trigger('click')
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-payment-id="7"]').trigger('click')
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeUndefined()
  })

  it('blocks the review step until at least two participants are chosen', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Choose at least two participants to continue')

    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('always includes the candidate payer appointment member when creating an even split', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.get('[data-action="create"]').text()).toBe('Request 25.00 P')
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

  it('blocks itemized creation until each item quantity is allocated', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="add-item"]').trigger('click')

    await wrapper.get('[data-item-name="0"]').setValue('Pasta')
    await wrapper.get('[data-item-unit-price="0"]').setValue('12.50')
    await wrapper.get('[data-item-quantity="0"]').setValue('2')
    await wrapper.get('[data-allocation-quantity="0:12"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Allocate every item quantity before continuing')

    await wrapper.get('[data-allocation-quantity="0:19"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('keeps the receipt entry point disabled until receipt capture ships', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    expect(wrapper.get('[data-action="add-receipt"]').attributes('disabled')).toBeDefined()
  })
})
