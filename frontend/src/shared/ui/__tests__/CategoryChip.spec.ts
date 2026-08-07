import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CategoryChip from '../CategoryChip.vue'

function mountChip(props: Record<string, unknown> = {}) {
  return mount(CategoryChip, { props: { category: 'food', label: 'Food', ...props } })
}

describe('CategoryChip', () => {
  /*
   * 색만으로 영역을 말하면 색각 이상 사용자가 읽을 수 없다. 라벨은 어느 용도에서도
   * 생략되지 않아야 한다.
   */
  it('always shows a text label', () => {
    expect(mountChip().text()).toBe('Food')
    expect(mountChip({ interactive: true }).text()).toBe('Food')
  })

  it('renders a static label as a non-interactive element', () => {
    const wrapper = mountChip()

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.get('span').classes()).toContain('bg-food')
  })

  it('renders a toggle with its pressed state', async () => {
    const wrapper = mountChip({ interactive: true, selected: false })

    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  it('fills with the category core colour only when selected', () => {
    expect(mountChip({ interactive: true, selected: true }).get('button').classes()).toContain(
      'bg-food',
    )
    expect(mountChip({ interactive: true, selected: false }).get('button').classes()).toContain(
      'border-hairline',
    )
  })

  it.each([
    ['beauty', 'bg-beauty'],
    ['shopping', 'bg-shopping'],
    ['show', 'bg-show'],
    ['food', 'bg-food'],
  ] as const)('codes %s with its own colour', (category, expected) => {
    expect(mountChip({ category }).get('span').classes()).toContain(expected)
  })
})
