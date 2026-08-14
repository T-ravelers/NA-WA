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

  it('uses the same All of Seoul and Other areas behavior as Event', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Other areas')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    const applied = wrapper.emitted('apply') ?? []
    expect(applied[applied.length - 1]?.[0]).toMatchObject({
      region1: ['서울'],
      region2Other: true,
    })
  })

  it('normalizes option deselection to undefined', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'options', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    const parkingButton = wrapper.findAll('button').find((button) => button.text() === 'Parking')
    await parkingButton?.trigger('click')

    let changes = wrapper.emitted('change') ?? []
    expect(changes[changes.length - 1]?.[0]).toMatchObject({ hasParking: true })

    await parkingButton?.trigger('click')

    changes = wrapper.emitted('change') ?? []
    expect(changes[changes.length - 1]?.[0]).toMatchObject({ hasParking: undefined })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ hasParking: undefined })
  })
})
