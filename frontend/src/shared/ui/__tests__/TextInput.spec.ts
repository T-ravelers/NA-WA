import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TextInput from '../TextInput.vue'

function mountInput(props: Record<string, unknown> = {}) {
  return mount(TextInput, { props: { modelValue: '', label: 'Journey name', ...props } })
}

describe('TextInput', () => {
  it('connects the label to the input', () => {
    const wrapper = mountInput()
    const id = wrapper.get('input').attributes('id')

    expect(id).toBeDefined()
    expect(wrapper.get('label').attributes('for')).toBe(id)
    expect(wrapper.get('label').text()).toBe('Journey name')
  })

  it('keeps the label for assistive technology when hidden', () => {
    const wrapper = mountInput({ labelHidden: true })

    expect(wrapper.get('label').classes()).toContain('sr-only')
    expect(wrapper.get('label').text()).toBe('Journey name')
  })

  it('emits the typed value', async () => {
    const wrapper = mountInput()

    await wrapper.get('input').setValue('Seoul')

    expect(wrapper.emitted('update:modelValue')).toEqual([['Seoul']])
  })

  it('forwards native date constraints without losing the generated label connection', () => {
    const wrapper = mountInput({ type: 'date', min: '2026-08-10' })
    const input = wrapper.get('input')

    expect(input.attributes('type')).toBe('date')
    expect(input.attributes('min')).toBe('2026-08-10')
    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
  })

  /*
   * 오류를 빨간 테두리로만 말하면 색각 이상 사용자와 스크린 리더 사용자가 무엇이
   * 잘못됐는지 알 수 없다. 문구가 입력과 연결돼 있어야 한다.
   */
  it('announces an error through the input', () => {
    const wrapper = mountInput({ error: 'Enter a name' })
    const input = wrapper.get('input')

    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${input.attributes('aria-describedby')}`).text()).toBe('Enter a name')
  })

  it('prefers the error over the helper text', () => {
    const wrapper = mountInput({ helper: 'Up to 30 characters', error: 'Enter a name' })

    expect(wrapper.get('p').text()).toBe('Enter a name')
  })

  it('is not marked invalid without an error', () => {
    expect(mountInput().get('input').attributes('aria-invalid')).toBe('false')
  })
})
