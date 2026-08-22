import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import PlaceFilterSheet from '../PlaceFilterSheet.vue'

describe('PlaceFilterSheet', () => {
  it('keeps the current sort while Saved is the checked item', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')

    const changes = wrapper.emitted('change') ?? []
    expect(changes[changes.length - 1]?.[0]).toMatchObject({
      sort: 'NEWEST',
      savedOnly: true,
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ savedOnly: true })
  })

  it('turns Saved off when a sort is picked', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST', savedOnly: true }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Popular'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    /* 세 항목은 배타적이다. 정렬을 고르면 Saved는 꺼진다. */
    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({
      sort: 'POPULAR',
      savedOnly: undefined,
    })
  })

  it('moves the single check onto Saved instead of leaving it on the sort', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    /** 그 이름의 줄에 체크가 붙어 있는가. 개수만 세면 체크가 엉뚱한 줄에 남아도 통과한다. */
    const isChecked = (label: string) =>
      wrapper
        .findAll('button')
        .find((button) => button.text().startsWith(label))
        ?.find('svg.tabler-icon-check')
        .exists() === true

    expect(isChecked('Newest')).toBe(true)
    expect(isChecked('Saved')).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')

    /* 세 항목은 배타적이다. 체크는 정확히 Saved 하나에만 남는다. */
    expect(isChecked('Saved')).toBe(true)
    expect(isChecked('Newest')).toBe(false)
    expect(wrapper.findAll('svg.tabler-icon-check')).toHaveLength(1)
  })

  it('uses the same All of Seoul and Other areas behavior as Event', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'NEWEST' }, resultCount: 3 },
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
      props: { kind: 'options', filters: { sort: 'NEWEST' }, resultCount: 3 },
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
