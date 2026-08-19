import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import ExploreFilterSheet from '../ExploreFilterSheet.vue'

describe('ExploreFilterSheet date presets', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // 2026-08-20 목요일 — 이번 주말은 22(토)·23(일), 말일은 31일.
    vi.setSystemTime(new Date(2026, 7, 20))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function mountDateSheet() {
    return mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'date', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })
  }

  function pressButton(wrapper: VueWrapper, text: string) {
    return wrapper
      .findAll('button')
      .find((button) => button.text() === text)
      ?.trigger('click')
  }

  function pressApply(wrapper: VueWrapper) {
    return wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')
  }

  function lastApplied(wrapper: VueWrapper): Record<string, unknown> {
    const events = wrapper.emitted('apply')
    return events?.[events.length - 1]?.[0] as Record<string, unknown>
  }

  it('applies the whole preset range when only the preset is chosen', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'This weekend')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      datePreset: 'THIS_WEEKEND',
      startDate: '2026-08-22',
      endDate: '2026-08-23',
    })
  })

  it('keeps an Opening soon selection open ended', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'Opening soon')
    await pressApply(wrapper)

    const applied = lastApplied(wrapper)
    expect(applied.datePreset).toBe('OPENING_SOON')
    expect(applied.startDate).toBe('2026-08-21')
    expect(applied.endDate).toBeUndefined()
  })

  it('narrows dates inside the preset range instead of clearing the preset', async () => {
    const wrapper = mountDateSheet()

    // 24·25는 달력 그리드의 앞뒤 이웃 달 셀(7월 26~31, 9월 1~5)과 겹치지 않는
    // 날짜라 텍스트로 유일하게 찾을 수 있다.
    await pressButton(wrapper, 'This month')
    await pressButton(wrapper, '24')
    await pressButton(wrapper, '25')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      datePreset: 'THIS_MONTH',
      startDate: '2026-08-24',
      endDate: '2026-08-25',
    })
  })

  it('disables days outside the preset range', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'This weekend')

    const day21 = wrapper.findAll('button').find((button) => button.text() === '21')
    const day22 = wrapper.findAll('button').find((button) => button.text() === '22')
    expect(day21?.attributes('disabled')).toBeDefined()
    expect(day22?.attributes('disabled')).toBeUndefined()
  })

  it('paints the whole selected range white and keeps the endpoints as pills', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, '21')
    await pressButton(wrapper, '23')

    const day21 = wrapper.findAll('button').find((button) => button.text() === '21')
    const day22 = wrapper.findAll('button').find((button) => button.text() === '22')
    const day23 = wrapper.findAll('button').find((button) => button.text() === '23')
    expect(day21?.classes()).toContain('bg-paper-fill')
    expect(day21?.classes()).not.toContain('rounded-none')
    expect(day22?.classes()).toContain('bg-paper-fill')
    expect(day22?.classes()).toContain('rounded-none')
    expect(day23?.classes()).toContain('bg-paper-fill')
    expect(day23?.classes()).not.toContain('rounded-none')
    expect(wrapper.html()).not.toContain('bg-surface-2')
  })

  it('disables past days when no preset is chosen', () => {
    const wrapper = mountDateSheet()

    const day19 = wrapper.findAll('button').find((button) => button.text() === '19')
    const day20 = wrapper.findAll('button').find((button) => button.text() === '20')
    expect(day19?.attributes('disabled')).toBeDefined()
    expect(day20?.attributes('disabled')).toBeUndefined()
  })
})

describe('ExploreFilterSheet', () => {
  it('does not expose saved sorting before the saved API is connected', () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    expect(wrapper.findAll('button').some((button) => button.text() === 'Saved')).toBe(false)
  })

  it('emits close when the scrim is pressed', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'options', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    await wrapper.get('button[aria-label="Close filter sheet"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('keeps option changes local until Apply is pressed', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'options', filters: { sort: 'NEWEST' }, resultCount: 3 },
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

  it('only exposes Seoul and sends operational_v9 region values', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    expect(wrapper.text()).toContain('Seoul')
    expect(wrapper.text()).not.toContain('Gyeonggi')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seongsu'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[wrapper.emitted('apply')!.length - 1]?.[0]).toMatchObject({
      region1: ['서울'],
      region2: ['성수'],
    })
  })

  it('can select unclassified region2 values as other areas', async () => {
    const wrapper = mount(ExploreFilterSheet, {
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

    expect(wrapper.emitted('apply')?.[wrapper.emitted('apply')!.length - 1]?.[0]).toMatchObject({
      region1: ['서울'],
      region2Other: true,
    })
  })

  it('can combine a named subregion with other areas', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'region', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seongsu'))
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
      region1: ['서울'],
      region2: ['성수'],
      region2Other: true,
    })
  })

  it('selects a sector and its activities using operational_v9 IDs', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    const foodHeader = wrapper.findAll('button').find((button) => button.text().includes('Food'))
    await foodHeader?.find('[role="checkbox"]').trigger('click')

    expect(wrapper.emitted('change')?.[wrapper.emitted('change')!.length - 1]?.[0]).toMatchObject({
      sectorIds: [2],
    })

    await foodHeader?.trigger('click')
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
