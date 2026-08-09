import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import PlaceFilterSheet from '../PlaceFilterSheet.vue'

describe('PlaceFilterSheet', () => {
  it('keeps Saved independent from the sort choice', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')

    const changes = wrapper.emitted('change') ?? []
    expect(changes[changes.length - 1]?.[0]).toMatchObject({
      sort: 'LATEST',
      savedOnly: true,
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ savedOnly: true })
  })

  it('emits the selected region and area', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Gyeonggi'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Suwon'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    const applied = wrapper.emitted('apply') ?? []
    expect(applied[applied.length - 1]?.[0]).toMatchObject({
      region1: ['Gyeonggi'],
      region2: ['Suwon'],
    })
  })
})
