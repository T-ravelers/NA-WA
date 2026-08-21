import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import {
  reportDailyTrendFixture,
  reportDailyTrendZeroFixture,
} from '../__fixtures__/reportPresentation'
import ReportDailyTrend from '../ReportDailyTrend.vue'
import type { ReportDailyTrendPoint, ReportDailyTrendProps } from '../types'

const EMPTY_COPY = {
  emptyTitle: 'No daily spending yet',
  emptyDescription: 'Days with expenses will show up here.',
}

/** 통화와 빈 상태 문구는 매번 같으므로 여기서 채우고, 나머지는 케이스별로 받는다. */
function mountTrend(props: Partial<ReportDailyTrendProps> & Pick<ReportDailyTrendProps, 'points'>) {
  return mount(ReportDailyTrend, {
    props: { currency: 'KRW', ...EMPTY_COPY, ...props },
    // 빈 상태에서만 `StateEmpty`가 만들어지고 그 안에서 `useI18n`이 불린다.
    global: { plugins: [i18n] },
  })
}

/** `"10,30 60,66"` 형태의 polyline 좌표를 숫자 쌍으로 되돌린다. */
function readPolyline(points: string): { x: number; y: number }[] {
  return points.split(' ').map((pair) => {
    const [x, y] = pair.split(',').map(Number)

    return { x: x ?? Number.NaN, y: y ?? Number.NaN }
  })
}

describe('ReportDailyTrend', () => {
  it('plots one vertex per day and spans the full plot width', () => {
    const wrapper = mountTrend({ points: reportDailyTrendFixture })
    const plotted = readPolyline(wrapper.get('polyline').attributes('points') ?? '')

    expect(plotted).toHaveLength(7)
    expect(plotted[0]?.x).toBe(8)
    expect(plotted[6]?.x).toBe(310)
  })

  it('puts the highest spending day at the top of the plot area', () => {
    const wrapper = mountTrend({ points: reportDailyTrendFixture })
    const plotted = readPolyline(wrapper.get('polyline').attributes('points') ?? '')

    // 최고액(262,000 P)은 상단 경계 16, 최저액(76,000 P)은 그보다 아래에 있다.
    expect(plotted[0]?.y).toBe(16)
    expect(plotted[2]?.y).toBeGreaterThan(16)
    expect(plotted.every((point) => point.y <= 93)).toBe(true)
    expect(wrapper.findAll('circle')).toHaveLength(reportDailyTrendFixture.length)
  })

  it('keeps every amount available as text for screen readers', () => {
    const wrapper = mountTrend({ points: reportDailyTrendFixture })
    const readout = wrapper.findAll('ul.sr-only li')

    expect(readout).toHaveLength(7)
    expect(readout[0]?.text()).toBe('Mar 28: 262,000 P')
    expect(readout[6]?.text()).toBe('Apr 5: 108,000 P')
  })

  it('labels only the first, middle and last day on the axis', () => {
    const wrapper = mountTrend({ points: reportDailyTrendFixture })
    const labels = wrapper.findAll('div[aria-hidden="true"] span').map((span) => span.text())

    expect(labels).toEqual(['Mar 28', 'Mar 31', 'Apr 5'])
  })

  it('flattens the line onto the baseline when nothing was spent', () => {
    const wrapper = mountTrend({ points: reportDailyTrendZeroFixture })
    const plotted = readPolyline(wrapper.get('polyline').attributes('points') ?? '')

    expect(plotted).toHaveLength(3)
    expect(plotted.every((point) => point.y === 93)).toBe(true)
    expect(wrapper.text()).toContain('May 2')
  })

  it('centres a single day instead of dividing by zero', () => {
    const single: ReportDailyTrendPoint[] = [{ date: '2026-04-01', label: 'Apr 1', amount: 90_000 }]
    const wrapper = mountTrend({ points: single })
    const plotted = readPolyline(wrapper.get('polyline').attributes('points') ?? '')

    expect(plotted).toHaveLength(1)
    expect(plotted[0]?.x).toBe(159)
    // 첫·마지막 점이 같은 항목이어도 표식은 하나만 남는다.
    expect(wrapper.findAll('circle')).toHaveLength(1)
  })

  it('shows the empty copy it was given instead of a chart', () => {
    const wrapper = mountTrend({ points: [] })

    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.text()).toContain('No daily spending yet')
    expect(wrapper.text()).toContain('Days with expenses will show up here.')
  })

  it('hides the decorative svg and disables the line transition under reduced motion', () => {
    const wrapper = mountTrend({
      points: reportDailyTrendFixture,
      description: 'Spending peaked on Mar 28.',
    })

    expect(wrapper.get('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.get('p.sr-only').text()).toBe('Spending peaked on Mar 28.')
    expect(wrapper.get('polyline').classes()).toContain('motion-reduce:transition-none')
  })
})
