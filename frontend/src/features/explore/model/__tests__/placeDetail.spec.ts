import { describe, expect, it } from 'vitest'

import { normalizePlaceDetail, toClosedDays, toDetailEntries } from '../placeDetail'

describe('placeDetail model helpers', () => {
  it('normalizes a nullable Place detail payload', () => {
    expect(
      normalizePlaceDetail({
        itemId: 880001,
        name: 'Seongsu Onsil',
        brand: null,
        branch: null,
        placeKind: 'NOT_SUPPORTED',
        thumbnailUrl: null,
        imageUrls: ['place.jpg', '', 42],
        region1: 'Seoul',
        region2: 'Seongsu',
        region3: null,
        addressRoad: null,
        addressDetail: null,
        latitude: null,
        longitude: null,
        hasForeignLang: null,
        hasParking: true,
        reservable: null,
        takeoutAvailable: null,
        cardPaymentAvailable: null,
        smokeFree: null,
        kidFacility: null,
        hasRestroom: null,
        isActive: null,
        viewCount: 0,
        favoriteCount: 0,
        saved: false,
        sourceUrl: null,
        postalCode: null,
        openingHours: { mon: '11:30–21:00' },
        closedDays: ['Seollal'],
        menuSummary: null,
        tel: null,
        activities: null,
      }),
    ).toMatchObject({
      placeId: 880001,
      placeKind: 'ETC',
      imageUrls: ['place.jpg'],
      isActive: false,
      activities: [],
    })
  })

  it('converts opening hours and closed days into display values', () => {
    expect(toDetailEntries({ mon: '11:30–21:00', tue: '11:30–21:00' })).toEqual([
      { label: 'mon', value: '11:30–21:00' },
      { label: 'tue', value: '11:30–21:00' },
    ])
    expect(toClosedDays(['Seollal', 'Chuseok'])).toBe('Seollal, Chuseok')
    expect(toClosedDays({ regular: 'Mondays' })).toBe('regular: Mondays')
  })
})
