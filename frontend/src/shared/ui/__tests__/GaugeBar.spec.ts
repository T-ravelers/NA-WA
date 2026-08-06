import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import GaugeBar from '../GaugeBar.vue'

function mountGauge(value: number) {
  return mount(GaugeBar, { props: { value, label: 'Budget used' } })
}

function fillWidth(wrapper: ReturnType<typeof mountGauge>): string | undefined {
  return wrapper.get('[role="progressbar"] > div').attributes('style')
}

describe('GaugeBar', () => {
  it('is announced as a progress bar with a name and value', () => {
    const bar = mountGauge(0.42).get('[role="progressbar"]')

    expect(bar.attributes('aria-label')).toBe('Budget used')
    expect(bar.attributes('aria-valuenow')).toBe('42')
    expect(bar.attributes('aria-valuemin')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('100')
  })

  it('draws the fill proportionally', () => {
    expect(fillWidth(mountGauge(0.42))).toContain('width: 42%')
  })

  // 예산 초과는 실제로 일어난다. 100%를 넘겨 그리면 트랙 밖으로 삐져나온다.
  it('clamps a value above 1', () => {
    const wrapper = mountGauge(1.8)

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    expect(fillWidth(wrapper)).toContain('width: 100%')
  })

  it('clamps a negative value', () => {
    const wrapper = mountGauge(-0.5)

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
    expect(fillWidth(wrapper)).toContain('width: 0%')
  })

  // 0으로 나눈 결과가 그대로 넘어오는 경우가 있다.
  it('treats NaN as empty rather than rendering a broken width', () => {
    const wrapper = mountGauge(Number.NaN)

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
    expect(fillWidth(wrapper)).toContain('width: 0%')
  })
})
