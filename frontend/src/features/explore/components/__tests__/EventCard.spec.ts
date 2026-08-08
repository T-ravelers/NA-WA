import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import EventCard from '../EventCard.vue'

describe('EventCard', () => {
  const event = {
    itemId: 42,
    eventKind: 'POPUP' as const,
    status: 'ONGOING' as const,
    title: 'Sample event',
    subtitle: null,
    thumbnailUrl: null,
    region1: 'Seoul',
    region2: null,
    region3: null,
    latitude: null,
    longitude: null,
    startDate: '2026-08-01',
    endDate: '2026-08-31',
  }

  it('emits the event id when the card is activated', async () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n, createPinia()] },
      props: { event },
    })

    await wrapper.get('article').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })

  it('reflects the saved state when the heart is toggled', async () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n, createPinia()] },
      props: { event },
    })

    const saveButton = wrapper.get('button[aria-pressed]')

    expect(saveButton.attributes('aria-pressed')).toBe('false')

    await saveButton.trigger('click')

    expect(saveButton.attributes('aria-pressed')).toBe('true')
  })
})
