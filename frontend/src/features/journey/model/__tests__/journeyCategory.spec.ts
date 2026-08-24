import { describe, expect, it } from 'vitest'

import type { JourneyTimelineItem } from '../../api/journeyApi'
import { categoryForJourneyItem, categoryLabelKey } from '../journeyCategory'

function item(itemType: 'EVENT' | 'PLACE', kind: string | null): JourneyTimelineItem {
  return {
    tripItemId: 1,
    itemId: 1,
    status: 'ADDED',
    displayOrder: 0,
    note: null,
    exploreItem: {
      itemType,
      title: 'Item',
      thumbnailUrl: null,
      imageUrls: [],
      location: {
        region1: null,
        region2: null,
        region3: null,
        addressRoad: null,
        addressDetail: null,
        latitude: null,
        longitude: null,
      },
    },
    ...(itemType === 'EVENT'
      ? {
          eventDetail: {
            eventKind: kind,
            startDate: null,
            endDate: null,
            organizer: null,
            reservationUrl: null,
            venueName: null,
          },
        }
      : {
          placeDetail: {
            placeKind: kind,
            addressDetail: null,
            menuSummary: null,
            isActive: true,
          },
        }),
  }
}

describe('journeyCategory', () => {
  it.each([
    ['EVENT', 'CONCERT', 'show'],
    ['EVENT', 'POPUP', 'shopping'],
    ['EVENT', 'FESTIVAL', 'food'],
    ['PLACE', 'BEAUTY', 'beauty'],
    ['PLACE', '상설시장', 'shopping'],
    ['PLACE', '카페', 'food'],
  ] as const)('maps %s %s to %s', (itemType, kind, category) => {
    expect(categoryForJourneyItem(item(itemType, kind))).toBe(category)
  })

  it('falls unknown kinds back to Shows like Explore cards', () => {
    expect(categoryForJourneyItem(item('EVENT', 'ETC'))).toBe('show')
    expect(categoryForJourneyItem(item('PLACE', null))).toBe('show')
  })

  it('uses the shared four-category display keys', () => {
    expect((['beauty', 'shopping', 'show', 'food'] as const).map(categoryLabelKey)).toEqual([
      'spendingCategory.BEAUTY',
      'spendingCategory.SHOPPING',
      'spendingCategory.SHOW',
      'spendingCategory.FOOD',
    ])
  })
})
