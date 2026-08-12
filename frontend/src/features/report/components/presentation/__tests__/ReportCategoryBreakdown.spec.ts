import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import {
  reportCategoryBreakdownFixture,
  reportCategoryBreakdownZeroFixture,
} from '../__fixtures__/reportPresentation'
import ReportCategoryBreakdown from '../ReportCategoryBreakdown.vue'
import type { ReportCategoryBreakdownProps } from '../types'

const EMPTY_COPY = {
  emptyTitle: 'No spending yet',
  emptyDescription: 'Add an expense to see where your money went.',
}

/** 통화와 빈 상태 문구는 매번 같으므로 여기서 채우고, 나머지는 케이스별로 받는다. */
function mountBreakdown(
  props: Partial<ReportCategoryBreakdownProps> & Pick<ReportCategoryBreakdownProps, 'items'>,
) {
  return mount(ReportCategoryBreakdown, {
    props: { currency: 'KRW', ...EMPTY_COPY, ...props },
    // 빈 상태에서만 `StateEmpty`가 만들어지고 그 안에서 `useI18n`이 불린다.
    global: { plugins: [i18n] },
  })
}

describe('ReportCategoryBreakdown', () => {
  it('lists every category with its label, share and amount', () => {
    const wrapper = mountBreakdown({ items: reportCategoryBreakdownFixture })
    const rows = wrapper.findAll('li')

    expect(rows).toHaveLength(4)
    expect(rows[0]?.text()).toContain('Food')
    expect(rows[0]?.text()).toContain('42%')
    expect(rows[0]?.text()).toContain('₩539,500')
    expect(rows[3]?.text()).toContain('Beauty')
    expect(rows[3]?.text()).toContain('₩128,400')
  })

  it('draws one arc per non-zero category on top of the track ring', () => {
    const wrapper = mountBreakdown({ items: reportCategoryBreakdownFixture })
    const circles = wrapper.findAll('circle')

    // 트랙 1개 + 조각 4개.
    expect(circles).toHaveLength(5)
    expect(circles[1]?.attributes('stroke-dasharray')).toBe('42 58')
    expect(circles[1]?.attributes('stroke-dashoffset')).toBe('25')
    // 두 번째 조각은 첫 조각(42) 다음에서 시작한다.
    expect(circles[2]?.attributes('stroke-dashoffset')).toBe('-17')
  })

  it('keeps the legend readable when every amount is zero', () => {
    const wrapper = mountBreakdown({ items: reportCategoryBreakdownZeroFixture })

    // 조각은 하나도 그리지 않지만 트랙과 범례는 남는다.
    expect(wrapper.findAll('circle')).toHaveLength(1)
    expect(wrapper.findAll('li')).toHaveLength(3)
    expect(wrapper.text()).toContain('Uncategorized')
    expect(wrapper.text()).toContain('₩0')
    expect(wrapper.text()).toContain('0%')
  })

  it('shows the empty copy it was given instead of a chart', () => {
    const wrapper = mountBreakdown({ items: [] })

    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.text()).toContain('No spending yet')
    expect(wrapper.text()).toContain('Add an expense to see where your money went.')
  })

  it('exposes the chart description and hides the decorative svg', () => {
    const wrapper = mountBreakdown({
      items: reportCategoryBreakdownFixture,
      description: 'Food is the largest share at 42 percent.',
    })

    expect(wrapper.get('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.get('p.sr-only').text()).toBe('Food is the largest share at 42 percent.')
  })

  it('renders the centre readout only when both parts are passed', () => {
    const withCentre = mountBreakdown({
      items: reportCategoryBreakdownFixture,
      centerValue: '12',
      centerLabel: 'events',
    })
    const withoutCentre = mountBreakdown({ items: reportCategoryBreakdownFixture })

    expect(withCentre.text()).toContain('12')
    expect(withCentre.text()).toContain('events')
    expect(withoutCentre.text()).not.toContain('events')
  })

  it('disables the arc transition under reduced motion', () => {
    const wrapper = mountBreakdown({ items: reportCategoryBreakdownFixture })

    expect(wrapper.findAll('circle')[1]?.classes()).toContain('motion-reduce:transition-none')
  })
})
