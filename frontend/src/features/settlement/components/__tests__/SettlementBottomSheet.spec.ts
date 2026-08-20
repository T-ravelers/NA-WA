import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'

import SettlementBottomSheet from '../SettlementBottomSheet.vue'

afterEach(() => {
  document.body.innerHTML = ''
})

describe('SettlementBottomSheet', () => {
  it('Escape로 닫기를 요청하고 해제될 때 이전 포커스를 복원한다', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()

    const wrapper = mount(SettlementBottomSheet, {
      props: { label: 'Receipt scanner' },
      slots: { default: '<button type="button">Analyze receipt</button>' },
      attachTo: document.body,
    })
    await nextTick()

    expect(document.activeElement?.textContent).toBe('Analyze receipt')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('close')).toEqual([[]])

    wrapper.unmount()
    expect(document.activeElement).toBe(opener)
  })
})
