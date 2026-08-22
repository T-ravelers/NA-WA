import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReportComparisonBars from '../ReportComparisonBars.vue'

const PROPS = {
  totalLabel: 'Total spend',
  chipsLabel: 'Group members',
  me: { id: 1, label: 'You', amount: 1284500 },
  peers: [
    { id: 2, label: 'Mina', amount: 978400 },
    { id: 3, label: 'Jae', amount: 510000 },
  ],
}

describe('ReportComparisonBars', () => {
  // i18n 플러그인 없이 마운트한다. props-only 계약이 실제로 상위와 끊겨 있는지 보는 것이다.
  it('compares me with the first peer by default and scales bars to the larger amount', () => {
    const wrapper = mount(ReportComparisonBars, { props: PROPS })

    expect(wrapper.text()).toContain('Total spend')
    expect(wrapper.findAll('dt').map((row) => row.text())).toEqual(['You', 'Mina'])
    expect(wrapper.text()).toContain('1,284,500 P')
    expect(wrapper.text()).toContain('978,400 P')

    const fills = wrapper.findAll('dd span span')
    expect(fills[0]?.attributes('style')).toContain('width: 100%')
    expect(fills[1]?.attributes('style')).toContain('width: 76.')
  })

  it('switches the compared peer from the chips', async () => {
    const wrapper = mount(ReportComparisonBars, { props: PROPS })
    const chips = wrapper.findAll('[role="radio"]')

    expect(chips.map((chip) => chip.attributes('aria-checked'))).toEqual(['true', 'false'])

    await chips[1]?.trigger('click')

    expect(wrapper.findAll('dt').map((row) => row.text())).toEqual(['You', 'Jae'])
    expect(wrapper.text()).toContain('510,000 P')
  })

  it('shows only my bar when there is no peer, without a chip group', () => {
    const wrapper = mount(ReportComparisonBars, { props: { ...PROPS, peers: [] } })

    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
    expect(wrapper.findAll('dt')).toHaveLength(1)
  })

  it('leaves bars empty when everyone spent nothing', () => {
    const wrapper = mount(ReportComparisonBars, {
      props: {
        ...PROPS,
        me: { id: 1, label: 'You', amount: 0 },
        peers: [{ id: 2, label: 'Mina', amount: 0 }],
      },
    })

    const fills = wrapper.findAll('dd span span')
    expect(fills.every((fill) => fill.attributes('style')?.includes('width: 0%'))).toBe(true)
    expect(wrapper.text()).toContain('0 P')
  })
})
