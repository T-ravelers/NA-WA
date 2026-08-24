import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'
import AppTicket from '@/shared/ui/AppTicket.vue'
import GaugeBar from '@/shared/ui/GaugeBar.vue'

import type { JourneyDetail } from '../../api/journeyApi'
import JourneySummary from '../JourneySummary.vue'

const journey: JourneyDetail = {
  tripId: 7,
  title: 'Seoul and Busan',
  startDate: '2026-08-25',
  endDate: '2026-08-26',
  budgetAmount: 1_800_000,
  spentAmount: 1_284_500,
  companionPreference: '2-4',
  regions: [],
}

function mountSummary(overrides: Partial<JourneyDetail> = {}) {
  return mount(JourneySummary, {
    props: { journey: { ...journey, ...overrides } },
    global: { plugins: [i18n] },
  })
}

describe('JourneySummary', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the current budget comparison in a ticket with an accessible gauge', () => {
    const wrapper = mountSummary()

    expect(wrapper.getComponent(AppTicket).props('tone')).toBe('paper')
    expect(wrapper.text()).toContain('Spent')
    expect(wrapper.text()).toContain('1,284,500 P')
    expect(wrapper.text()).toContain('Left')
    expect(wrapper.text()).toContain('515,500 P')
    expect(wrapper.text()).toContain('71% of budget')
    expect(wrapper.text()).toContain('Budget 1,800,000 P')
    expect(wrapper.text()).toContain('Travel party')
    expect(wrapper.text()).toContain('2–4')
    expect(wrapper.getComponent(GaugeBar).props('value')).toBeCloseTo(1_284_500 / 1_800_000)
    expect(wrapper.get('[role="progressbar"]').attributes('aria-label')).toBe('71% of budget')
  })

  it('shows the overage while the visual gauge remains capped at 100 percent', () => {
    const wrapper = mountSummary({ budgetAmount: 1_000, spentAmount: 1_250 })

    expect(wrapper.text()).toContain('Over budget')
    expect(wrapper.text()).toContain('250 P')
    expect(wrapper.text()).toContain('125% of budget')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  })

  it('keeps spending visible when no budget limit exists', () => {
    const wrapper = mountSummary({ budgetAmount: null, spentAmount: 42_000 })

    expect(wrapper.text()).toContain('42,000 P')
    expect(wrapper.text()).toContain('No budget set')
    expect(wrapper.text()).toContain('No budget limit')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
  })

  it('does not divide by a zero budget', () => {
    const wrapper = mountSummary({ budgetAmount: 0, spentAmount: 1_000 })

    expect(wrapper.text()).toContain('Budget is 0 P')
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
  })
})
