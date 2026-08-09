import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ExploreItemTabs from '../ExploreItemTabs.vue'

describe('ExploreItemTabs', () => {
  it('renders Events and Places choices', () => {
    const wrapper = mount(ExploreItemTabs, {
      props: {
        modelValue: 'events',
        eventsLabel: 'Events',
        placesLabel: 'Places',
        label: 'Explore content type',
      },
    })

    expect(wrapper.get('[role="radiogroup"]').attributes('aria-label')).toBe('Explore content type')
    const buttons = wrapper.findAll('button')

    expect(buttons).toHaveLength(2)
    expect(buttons[0]?.text()).toBe('Events')
    expect(buttons[1]?.text()).toBe('Places')
  })

  it('emits the selected content type', async () => {
    const wrapper = mount(ExploreItemTabs, {
      props: {
        modelValue: 'events',
        eventsLabel: 'Events',
        placesLabel: 'Places',
        label: 'Explore content type',
      },
    })

    await wrapper.findAll('button')[1]?.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['places']])
  })
})
