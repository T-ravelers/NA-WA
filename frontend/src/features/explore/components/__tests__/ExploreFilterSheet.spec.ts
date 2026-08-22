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

  it('builds an Opening soon range from its first selectable day forward', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'Opening soon')
    /* 시작일을 먼저 누르는 순서. 끝날부터 누르는 순서만 되면 안 된다. */
    await lastButton(wrapper, '21')?.trigger('click')
    await lastButton(wrapper, '25')?.trigger('click')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      datePreset: 'OPENING_SOON',
      startDate: '2026-08-21',
      endDate: '2026-08-25',
    })
  })

  it('builds the same Opening soon range when the later day is tapped first', async () => {
    const wrapper = mountDateSheet()

    await pressButton(wrapper, 'Opening soon')
    await lastButton(wrapper, '25')?.trigger('click')
    await lastButton(wrapper, '21')?.trigger('click')
    await pressApply(wrapper)

    expect(lastApplied(wrapper)).toMatchObject({
      datePreset: 'OPENING_SOON',
      startDate: '2026-08-21',
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

  it('keeps a lone start date as a single dot, not a band', async () => {
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

    // 하루는 칸을 채우지 않는다. 칸을 채우면 하루가 기간처럼 읽힌다.
    expect(day21?.classes()).toContain('mx-auto')
    expect(day21?.classes()).toContain('w-9')
    expect(day21?.classes()).not.toContain('w-full')
  })

  it('keeps a closed single-day range as the same dot', async () => {
    const wrapper = mountDateSheet()

    // 같은 날을 두 번 탭하면 시작·종료가 같은 하루짜리 기간이 된다. 시작일만
    // 고른 상태와 같은 하루이므로 같은 모양이어야 한다.
    await pressButton(wrapper, '25')
    await pressButton(wrapper, '25')

    const day25 = wrapper.findAll('button').find((button) => button.text() === '25')
    expect(day25?.classes()).toContain('bg-paper-fill')
    expect(day25?.classes()).toContain('rounded-pill')
    expect(day25?.classes()).toContain('mx-auto')
    expect(day25?.classes()).toContain('w-9')
    expect(day25?.classes()).not.toContain('w-full')
    expect(day25?.classes()).not.toContain('rounded-l-pill')
    expect(day25?.classes()).not.toContain('rounded-r-pill')
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

const FOOD_ACTIVITY_LABELS = [
  'Cafe / Dessert',
  'Restaurant',
  'Tourist Restaurant',
  'Street Food',
  'Bar / Liquor',
  'Tea House',
  'Snack',
  'Food Festival',
]

describe('ExploreFilterSheet', () => {
  function mountCategorySheet() {
    return mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'NEWEST' }, resultCount: 3 },
    })
  }

  function sectorHeader(wrapper: VueWrapper, label: string) {
    return wrapper.findAll('button').find((button) => button.text().includes(label))
  }

  function checkSector(wrapper: VueWrapper, label: string) {
    return sectorHeader(wrapper, label)?.find('[role="checkbox"]').trigger('click')
  }

  /** 접혀 있는 대분류의 소분류 칩은 아예 그려지지 않는다. 눌러 보려면 먼저 펼쳐야 한다. */
  function expandSector(wrapper: VueWrapper, label: string) {
    return sectorHeader(wrapper, label)?.trigger('click')
  }

  function isSectorChecked(wrapper: VueWrapper, label: string): boolean {
    return (
      sectorHeader(wrapper, label)?.find('[role="checkbox"]').attributes('aria-checked') === 'true'
    )
  }

  function pressButton(wrapper: VueWrapper, text: string) {
    return wrapper
      .findAll('button')
      .find((button) => button.text() === text)
      ?.trigger('click')
  }

  /** 소분류 칩은 선택되면 채워진 배경으로 바뀐다. */
  function isActivityChecked(wrapper: VueWrapper, label: string): boolean {
    return (
      wrapper
        .findAll('button')
        .find((button) => button.text() === label)
        ?.classes()
        .includes('bg-paper-fill') === true
    )
  }

  function lastChange(wrapper: VueWrapper): Record<string, unknown> {
    const changes = wrapper.emitted('change') ?? []

    return changes[changes.length - 1]?.[0] as Record<string, unknown>
  }

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

  it('turns Saved off when a sort is picked', async () => {
    const wrapper = mount(ExploreFilterSheet, {
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

    /* 네 항목은 배타적이다. 정렬을 고르면 Saved는 꺼진다. */
    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({
      sort: 'POPULAR',
      savedOnly: undefined,
    })
  })

  it('keeps the current sort while Saved is the checked item', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'ENDING_SOON' }, resultCount: 3 },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')

    /* 체크만 Saved로 옮겨간다. 목록 순서는 직전 정렬을 그대로 쓴다. */
    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({
      sort: 'ENDING_SOON',
      savedOnly: true,
    })
  })

  it('moves the single check onto Saved instead of leaving it on the sort', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'sort', filters: { sort: 'POPULAR' }, resultCount: 3 },
    })

    /** 그 이름의 줄에 체크가 붙어 있는가. 개수만 세면 체크가 엉뚱한 줄에 남아도 통과한다. */
    const isChecked = (label: string) =>
      wrapper
        .findAll('button')
        .find((button) => button.text().startsWith(label))
        ?.find('svg.tabler-icon-check')
        .exists() === true

    expect(isChecked('Popular')).toBe(true)
    expect(isChecked('Saved')).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saved')
      ?.trigger('click')

    /* 네 항목은 배타적이다. 체크는 정확히 Saved 하나에만 남는다. */
    expect(isChecked('Saved')).toBe(true)
    expect(isChecked('Popular')).toBe(false)
    expect(wrapper.findAll('svg.tabler-icon-check')).toHaveLength(1)
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

    /* 소분류가 전부 켜져도 서버로는 대분류 하나로 접어 보낸다. */
    expect(lastChange(wrapper)).toMatchObject({ sectorIds: [2], activityIds: undefined })

    await foodHeader?.trigger('click')
    await pressButton(wrapper, 'Cafe / Dessert')

    /* 대분류를 켜면 소분류도 켜져 있으므로, 한 번 누르면 그 소분류만 꺼진다. */
    expect(lastChange(wrapper)).toMatchObject({
      sectorIds: undefined,
      activityIds: [10, 11, 12, 13, 14, 15, 16],
    })
  })

  it('shows the activities as checked when the sheet opens on a remembered sector', async () => {
    const wrapper = mount(ExploreFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'NEWEST', sectorIds: [2] }, resultCount: 3 },
    })

    await expandSector(wrapper, 'Food')

    /*
     * 주소에는 대분류가 ID 하나로 실려 온다(`eventSectorIds=2`). 그것을 소분류로 펼쳐 두지
     * 않으면 대분류만 체크로 보이고 아래 소분류 칩은 전부 꺼진 채로 그려진다.
     */
    expect(isSectorChecked(wrapper, 'Food')).toBe(true)
    expect(FOOD_ACTIVITY_LABELS.every((label) => isActivityChecked(wrapper, label))).toBe(true)
  })

  it('opens with every sector collapsed', () => {
    const wrapper = mountCategorySheet()

    /* 하나만 펼쳐 두면 그 대분류만 있는 것처럼 보이고 나머지를 못 찾는다. */
    expect(isActivityChecked(wrapper, 'Makeup / Cosmetics')).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text() === 'Skincare')).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text() === 'Cafe / Dessert')).toBe(
      false,
    )
  })

  it('writes how many activities are chosen next to the sector name', async () => {
    const wrapper = mountCategorySheet()

    await expandSector(wrapper, 'Food')
    await pressButton(wrapper, 'Cafe / Dessert')
    await pressButton(wrapper, 'Restaurant')

    /* 접어 두면 무엇을 골랐는지 안 보이므로 헤더에 개수를 남긴다. */
    expect(sectorHeader(wrapper, 'Food')?.text()).toContain('· 2')

    await checkSector(wrapper, 'Food')

    /* 전부 고른 상태에서도 개수는 그대로 보인다. */
    expect(sectorHeader(wrapper, 'Food')?.text()).toContain('· 8')
  })

  it('leaves the sector name alone when nothing under it is chosen', () => {
    const wrapper = mountCategorySheet()

    expect(sectorHeader(wrapper, 'Food')?.text()).not.toContain('·')
  })

  it('checks every activity under a sector when the sector is checked', async () => {
    const wrapper = mountCategorySheet()

    await checkSector(wrapper, 'Food')
    await expandSector(wrapper, 'Food')

    expect(isSectorChecked(wrapper, 'Food')).toBe(true)
    expect(FOOD_ACTIVITY_LABELS.every((label) => isActivityChecked(wrapper, label))).toBe(true)
  })

  it('clears every activity under a sector when the sector is unchecked', async () => {
    const wrapper = mountCategorySheet()

    await checkSector(wrapper, 'Food')
    await checkSector(wrapper, 'Food')
    await expandSector(wrapper, 'Food')

    expect(isSectorChecked(wrapper, 'Food')).toBe(false)
    expect(FOOD_ACTIVITY_LABELS.some((label) => isActivityChecked(wrapper, label))).toBe(false)
    expect(lastChange(wrapper)).toMatchObject({ sectorIds: undefined, activityIds: undefined })
  })

  it('keeps the remaining activities checked when the sector loses one', async () => {
    const wrapper = mountCategorySheet()

    await checkSector(wrapper, 'Food')
    await expandSector(wrapper, 'Food')
    await pressButton(wrapper, 'Cafe / Dessert')

    /* 대분류만 풀리고 나머지 소분류 체크는 그대로 남는다. */
    expect(isSectorChecked(wrapper, 'Food')).toBe(false)
    expect(isActivityChecked(wrapper, 'Cafe / Dessert')).toBe(false)
    expect(isActivityChecked(wrapper, 'Restaurant')).toBe(true)
  })

  it('checks the sector once its last activity is checked', async () => {
    const wrapper = mountCategorySheet()

    await expandSector(wrapper, 'Food')
    for (const label of FOOD_ACTIVITY_LABELS) {
      await pressButton(wrapper, label)
    }

    expect(isSectorChecked(wrapper, 'Food')).toBe(true)
    /* 하나씩 눌러 채운 것도 대분류를 통째로 고른 것과 같은 요청이 된다. */
    expect(lastChange(wrapper)).toMatchObject({ sectorIds: [2], activityIds: undefined })
  })
})
