import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

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

  it('holds the sending screen after a success so the request cannot be sent twice', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 부모가 다음 화면으로 넘기기 전까지 검토 화면이 다시 보이면 안 된다. 멱등키는 이미
    // 지워져 있어 두 번째 요청은 새 키로 나간다.
    expect(wrapper.find('[data-action="create"]').exists()).toBe(false)
    expect(wrapper.emitted('submittingChange')).toEqual([[true]])
    expect(create).toHaveBeenCalledTimes(1)
  })

  it('returns to the review step when the request fails', async () => {
    create.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-005', 400, 'invalid'))
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-action="create"]').exists()).toBe(true)
    expect(wrapper.emitted('submittingChange')).toEqual([[true], [false]])
    expect(wrapper.emitted('complete')).toBeUndefined()
  })

  it('drops the allocations of a participant who is removed', async () => {
    const wrapper = mountCreate([
      candidate({
        participants: [
          { id: '12', name: 'Alex', initials: 'AL' },
          { id: '19', name: 'Mina', initials: 'MI' },
          { id: '27', name: 'Sora', initials: 'SO' },
        ],
      }),
    ])
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-participant-id="27"]').trigger('click')
    await wrapper.get('[data-action="add-item"]').trigger('click')

    await wrapper.get('[data-item-name="0"]').setValue('Pasta')
    await wrapper.get('[data-item-unit-price="0"]').setValue('12.50')
    await wrapper.get('[data-item-quantity="0"]').setValue('3')
    await wrapper.get('[data-allocation-quantity="0:12"]').setValue('1')
    await wrapper.get('[data-allocation-quantity="0:19"]').setValue('1')
    await wrapper.get('[data-allocation-quantity="0:27"]').setValue('1')

    await wrapper.get('[data-participant-id="27"]').trigger('click')

    // 다시 선택하면 이전 값이 아니라 빈 칸으로 시작한다. 값이 지워졌다는 증거다.
    await wrapper.get('[data-participant-id="27"]').trigger('click')
    expect(
      (wrapper.get('[data-allocation-quantity="0:27"]').element as HTMLInputElement).value,
    ).toBe('')

    // 해제한 사람의 배분값이 남으면, 보이는 칸이 모두 맞아도 검증이 숨은 1을 계속 더해
    // 어떤 편집으로도 다음 단계로 넘어갈 수 없다.
    await wrapper.get('[data-participant-id="27"]').trigger('click')
    await wrapper.get('[data-item-quantity="0"]').setValue('2')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('returns to the first step when the chosen payment disappears from a refetch', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('no longer available')
    expect(wrapper.text()).toContain('No payments available')
    const steps = wrapper.emitted('update:step') ?? []
    expect(steps[steps.length - 1]).toEqual([1])
  })

  it('keeps the wizard on its step when a refetch still holds the chosen payment', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [candidate(), candidate({ transferId: '8' })] })
    await flushPromises()

    expect(wrapper.text()).not.toContain('no longer available')
    expect(wrapper.get('[data-participant-id="19"]').attributes('aria-pressed')).toBe('true')
  })

  it('leaves the wizard alone when candidates change after a successful submit', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 성공 직후 부모의 무효화가 정산된 결제를 뺀 목록을 내려보낸다. 이때 1단계로 되돌리면
    // 부모가 다음 화면으로 넘기기 전까지 살아 있는 성공 화면 뒤에서 상태가 뒤집힌다.
    await wrapper.setProps({ candidates: [] })
    await flushPromises()

    const steps = wrapper.emitted('update:step') ?? []
    expect(steps[steps.length - 1]).toEqual([3])
  })

  it('clears the gone-payment notice when the user changes the journey', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [] })
    await flushPromises()
    await wrapper.setProps({ candidates: [candidate({ transferId: '8' })] })
    await flushPromises()
    expect(wrapper.text()).toContain('no longer available')

    // 여정을 갈아타는 순간은 이미 다른 결제를 고르는 중이라 안내가 소임을 다한 시점이다.
    await wrapper.get('[data-action="change-journey"]').trigger('click')
    expect(wrapper.text()).not.toContain('no longer available')
  })

  it('keeps the receipt entry point disabled until receipt capture ships', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    expect(wrapper.get('[data-action="add-receipt"]').attributes('disabled')).toBeDefined()
  })
})
