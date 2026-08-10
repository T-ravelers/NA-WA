import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import PlaceCard from '../PlaceCard.vue'

describe('PlaceCard', () => {
  const place = {
    itemId: 42,
    name: 'Seongsu Onsil',
    brand: null,
    branch: null,
    placeKind: 'RESTAURANT',
    thumbnailUrl: null,
    imageUrls: [],
    region1: 'Seoul',
    region2: 'Seongsu',
    region3: null,
    addressRoad: null,
    addressDetail: null,
    latitude: null,
    longitude: null,
    isActive: true,
    viewCount: 10,
    favoriteCount: 2,
    hasForeignLang: null,
    hasParking: true,
    reservable: true,
    takeoutAvailable: false,
    cardPaymentAvailable: null,
    smokeFree: null,
    kidFacility: null,
    hasRestroom: false,
  }

  it('renders the place kind, region and available options', () => {
    const wrapper = mount(PlaceCard, {
      global: { plugins: [i18n] },
      props: { place },
    })

    expect(wrapper.text()).toContain('Food · Restaurant')
    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Reservation')
    expect(wrapper.text()).not.toContain('Takeout')
  })

  it('emits the place id when the card content is activated', async () => {
    const wrapper = mount(PlaceCard, {
      global: { plugins: [i18n] },
      props: { place },
    })

    await wrapper.get('button[aria-label="Open Seongsu Onsil"]').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })
})
