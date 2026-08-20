import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import AppToastHost from '../AppToastHost.vue'
import { showToast } from '../toast'

describe('toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('shows a message and removes it after the duration', async () => {
    const wrapper = mount(AppToastHost)

    showToast('Saved list update failed')
    await nextTick()

    expect(wrapper.text()).toContain('Saved list update failed')

    vi.advanceTimersByTime(3_000)
    await nextTick()

    expect(wrapper.text()).not.toContain('Saved list update failed')
  })

  it('stacks multiple messages independently', async () => {
    const wrapper = mount(AppToastHost)

    showToast('first message')
    vi.advanceTimersByTime(1_000)
    showToast('second message')
    await nextTick()

    expect(wrapper.text()).toContain('first message')
    expect(wrapper.text()).toContain('second message')

    vi.advanceTimersByTime(2_000)
    await nextTick()

    expect(wrapper.text()).not.toContain('first message')
    expect(wrapper.text()).toContain('second message')
  })
})
