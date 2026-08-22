import { mount, type VueWrapper } from '@vue/test-utils'
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

  /*
   * 카테고리 체크박스는 Event 시트와 같은 규칙이다. 두 화면이 갈라지면 사용자는 같은
   * 분류를 두 가지 방식으로 다뤄야 하므로, 규칙을 양쪽에서 따로 고정한다.
   */
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

  function mountCategorySheet() {
    return mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'POPULAR' }, resultCount: 3 },
    })
  }

  function sectorHeader(wrapper: VueWrapper, label: string) {
    return wrapper.findAll('button').find((button) => button.text().includes(label))
  }

  function checkSector(wrapper: VueWrapper, label: string) {
    return sectorHeader(wrapper, label)?.find('[role="checkbox"]').trigger('click')
  }

  /** 접혀 있는 대분류의 소분류 칩은 그려지지 않는다. 눌러 보려면 먼저 펼쳐야 한다. */
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

  it('shows the activities as checked when the sheet opens on a remembered sector', async () => {
    const wrapper = mount(PlaceFilterSheet, {
      global: { plugins: [i18n] },
      props: { kind: 'category', filters: { sort: 'POPULAR', sectorIds: [2] }, resultCount: 3 },
    })

    await expandSector(wrapper, 'Food')

    /*
     * 주소에는 대분류가 ID 하나로 실려 온다(`placeSectorIds=2`). 그것을 소분류로 펼쳐 두지
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
    /* 소분류가 전부 켜져도 서버로는 대분류 하나로 접어 보낸다. */
    expect(lastChange(wrapper)).toMatchObject({ sectorIds: [2], activityIds: undefined })
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
    expect(lastChange(wrapper)).toMatchObject({
      sectorIds: undefined,
      activityIds: [10, 11, 12, 13, 14, 15, 16],
    })
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
