import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementSummary } from '../../model/settlement'
import SettlementListCard from '../SettlementListCard.vue'

const settlement: SettlementSummary = {
  id: '17',
  title: 'Dinner split',
  totalAmount: '12000',
  receivableAmount: '6000',
  type: 'EQUAL',
  status: 'REQUESTED',
  viewer: {
    role: 'CREATOR',
    shareAmount: '6000',
    payableAmount: '0',
    requestStatus: 'NOT_REQUESTED',
    allowedActions: [],
  },
  createdAt: '2026-08-24T12:00:00',
  completedAt: '',
}

describe('SettlementListCard', () => {
  it('shows press feedback on the clickable card unless reduced motion is requested', () => {
    const wrapper = mount(SettlementListCard, {
      props: { settlement, side: 'sent' },
      global: { plugins: [i18n] },
    })

    expect(wrapper.get('button').classes()).toEqual(
      expect.arrayContaining([
        'transition-transform',
        'motion-reduce:transition-none',
        'active:scale-[0.98]',
        'motion-reduce:active:scale-100',
      ]),
    )
  })

  it('opens the settlement from the card', async () => {
    const wrapper = mount(SettlementListCard, {
      props: { settlement, side: 'sent' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('open')).toHaveLength(1)
  })
})
