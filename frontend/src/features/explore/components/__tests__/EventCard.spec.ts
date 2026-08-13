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

  it('renders the period when both dates are present', () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n, createPinia()] },
      props: { event },
    })

    expect(wrapper.text()).toContain('2026.08.01 ~ 2026.08.31')
  })

  // `end_date`는 널을 허용하며 로컬 시드 854건 중 74건이 널이다. 가드가 없으면 렌더
  // 예외가 형제 vnode까지 무너뜨려 카드 한 장이 아니라 목록 전체가 사라진다.
  it('renders a card whose end date is missing', () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n, createPinia()] },
      props: { event: { ...event, endDate: null } },
    })

    expect(wrapper.text()).toContain('Sample event')
    expect(wrapper.text()).toContain('2026.08.01')
    expect(wrapper.text()).not.toContain('~')
  })

  it('renders a card whose dates are both missing', () => {
    const wrapper = mount(EventCard, {
      global: { plugins: [i18n, createPinia()] },
      props: { event: { ...event, startDate: null, endDate: null } },
    })

    expect(wrapper.text()).toContain('Sample event')
    expect(wrapper.text()).not.toContain('~')
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
