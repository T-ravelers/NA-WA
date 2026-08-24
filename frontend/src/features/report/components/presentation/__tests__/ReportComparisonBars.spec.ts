import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReportComparisonBars from '../ReportComparisonBars.vue'

const PROPS = {
  totalLabel: 'Total spend',
  dailyAverageLabel: 'Daily avg',
  chipsLabel: 'Group members',
  me: { id: 1, label: 'You', totalSpent: 1_284_500, dailyAverage: 128_450 },
  peers: [
    {
      id: 2,
      label: 'Mina',
      totalSpent: 978_400,
      dailyAverage: 64_225,
      profileImageUrl: 'https://example.test/mina.png',
    },
    {
      id: 3,
      label: 'Jae',
      totalSpent: 510_000,
      dailyAverage: 51_000,
      profileImageUrl: null,
    },
  ],
}

describe('ReportComparisonBars', () => {
  // i18n 플러그인 없이 마운트한다. props-only 계약이 실제로 상위와 끊겨 있는지 보는 것이다.
  it('compares total spend and daily average with independent scales', () => {
    const wrapper = mount(ReportComparisonBars, { props: PROPS })
    const total = wrapper.get('[data-metric="totalSpent"]')
    const dailyAverage = wrapper.get('[data-metric="dailyAverage"]')

    expect(total.text()).toContain('Total spend')
    expect(dailyAverage.text()).toContain('Daily avg')
    expect(total.findAll('dt').map((row) => row.text())).toEqual(['You', 'Mina'])
    expect(dailyAverage.findAll('dt').map((row) => row.text())).toEqual(['You', 'Mina'])
    expect(total.text()).toContain('1,284,500 P')
    expect(total.text()).toContain('978,400 P')
    expect(dailyAverage.text()).toContain('128,450 P')
    expect(dailyAverage.text()).toContain('64,225 P')

    const totalFills = total.findAll('dd span span')
    const dailyAverageFills = dailyAverage.findAll('dd span span')
    expect(totalFills[0]?.attributes('style')).toContain('width: 100%')
    expect(totalFills[1]?.attributes('style')).toContain('width: 76.')
    expect(dailyAverageFills[0]?.attributes('style')).toContain('width: 100%')
    expect(dailyAverageFills[1]?.attributes('style')).toContain('width: 50%')
  })

  it('mixes a profile photo with a grapheme-safe initial fallback', () => {
    const wrapper = mount(ReportComparisonBars, {
      props: {
        ...PROPS,
        peers: [
          PROPS.peers[0]!,
          {
            ...PROPS.peers[1]!,
            label: '🇰🇷 Jae',
          },
        ],
      },
    })
    const chips = wrapper.findAll('[role="radio"]')

    expect(chips[0]?.get('img').attributes('src')).toBe('https://example.test/mina.png')
    expect(chips[0]?.get('img').attributes('alt')).toBe('')
    expect(chips[1]?.find('img').exists()).toBe(false)
    expect(chips[1]?.get('[aria-hidden="true"]').text()).toBe('🇰🇷')
  })

  it('falls back to the initial when a profile photo fails to load', async () => {
    const wrapper = mount(ReportComparisonBars, { props: PROPS })
    const firstChip = wrapper.get('[role="radio"]')

    await firstChip.get('img').trigger('error')

    expect(firstChip.find('img').exists()).toBe(false)
    expect(firstChip.get('[aria-hidden="true"]').text()).toBe('M')
  })

  it('switches both compared metrics from the shared peer chips', async () => {
    const wrapper = mount(ReportComparisonBars, { props: PROPS })
    const chips = wrapper.findAll('[role="radio"]')

    expect(chips.map((chip) => chip.attributes('aria-checked'))).toEqual(['true', 'false'])

    await chips[1]?.trigger('click')

    expect(wrapper.findAll('dt').map((row) => row.text())).toEqual(['You', 'Jae', 'You', 'Jae'])
    expect(wrapper.get('[data-metric="totalSpent"]').text()).toContain('510,000 P')
    expect(wrapper.get('[data-metric="dailyAverage"]').text()).toContain('51,000 P')
  })

  it('shows only my two bars when there is no peer, without a chip group', () => {
    const wrapper = mount(ReportComparisonBars, { props: { ...PROPS, peers: [] } })

    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
    expect(wrapper.findAll('dt')).toHaveLength(2)
  })

  it('leaves both metric bars empty when everyone spent nothing', () => {
    const wrapper = mount(ReportComparisonBars, {
      props: {
        ...PROPS,
        me: { id: 1, label: 'You', totalSpent: 0, dailyAverage: 0 },
        peers: [
          {
            id: 2,
            label: 'Mina',
            totalSpent: 0,
            dailyAverage: 0,
            profileImageUrl: null,
          },
        ],
      },
    })

    const fills = wrapper.findAll('dd span span')
    expect(fills.every((fill) => fill.attributes('style')?.includes('width: 0%'))).toBe(true)
    expect(wrapper.findAll('dd').every((cell) => cell.text().includes('0 P'))).toBe(true)
  })

  // SIMILAR처럼 상대가 평균 하나뿐이면 고를 것이 없다. 칩 없이 막대 두 쌍만 그린다.
  it('draws the first peer for both metrics without chips when chips are turned off', () => {
    const wrapper = mount(ReportComparisonBars, { props: { ...PROPS, chips: false } })

    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
    expect(wrapper.findAll('dt').map((cell) => cell.text())).toEqual(['You', 'Mina', 'You', 'Mina'])
  })
})
