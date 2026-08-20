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

  // 같은 숫자가 이웃 달 셀에도 있을 때는 그리드에서 나중에 오는(이번 달) 쪽.
  function lastButton(wrapper: VueWrapper, text: string) {
    const matches = wrapper.findAll('button').filter((button) => button.text() === text)
    return matches[matches.length - 1]
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

  it('closes a single day narrowed inside Opening soon to a one-day range', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'Opening soon')
    await pressButton(wrapper, '25')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      datePreset: 'OPENING_SOON',
      startDate: '2026-08-25',
      endDate: '2026-08-25',
    })
  })

  it('closes a plain single-day pick to a one-day range', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, '25')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      startDate: '2026-08-25',
      endDate: '2026-08-25',
    })
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

  it('paints the selected range as one continuous band', async () => {
    const wrapper = mountDateSheet()

    // 21(금)~25(화)는 주말을 사이에 두고 두 줄에 걸친다. 기간 양 끝, 줄 넘김
    // 경계, 줄 한가운데가 모두 이 범위 안에 있다.
    await pressButton(wrapper, '21')
    await pressButton(wrapper, '25')

    const days = ['21', '22', '23', '24', '25'].map((text) =>
      wrapper.findAll('button').find((button) => button.text() === text),
    )
    const [day21, day22, day23, day24, day25] = days

    // 칸 전체를 채워야 날짜끼리 이어져 하나의 기간으로 읽힌다. 칸보다 좁은 칩을
    // 가운데 두면 흰색이어도 끊겨 보인다.
    for (const day of days) {
      expect(day?.classes()).toContain('bg-paper-fill')
      expect(day?.classes()).toContain('w-full')
      expect(day?.classes()).not.toContain('mx-auto')
    }

    // 띠가 시작·끝나는 자리만 둥글고 사이는 각지다. 21은 기간 시작, 22(토)는
    // 줄의 끝, 23(일)은 새 줄의 시작, 25는 기간 끝이라 깎이고, 한가운데의
    // 24는 각지다.
    expect(day21?.classes()).toContain('rounded-l-pill')
    expect(day21?.classes()).not.toContain('rounded-r-pill')
    expect(day22?.classes()).not.toContain('rounded-l-pill')
    expect(day22?.classes()).toContain('rounded-r-pill')
    expect(day23?.classes()).toContain('rounded-l-pill')
    expect(day23?.classes()).not.toContain('rounded-r-pill')
    expect(day24?.classes()).not.toContain('rounded-l-pill')
    expect(day24?.classes()).not.toContain('rounded-r-pill')
    expect(day25?.classes()).toContain('rounded-r-pill')
    expect(day25?.classes()).not.toContain('rounded-l-pill')
    expect(wrapper.html()).not.toContain('bg-surface-2')
  })

  it('keeps a lone start date visible as a single full-width pill', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, '21')

    const day21 = wrapper.findAll('button').find((button) => button.text() === '21')
    const day22 = wrapper.findAll('button').find((button) => button.text() === '22')
    // 시작일만 고른 상태에서도 달력에 선택이 보여야 한다. 배경 없이 모서리만
    // 깎으면 화면에는 아무것도 그려지지 않아, 미선택 셀과 구분해 단언한다.
    expect(day21?.classes()).toContain('bg-paper-fill')
    expect(day21?.classes()).toContain('rounded-pill')
    expect(day21?.classes()).not.toContain('rounded-l-pill')
    expect(day22?.classes()).not.toContain('bg-paper-fill')
  })

  it('keeps adjacent-month cells out of the band even when their date is in range', async () => {
    const wrapper = mountDateSheet()

    // 30은 이웃 달 셀(7월 30일)이 먼저 걸리므로 마지막 일치(8월 30일)를 누른다.
    await lastButton(wrapper, '30')?.trigger('click')
    await wrapper.get('button[aria-label="Next month"]').trigger('click')
    await pressButton(wrapper, '2')
    await wrapper.get('button[aria-label="Previous month"]').trigger('click')

    // 기간은 8/30~9/2. 8월 그리드 마지막 줄의 9월 1일은 날짜로는 기간에 들지만
    // 이웃 달 셀이라 띠를 그리지 않는다 — 흐린 글자 위에 흰 배경이 얹히면
    // 선택할 수 없는 날짜가 오히려 도드라진다.
    const day31 = lastButton(wrapper, '31')
    const trailing1 = lastButton(wrapper, '1')
    expect(day31?.classes()).toContain('bg-paper-fill')
    // 띠가 이웃 달 경계에서 잘리므로 8월 마지막 날이 오른쪽 끝으로 깎인다.
    expect(day31?.classes()).toContain('rounded-r-pill')
    expect(trailing1?.classes()).not.toContain('bg-paper-fill')
    expect(trailing1?.classes()).toContain('text-ink-3/40')
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
  it('applies the Saved toggle now that the like API is wired', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({ savedOnly: true })
  })

  it('clears the Saved toggle when the sort sheet is reset', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'NEWEST', savedOnly: true }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Reset')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    const applied = wrapper.emitted('apply')?.[0]?.[0] as { savedOnly?: boolean }
    expect(applied.savedOnly).toBeUndefined()
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
