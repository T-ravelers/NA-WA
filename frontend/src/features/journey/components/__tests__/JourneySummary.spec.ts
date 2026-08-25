import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'
import AppTicket from '@/shared/ui/AppTicket.vue'
import GaugeBar from '@/shared/ui/GaugeBar.vue'

import type { JourneyDetail } from '../../api/journeyApi'
import type { JourneyExpenseCandidate } from '../../model/reportIntegration'
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

/* 범례가 세는 자리. 금액은 `journey.spentAmount`가 정본이라 이 합계와 일부러 다르게 둔다. */
const expenses: JourneyExpenseCandidate[] = [
  { amount: '30000', category: 'FOOD', occurredDate: '2026-08-25', displayMemo: null },
  { amount: '20000', category: 'SHOPPING', occurredDate: '2026-08-25', displayMemo: null },
  { amount: '10000', category: 'BEAUTY', occurredDate: '2026-08-26', displayMemo: null },
  { amount: '5000', category: 'SHOW', occurredDate: '2026-08-26', displayMemo: null },
]

function mountSummary(
  overrides: Partial<JourneyDetail> = {},
  props: { expenses?: JourneyExpenseCandidate[]; itemCount?: number } = {},
) {
  return mount(JourneySummary, {
    props: {
      journey: { ...journey, ...overrides },
      expenses: props.expenses ?? expenses,
      itemCount: props.itemCount ?? 12,
    },
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
    expect(wrapper.getComponent(GaugeBar).props('value')).toBeCloseTo(1_284_500 / 1_800_000)
    expect(wrapper.get('[role="progressbar"]').attributes('aria-label')).toBe('71% of budget')
  })

  it('reads the ticket stub as the top three consumption areas and the item count', () => {
    const wrapper = mountSummary()

    // 시안의 stub은 동행 인원이 아니라 소비영역 범례와 담은 항목 수다.
    expect(wrapper.text()).not.toContain('Travel party')
    expect(wrapper.text()).toContain('12 events')
    expect(wrapper.text()).toContain('Food')
    expect(wrapper.text()).toContain('Shopping')
    expect(wrapper.text()).toContain('Beauty')
    // 금액 순 상위 셋까지만 둔다.
    expect(wrapper.text()).not.toContain('Shows')
  })

  it('keeps the ticket total on the server figure rather than the candidate list', () => {
    const wrapper = mountSummary()

    // 후보 합계는 65,000 P다. 정산으로 회수한 금액까지 상계한 서버 값이 정본이다(#541).
    expect(wrapper.text()).toContain('1,284,500 P')
    expect(wrapper.text()).not.toContain('65,000 P')
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
