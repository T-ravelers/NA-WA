import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import ExploreFilterSheet from '../ExploreFilterSheet.vue'

describe('ExploreFilterSheet', () => {
  it('does not expose saved sorting before the saved API is connected', () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    expect(wrapper.findAll('button').some((button) => button.text() === 'Saved')).toBe(false)
  })

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
    expect(wrapper.emitted('change')?.[wrapper.emitted('change')!.length - 1]?.[0]).toMatchObject({
      freeOnly: true,
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ freeOnly: true })
  })

  it('clears the current sheet selections without applying or closing', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: {
        kind: 'options',
        filters: { sort: 'POPULAR', freeOnly: true },
        resultCount: 3,
      },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Reset')
      ?.trigger('click')

    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })

  it('offers subregions for regions outside Seoul', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    const gyeonggiButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Gyeonggi'))
    await gyeonggiButton?.trigger('click')

    const suwonButton = wrapper.findAll('button').find((button) => button.text().includes('Suwon'))
    expect(suwonButton).toBeDefined()

    await suwonButton?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[wrapper.emitted('apply')!.length - 1]?.[0]).toMatchObject({
      region1: ['Gyeonggi'],
      region2: ['Suwon'],
    })
  })

  it('can select unclassified region2 values as other areas', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Gyeonggi'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Other areas')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[wrapper.emitted('apply')!.length - 1]?.[0]).toMatchObject({
      region1: ['Gyeonggi'],
      region2Other: true,
    })
  })

  it('can combine a named subregion with other areas', async () => {
    const wrapper = mount(ExploreFilterSheet, {
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
      .find((button) => button.text() === 'Other areas')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[wrapper.emitted('apply')!.length - 1]?.[0]).toMatchObject({
      region1: ['Gyeonggi'],
      region2: ['Suwon'],
      region2Other: true,
    })
  })

  it('selects a sector and its activities using the existing numeric filter contract', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'LATEST' }, resultCount: 3 },
    })

    const foodHeader = wrapper.findAll('button').find((button) => button.text().includes('Food'))
    await foodHeader?.find('[role="checkbox"]').trigger('click')

    expect(wrapper.emitted('change')?.[wrapper.emitted('change')!.length - 1]?.[0]).toMatchObject({
      sectorIds: [2],
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cafe / Dessert')
      ?.trigger('click')

    expect(wrapper.emitted('change')?.[wrapper.emitted('change')!.length - 1]?.[0]).toMatchObject({
      sectorIds: undefined,
      activityIds: [9],
    })
  })
})
