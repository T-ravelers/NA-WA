import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppButton from '../AppButton.vue'

function mountButton(props: Record<string, unknown> = {}) {
  return mount(AppButton, {
    props,
    slots: { default: 'Continue' },
    global: { plugins: [i18n] },
  })
}

describe('AppButton', () => {
  // 폼 안에 놓였을 때 의도치 않은 submit이 일어나지 않아야 한다.
  it('defaults to a non-submitting button', () => {
    expect(mountButton().get('button').attributes('type')).toBe('button')
  })

  it.each([
    ['primary', 'bg-paper-fill'],
    ['secondary', 'border-hairline-strong'],
    ['settle', 'bg-settlement'],
    ['destructive', 'bg-danger'],
  ] as const)('draws the %s variant from tokens', (variant, expected) => {
    expect(mountButton({ variant }).get('button').classes()).toContain(expected)
  })

  it('uses readable dark text on the destructive surface', () => {
    const classes = mountButton({ variant: 'destructive' }).get('button').classes()

    expect(classes).toContain('text-on-category')
  })

  // paper 카드 위(밝은 바탕)의 보조 버튼은 secondary의 text-ink(밝은 캔버스 기준
  // 흰 글자) 대신 어두운 text-on-paper를 써야 읽힌다.
  it('draws the secondary-on-paper variant with paper-safe text color', () => {
    const classes = mountButton({ variant: 'secondary-on-paper' }).get('button').classes()

    expect(classes).toContain('text-on-paper')
    expect(classes).not.toContain('text-ink')
  })

  it('keeps the minimum touch target for dense buttons', () => {
    expect(mountButton({ dense: true }).get('button').classes()).toContain('h-11')
  })

  it('emits click when enabled', async () => {
    const wrapper = mountButton()

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('does not emit click when disabled', async () => {
    const wrapper = mountButton({ disabled: true })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('click')).toBeUndefined()
  })

  /*
   * 로딩 중 연타는 같은 요청을 두 번 보낸다. 정산·여정 생성에서 실제로 문제가 된다.
   */
  it('does not emit click while loading', async () => {
    const wrapper = mountButton({ loading: true })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('keeps its accessible name while loading', () => {
    const wrapper = mountButton({ loading: true })

    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('button').text()).toContain('Continue')
    expect(wrapper.get('span').classes()).toContain('sr-only')
  })
})
