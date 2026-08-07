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
  ] as const)('draws the %s variant from tokens', (variant, expected) => {
    expect(mountButton({ variant }).get('button').classes()).toContain(expected)
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
