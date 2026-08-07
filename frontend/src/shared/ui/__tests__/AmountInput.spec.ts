import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AmountInput from '../AmountInput.vue'

function mountAmount(props: Record<string, unknown> = {}) {
  return mount(AmountInput, { props: { modelValue: null, label: 'Budget', ...props } })
}

describe('AmountInput', () => {
  it('connects the label to the input', () => {
    const wrapper = mountAmount()

    expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('input').attributes('id'))
  })

  it('shows a thousands separator', () => {
    expect(mountAmount({ modelValue: 1250000 }).get('input').element.value).toBe('1,250,000')
  })

  it('emits a number, not the formatted string', async () => {
    const wrapper = mountAmount()

    await wrapper.get('input').setValue('1,250,000')

    expect(wrapper.emitted('update:modelValue')).toEqual([[1250000]])
  })

  it('drops characters that are not digits', async () => {
    const wrapper = mountAmount()

    await wrapper.get('input').setValue('₩12a3')

    expect(wrapper.emitted('update:modelValue')).toEqual([[123]])
  })

  /*
   * 빈 값은 0이 아니라 null이다. 정산에서 "0원을 썼다"와 "아직 입력하지 않았다"는
   * 다른 뜻이고, 이것을 섞으면 예산 게이지와 분배 계산이 잘못된다.
   */
  it('reports an empty input as null', async () => {
    const wrapper = mountAmount({ modelValue: 500 })

    await wrapper.get('input').setValue('')

    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })

  it('keeps the currency symbol out of the value', () => {
    const wrapper = mountAmount({ modelValue: 500, currencySymbol: '$' })

    expect(wrapper.get('input').element.value).toBe('500')
    expect(wrapper.get('span[aria-hidden="true"]').text()).toBe('$')
  })
})
