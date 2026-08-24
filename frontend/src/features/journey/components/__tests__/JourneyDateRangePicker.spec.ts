import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneyDateRangePicker from '../JourneyDateRangePicker.vue'

const props = {
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  startLabel: 'Start date',
  endLabel: 'End date',
}

function mountPicker(overrides: Partial<typeof props> = {}) {
  return mount(JourneyDateRangePicker, {
    props: { ...props, ...overrides },
    global: { plugins: [i18n] },
  })
}

describe('JourneyDateRangePicker', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('uses custom date buttons instead of native date inputs', () => {
    const wrapper = mountPicker()

    expect(wrapper.find('input[type="date"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="journey-date-start"]').text()).toContain('8/10/26')
    expect(wrapper.get('[data-testid="journey-date-end"]').text()).toContain('8/12/26')
  })

  it('moves focus into the picker, closes on Escape, and restores the opener', async () => {
    const wrapper = mount(JourneyDateRangePicker, {
      props,
      global: { plugins: [i18n] },
      attachTo: document.body,
    })
    const opener = wrapper.get('[data-testid="journey-date-start"]').element as HTMLButtonElement
    opener.focus()

    await wrapper.get('[data-testid="journey-date-start"]').trigger('click')

    expect(document.activeElement).toBe(
      wrapper.get('[role="dialog"]').findAll('button')[0]?.element,
    )

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(opener)
    wrapper.unmount()
  })

  it('keeps Tab focus inside the open picker', async () => {
    const wrapper = mount(JourneyDateRangePicker, {
      props,
      global: { plugins: [i18n] },
      attachTo: document.body,
    })

    await wrapper.get('[data-testid="journey-date-start"]').trigger('click')

    const controls = Array.from(
      wrapper
        .get('[role="dialog"]')
        .element.querySelectorAll<HTMLButtonElement>('button:not([disabled])'),
    )
    const first = controls[0]
    const last = controls[controls.length - 1]
    if (first === undefined || last === undefined) throw new Error('Expected dialog controls')

    first.focus()
    window.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true }),
    )
    expect(document.activeElement).toBe(last)

    last.focus()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true }))
    expect(document.activeElement).toBe(first)

    wrapper.unmount()
  })

  it('disables dates before the start while choosing an end date', async () => {
    const wrapper = mountPicker()

    await wrapper.get('[data-testid="journey-date-end"]').trigger('click')

    expect(wrapper.get('button[aria-label="Select August 9, 2026"]').attributes('disabled')).toBe(
      '',
    )
    expect(
      wrapper.get('button[aria-label="Select August 10, 2026"]').attributes('disabled'),
    ).toBeUndefined()
  })

  it('emits a complete ordered range only after applying', async () => {
    const wrapper = mountPicker({ startDate: '2026-08-08', endDate: '' })

    await wrapper.get('[data-testid="journey-date-start"]').trigger('click')
    await wrapper.get('button[aria-label="Select August 10, 2026"]').trigger('click')

    expect(wrapper.get('[data-testid="journey-date-target-end"]').attributes('aria-pressed')).toBe(
      'true',
    )
    expect(wrapper.get('button[aria-label="Select August 9, 2026"]').attributes('disabled')).toBe(
      '',
    )

    await wrapper.get('button[aria-label="Select August 12, 2026"]').trigger('click')
    const applyButton = wrapper.findAll('button').find((button) => button.text() === 'Apply dates')

    expect(applyButton).toBeDefined()
    await applyButton?.trigger('click')

    expect(wrapper.emitted('update:startDate')).toEqual([['2026-08-10']])
    expect(wrapper.emitted('update:endDate')).toEqual([['2026-08-12']])
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
