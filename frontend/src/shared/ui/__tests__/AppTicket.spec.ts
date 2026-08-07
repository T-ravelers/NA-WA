import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppTicket from '../AppTicket.vue'

function mountTicket(props: Record<string, unknown> = {}) {
  return mount(AppTicket, {
    props: { bodySize: 154, ...props },
    slots: { body: '<img alt="Seoul" />', stub: '<p>Seoul trip</p>' },
  })
}

describe('AppTicket', () => {
  it('renders both sections', () => {
    const wrapper = mountTicket()

    expect(wrapper.find('img').exists()).toBe(true)
    expect(wrapper.text()).toContain('Seoul trip')
  })

  /*
   * 노치는 배경색 원을 덧대는 것이 아니라 실제로 파낸 구멍이어야 한다. 그래야 티켓이
   * 사진이나 다른 면 위에 놓여도 어긋나지 않는다.
   */
  it('cuts the notches out with a mask instead of painting over them', () => {
    const style = mountTicket().get('div').attributes('style') ?? ''

    expect(style).toContain('mask-image')
    expect(style).toContain('transparent')
    expect(style).toContain('intersect')
  })

  it('places the notches on the split line', () => {
    const style = mountTicket({ bodySize: 154, notchSize: 20 }).get('div').attributes('style') ?? ''

    // 반지름 10px 원이 절취선(154px) 좌우 끝에 하나씩 놓인다.
    expect(style).toContain('circle 10px at 0 154px')
    expect(style).toContain('circle 10px at 100% 154px')
  })

  it('turns the split axis for a horizontal ticket', () => {
    const style =
      mountTicket({ orientation: 'horizontal', bodySize: 88, notchSize: 12 })
        .get('div')
        .attributes('style') ?? ''

    expect(style).toContain('circle 6px at 88px 0')
    expect(style).toContain('circle 6px at 88px 100%')
  })

  it.each([
    ['paper', 'bg-paper'],
    ['dark', 'bg-surface-1'],
    ['food', 'bg-food'],
  ] as const)('draws the %s tone from tokens', (tone, expected) => {
    expect(mountTicket({ tone }).get('div').classes()).toContain(expected)
  })

  it('rings the ticket when selected', () => {
    const style = mountTicket({ selected: true }).get('div').attributes('style') ?? ''

    expect(style).toContain('inset 0 0 0 2px')
  })

  // 절취선은 조형이지 정보가 아니다. 스크린 리더가 읽을 것이 없어야 한다.
  it('hides the perforation from assistive technology', () => {
    const perforation = mountTicket().get('[aria-hidden="true"]')

    expect(perforation.attributes('style')).toContain('dashed')
  })
})
