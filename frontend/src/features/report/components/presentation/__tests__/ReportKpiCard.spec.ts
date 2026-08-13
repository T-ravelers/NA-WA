import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { reportKpiFixture, reportKpiZeroFixture } from '../__fixtures__/reportPresentation'
import ReportKpiCard from '../ReportKpiCard.vue'

const LABELS = {
  totalLabel: 'Total spent',
  dailyAverageLabel: 'Daily avg',
}

describe('ReportKpiCard', () => {
  // i18n 플러그인 없이 마운트한다. props-only 계약이 실제로 상위와 끊겨 있는지 보는 것이다.
  it('renders both amounts and their labels from props alone', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS },
    })

    expect(wrapper.text()).toContain('Total spent')
    expect(wrapper.text()).toContain('₩1,284,500')
    expect(wrapper.text()).toContain('Daily avg')
    expect(wrapper.text()).toContain('₩142,700')
  })

  it('renders a zero journey without collapsing the layout', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiZeroFixture, ...LABELS },
    })

    expect(wrapper.findAll('dd').map((amount) => amount.text())).toEqual(['₩0', '₩0'])
  })

  it('renders the section heading only when one is passed', () => {
    const withoutHeading = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS },
    })
    const withHeading = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS, heading: 'Analysis' },
    })

    expect(withoutHeading.find('h2').exists()).toBe(false)
    expect(withHeading.get('h2').text()).toBe('Analysis')
  })

  it('formats amounts in the locale it is given', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS, locale: 'ja' },
    })

    // 로케일에 따라 구분 기호가 달라져도 값은 같아야 한다.
    expect(wrapper.text()).toContain('1,284,500')
  })
})
