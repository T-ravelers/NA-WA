import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import ExploreFilterBar from '../ExploreFilterBar.vue'

describe('ExploreFilterBar', () => {
  const props = {
    activeSheet: null,
    eventKindOptions: [
      { key: 'POPUP', label: 'Popup', selected: false },
      { key: 'CONCERT', label: 'Concert', selected: true },
    ],
    activeFilters: [],
  }

  it('opens a requested filter sheet', async () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('open')).toEqual([['date']])
  })

  it('emits an event kind toggle for the quick chips', async () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })

    await wrapper.findAll('button')[4]?.trigger('click')

    expect(wrapper.emitted('toggleKind')).toEqual([['POPUP']])
  })

  it('shows the global reset when only an event kind is selected', () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })

    expect(wrapper.findAll('button').some((button) => button.text() === 'Reset')).toBe(true)
  })
})
