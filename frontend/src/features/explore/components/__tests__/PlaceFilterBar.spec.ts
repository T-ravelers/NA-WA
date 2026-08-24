import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import PlaceFilterBar from '../PlaceFilterBar.vue'

describe('PlaceFilterBar', () => {
  it('hides the scrollbar on both horizontal filter rows', () => {
    const wrapper = mount(PlaceFilterBar, {
      global: { plugins: [i18n] },
      props: {
        activeSheet: null,
        placeKindOptions: [
          { key: 'RESTAURANT', label: 'Restaurant', selected: false },
          { key: 'CAFE', label: 'Cafe', selected: true },
        ],
        activeFilters: [],
      },
    })
    const rows = wrapper.findAll('.overflow-x-auto')

    expect(rows).toHaveLength(2)
    for (const row of rows) {
      expect(row.classes()).toContain('scrollbar-hidden')
    }
  })
})
