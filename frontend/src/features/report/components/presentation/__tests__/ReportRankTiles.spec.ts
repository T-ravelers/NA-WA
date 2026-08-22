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
})
