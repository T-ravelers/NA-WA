import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import InsufficientBalanceDialog from '../InsufficientBalanceDialog.vue'

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(InsufficientBalanceDialog, {
    props: {
      title: 'Not enough balance',
      description: 'Your balance is too low to send 12.50 P.',
      laterLabel: 'Not now',
      topupLabel: 'Top up',
      ...props,
    },
    global: { plugins: [i18n] },
    attachTo: document.body,
  })
}

describe('InsufficientBalanceDialog', () => {
  it('names itself with the title so it is announced as a dialog', () => {
    const dialog = mountDialog().get('[role="dialog"]')

    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-label')).toBe('Not enough balance')
  })

  it('offers the way out before the way forward', () => {
    const labels = mountDialog()
      .get('[role="dialog"]')
      .findAll('button')
      .map((button) => button.text())

    expect(labels).toEqual(['Not now', 'Top up'])
  })

  it.each([
    [0, 'close'],
    [1, 'topup'],
  ])('emits from button %i', async (index, event) => {
    const wrapper = mountDialog()

    await wrapper.get('[role="dialog"]').findAll('button')[index]?.trigger('click')

    expect(wrapper.emitted(event)).toHaveLength(1)
  })

  /** 팝업 밖을 눌러 닫는 것은 시트·다이얼로그 공통 동작이다. */
  it('closes when the backdrop is clicked', async () => {
    const wrapper = mountDialog()

    await wrapper.get('[role="presentation"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  /** 팝업 안을 누른 것까지 닫기로 세면 버튼을 누를 수 없다. */
  it('stays open when the dialog itself is clicked', async () => {
    const wrapper = mountDialog()

    await wrapper.get('[role="dialog"]').trigger('click')

    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('closes on Escape so it is not a keyboard trap', () => {
    const wrapper = mountDialog()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('moves focus into the dialog and gives it back on close', () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()

    const wrapper = mountDialog()
    expect(document.activeElement).toBe(
      wrapper.get('[role="dialog"]').findAll('button')[0]?.element,
    )

    wrapper.unmount()
    expect(document.activeElement).toBe(opener)
    opener.remove()
  })

  it('stands at the bottom when the caller asks for a sheet', () => {
    expect(mountDialog({ placement: 'bottom' }).get('[role="presentation"]').classes()).toContain(
      'items-end',
    )
  })
})
