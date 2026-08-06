import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CategoryDot from '../CategoryDot.vue'

describe('CategoryDot', () => {
  it.each([
    ['beauty', 'bg-beauty'],
    ['shopping', 'bg-shopping'],
    ['show', 'bg-show'],
    ['food', 'bg-food'],
  ] as const)('codes %s with its own colour', (category, expected) => {
    const wrapper = mount(CategoryDot, { props: { category } })

    expect(wrapper.get('span').classes()).toContain(expected)
  })

  /*
   * 색만으로는 영역을 구분할 수 없다. 화면에서 항상 라벨과 함께 쓰므로 점 자체는
   * 접근성 트리에서 감춰 같은 정보가 두 번 읽히지 않게 한다.
   */
  it('is hidden from assistive technology', () => {
    const wrapper = mount(CategoryDot, { props: { category: 'food' } })

    expect(wrapper.get('span').attributes('aria-hidden')).toBe('true')
  })
})
