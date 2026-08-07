import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import ExploreFilterSheet from '../ExploreFilterSheet.vue'

describe('ExploreFilterSheet', () => {
  it('emits close when the scrim is pressed', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'options', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper.get('button[aria-label="Close filter sheet"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('keeps option changes local until Apply is pressed', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'options', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Free')
      ?.trigger('click')
    expect(wrapper.emitted('apply')).toBeUndefined()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ freeOnly: true })
  })
})
