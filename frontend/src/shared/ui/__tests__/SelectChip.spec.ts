import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SelectChip from '../SelectChip.vue'

function mountChip(props: Record<string, unknown> = {}) {
  return mount(SelectChip, { props: { label: 'Food', ...props } })
}

describe('SelectChip', () => {
  it('renders a static label when it is not interactive', () => {
    const wrapper = mountChip()

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.get('span').text()).toBe('Food')
  })

  it('renders a button and emits toggle when interactive', async () => {
    const wrapper = mountChip({ interactive: true })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  /*
   * 선택 의미는 부르는 쪽이 정한다. 토글(`aria-pressed`)과 라디오(`role`+`aria-checked`)는
   * 접근성 트리에서 다른 것이라, 칩이 하나로 정해 버리면 둘 중 하나가 틀린다.
   */
  it('lets the caller own the selection semantics', () => {
    const toggle = mountChip({ interactive: true, 'aria-pressed': 'true' })
    const radio = mountChip({ interactive: true, role: 'radio', 'aria-checked': 'true' })

    expect(toggle.get('button').attributes('aria-pressed')).toBe('true')
    expect(radio.get('button').attributes('role')).toBe('radio')
    expect(radio.get('button').attributes('aria-checked')).toBe('true')
  })

  it('fills the surface only when selected', () => {
    expect(mountChip({ interactive: true, selected: true }).get('button').classes()).toContain(
      'bg-paper-fill',
    )
    expect(mountChip({ interactive: true, selected: false }).get('button').classes()).toContain(
      'border-hairline',
    )
  })

  it.each([
    ['paper', 'bg-paper-fill'],
    ['beauty', 'bg-beauty'],
    ['shopping', 'bg-shopping'],
    ['show', 'bg-show'],
    ['food', 'bg-food'],
  ] as const)('fills with %s', (fill, expected) => {
    expect(
      mountChip({ fill, selected: true, interactive: true }).get('button').classes(),
    ).toContain(expected)
  })

  /*
   * 세그먼트는 트랙(`surface-2`) 위에 얹혀 면이 이미 구분된다. 칩과 같은 테두리를
   * 두르면 트랙 안에 칸막이가 하나 더 생긴 것처럼 보인다.
   */
  it('drops the outline for segments but keeps it for chips', () => {
    const segment = mountChip({ interactive: true, size: 'segment' }).get('button')
    const chip = mountChip({ interactive: true, size: 'md' }).get('button')

    expect(segment.classes()).not.toContain('border-hairline')
    expect(segment.classes()).toContain('flex-1')
    expect(chip.classes()).toContain('border-hairline')
  })

  it('uses the compact height inside tickets', () => {
    expect(mountChip({ size: 'sm' }).get('span').classes()).toContain('h-6')
    expect(mountChip({ size: 'md' }).get('span').classes()).toContain('h-9')
  })
})
