import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReportRadarChart from '../ReportRadarChart.vue'

const AXES = [
  { key: 'FOOD', label: 'Food', mine: 42, cohort: 30 },
  { key: 'SHOPPING', label: 'Shopping', mine: 31, cohort: 45 },
  { key: 'SHOW', label: 'Shows', mine: 17, cohort: 10 },
  { key: 'BEAUTY', label: 'Beauty', mine: 10, cohort: 15 },
]

const LABELS = { mineLabel: 'You', cohortLabel: 'Group avg' }

describe('ReportRadarChart', () => {
  it('draws one polygon per series and a spoke per axis', () => {
    const wrapper = mount(ReportRadarChart, { props: { axes: AXES, ...LABELS } })

    // 링 4 + 계열 2
    expect(wrapper.findAll('polygon')).toHaveLength(6)
    expect(wrapper.findAll('line')).toHaveLength(4)
    expect(wrapper.findAll('circle')).toHaveLength(4)
  })

  it('keeps the axis labels in HTML, colored by category, not inside the SVG', () => {
    const wrapper = mount(ReportRadarChart, { props: { axes: AXES, ...LABELS } })

    expect(wrapper.find('svg text').exists()).toBe(false)
    const labels = wrapper.findAll('span[aria-hidden="true"].absolute')
    expect(labels.map((label) => label.text())).toEqual(['Food', 'Shopping', 'Shows', 'Beauty'])
    expect(labels[0]?.classes()).toContain('text-food')
    expect(labels[0]?.attributes('style')).toContain('top: 6%')
  })

  it('reads every value out loud for screen readers', () => {
    const wrapper = mount(ReportRadarChart, { props: { axes: AXES, ...LABELS } })

    expect(wrapper.get('ul.sr-only').text()).toContain('Food: You 42%, Group avg 30%')
  })

  it('does not draw with fewer than three axes but still lists the values', () => {
    const wrapper = mount(ReportRadarChart, { props: { axes: AXES.slice(0, 2), ...LABELS } })

    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.get('ul.sr-only').text()).toContain('Shopping')
  })

  it('scales the outer ring to the largest value so small shares stay visible', () => {
    const wrapper = mount(ReportRadarChart, {
      props: {
        axes: AXES.map((axis) => ({ ...axis, mine: axis.mine / 10, cohort: 0 })),
        ...LABELS,
      },
    })

    // 최댓값(4.2)이 바깥 링에 닿는다 — 12시 방향 점이 RADIUS만큼 위로 간다.
    const firstPoint = wrapper.findAll('circle')[0]
    expect(Number(firstPoint?.attributes('cy'))).toBeCloseTo(100 - 68, 0)
  })
})
