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

  it('hides the scrollbar on both horizontal filter rows', () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })
    const rows = wrapper.findAll('.overflow-x-auto')

    expect(rows).toHaveLength(2)
    for (const row of rows) {
      expect(row.classes()).toContain('scrollbar-hidden')
    }
  })

  it('emits an event kind toggle for the quick chips', async () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })

    await wrapper.findAll('button')[4]?.trigger('click')

    expect(wrapper.emitted('toggleKind')).toEqual([['POPUP']])
  })

  /** 그 버튼이 켜져 있는가. 켜지면 채워진 배경으로 바뀐다. */
  function isButtonActive(wrapper: ReturnType<typeof mount>, label: string): boolean {
    return (
      wrapper
        .findAll('button')
        .find((button) => button.text().startsWith(label))
        ?.classes()
        .includes('bg-paper-fill') === true
    )
  }

  it('lights up the button that owns each active filter', () => {
    const wrapper = mount(ExploreFilterBar, {
      global: { plugins: [i18n] },
      props: {
        ...props,
        activeFilters: [
          { key: 'date:preset', label: 'This weekend' },
          { key: 'region2:Seongsu', label: 'Seongsu' },
          { key: 'sector:2', label: 'Food' },
          { key: 'option:freeOnly', label: 'Free' },
        ],
      },
    })

    /*
     * 칩 key는 시트 이름이 아니라 필터 이름이다. 버튼 이름으로 startsWith를 하면
     * category가 sector:·activity:를, options가 option:을 놓쳐 영영 켜지지 않는다.
     */
    expect(isButtonActive(wrapper, 'Date')).toBe(true)
    expect(isButtonActive(wrapper, 'Region')).toBe(true)
    expect(isButtonActive(wrapper, 'Category')).toBe(true)
    expect(isButtonActive(wrapper, 'Options')).toBe(true)
  })

  it('counts only the filters that belong to each button', () => {
    const wrapper = mount(ExploreFilterBar, {
      global: { plugins: [i18n] },
      props: {
        ...props,
        activeFilters: [
          { key: 'sector:2', label: 'Food' },
          { key: 'activity:9', label: 'Cafe / Dessert' },
          { key: 'option:freeOnly', label: 'Free' },
        ],
      },
    })

    const categoryButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Category'))

    expect(categoryButton?.text()).toContain('· 2')
  })

  it('leaves a button dark when nothing under it is selected', () => {
    const wrapper = mount(ExploreFilterBar, {
      global: { plugins: [i18n] },
      props: { ...props, activeFilters: [{ key: 'sector:2', label: 'Food' }] },
    })

    expect(isButtonActive(wrapper, 'Category')).toBe(true)
    expect(isButtonActive(wrapper, 'Options')).toBe(false)
    expect(isButtonActive(wrapper, 'Date')).toBe(false)
  })

  it('shows the global reset when only an event kind is selected', () => {
    const wrapper = mount(ExploreFilterBar, { global: { plugins: [i18n] }, props })

    expect(wrapper.findAll('button').some((button) => button.text() === 'Reset')).toBe(true)
  })
})
