import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import EventCard from '../EventCard.vue'

describe('EventCard', () => {
  it('emits the event id when the card is activated', async () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n] },
      props: {
        event: {
          itemId: 42,
          eventKind: 'POPUP',
          status: 'ONGOING',
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
        },
      },
    })

    await wrapper.get('article').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })
})
