import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import IconOrb from '../IconOrb.vue'

describe('IconOrb', () => {
  it('exposes the accessible name of an icon-only button', () => {
    const wrapper = mount(IconOrb, { props: { label: 'Go back' } })

    expect(wrapper.get('button').attributes('aria-label')).toBe('Go back')
  })

  it('keeps the 44px touch target', () => {
    const wrapper = mount(IconOrb, { props: { label: 'Go back' } })

    expect(wrapper.get('button').classes()).toContain('size-11')
  })

  it('supports the 48px header size without changing the default', () => {
    const wrapper = mount(IconOrb, { props: { label: 'Search', size: 'lg' } })

    expect(wrapper.get('button').classes()).toContain('size-12')
    expect(wrapper.get('button').classes()).not.toContain('size-11')
  })

  it('exposes pressed state only for toggle buttons', () => {
    const regular = mount(IconOrb, { props: { label: 'Go back' } })
    const toggle = mount(IconOrb, { props: { label: 'Search', pressed: false } })

    expect(regular.get('button').attributes('aria-pressed')).toBeUndefined()
    expect(toggle.get('button').attributes('aria-pressed')).toBe('false')
  })

  it('renders the icon passed in the slot', () => {
    const wrapper = mount(IconOrb, {
      props: { label: 'Go back' },
      slots: { default: '<svg data-testid="icon" />' },
    })

    expect(wrapper.find('[data-testid="icon"]').exists()).toBe(true)
  })

  it('carries no background by default', () => {
    const wrapper = mount(IconOrb, { props: { label: 'Go back' } })

    expect(wrapper.get('button').classes()).toContain('bg-transparent')
  })

  it('uses a scrim fill over images so the icon stays legible', () => {
    const wrapper = mount(IconOrb, { props: { label: 'Close', variant: 'overlay' } })

    expect(wrapper.get('button').classes()).toContain('bg-scrim/45')
  })

  it('uses a raised surface when it sits on a panel', () => {
    const wrapper = mount(IconOrb, { props: { label: 'More', variant: 'surface' } })

    expect(wrapper.get('button').classes()).toContain('bg-surface-2')
  })

  it('emits click', async () => {
    const wrapper = mount(IconOrb, { props: { label: 'Go back' } })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
