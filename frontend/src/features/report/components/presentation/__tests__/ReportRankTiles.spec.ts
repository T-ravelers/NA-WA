import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReportRankTiles from '../ReportRankTiles.vue'

describe('ReportRankTiles', () => {
  it('renders a tile per rank with the category tone it is given', () => {
    const wrapper = mount(ReportRankTiles, {
      props: {
        tiles: [
          { key: 'FOOD', label: 'Food', rankText: '1st', tone: 'food' },
          { key: 'SHOPPING', label: 'Shopping', rankText: '2nd', tone: 'shopping' },
          { key: 'STAY', label: 'Stay', rankText: '4th', tone: 'surface' },
        ],
      },
    })

    const tiles = wrapper.findAll('li')
    expect(tiles).toHaveLength(3)
    expect(tiles[0]?.text()).toContain('# Food')
    expect(tiles[0]?.text()).toContain('1st')
    expect(tiles[0]?.classes()).toContain('bg-food')
    expect(tiles[2]?.classes()).toContain('bg-surface-2')
  })

  it('renders nothing but an empty list when there are no ranks', () => {
    const wrapper = mount(ReportRankTiles, { props: { tiles: [] } })

    expect(wrapper.findAll('li')).toHaveLength(0)
  })

  // 값이 순위인지 코호트 대비 비중인지는 화면이 안다. `# Food +12%`만으로는 무엇 대비인지 읽히지 않는다.
  it('names the list when the screen gives it a label, and stays unnamed otherwise', () => {
    const tiles = [{ key: 'FOOD', label: 'Food', rankText: '+12%', tone: 'food' as const }]

    expect(
      mount(ReportRankTiles, { props: { tiles, label: 'Share vs travelers like you' } })
        .get('ul')
        .attributes('aria-label'),
    ).toBe('Share vs travelers like you')
    expect(mount(ReportRankTiles, { props: { tiles } }).get('ul').attributes('aria-label')).toBe(
      undefined,
    )
  })
})
