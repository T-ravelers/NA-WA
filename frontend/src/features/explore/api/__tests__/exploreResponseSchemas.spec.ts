import { describe, expect, it } from 'vitest'

import {
  eventDetailResponseSchema,
  eventListResponseSchema,
  placeDetailResponseSchema,
  placeListResponseSchema,
} from '../exploreResponseSchemas'

const eventSummary = {
  itemId: 101,
  eventKind: 'FESTIVAL',
  status: 'ONGOING',
  title: 'Night market',
  subtitle: null,
  thumbnailUrl: null,
  region1: 'Seoul',
  region2: null,
  region3: null,
  latitude: 37.5,
  longitude: 127,
  startDate: '2026-08-01',
  endDate: null,
  saved: false,
}

const eventDetail = {
  eventId: 101,
  eventType: null,
  eventKind: 'FESTIVAL',
  title: 'Night market',
  subtitle: null,
  description: null,
  programText: null,
  thumbnailUrl: null,
  imageUrls: ['night-market.jpg'],
  links: null,
  reservationUrl: null,
  preReservation: null,
  status: 'ONGOING',
  isPermanent: false,
  startDate: '2026-08-01',
  endDate: null,
  operatingHours: null,
  openDays: null,
  openWeekend: null,
  opensLate: null,
  venueName: null,
  region1: 'Seoul',
  region2: null,
  region3: null,
  addressRoad: null,
  latitude: null,
  longitude: null,
  hasPhotoZone: null,
  isExperience: null,
  ageLimit: null,
  isFree: true,
  priceText: null,
  hasBenefit: null,
  reservable: false,
  contact: null,
  organizer: null,
  saved: false,
  activities: [],
}

const placeSummary = {
  itemId: 201,
  name: 'Seongsu Onsil',
  brand: null,
  branch: null,
  placeKind: 'CAFE',
  thumbnailUrl: null,
  imageUrls: [],
  region1: 'Seoul',
  region2: null,
  region3: null,
  addressRoad: null,
  addressDetail: null,
  latitude: null,
  longitude: null,
  isActive: true,
  viewCount: 0,
  favoriteCount: 0,
  saved: false,
}

describe('explore response schemas', () => {
  it('accepts nullable event end dates and forward-compatible extra fields', () => {
    const result = eventListResponseSchema.safeParse({
      content: [{ ...eventSummary, futureFlag: 'kept by caller' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      serverAddedField: true,
    })

    expect(result.success).toBe(true)
  })

  it('requires typed event fields instead of accepting a wrong display name type', () => {
    const result = eventDetailResponseSchema.safeParse({
      ...eventDetail,
      title: 123,
    })

    expect(result.success).toBe(false)
  })

  it('rejects unknown event kinds and statuses at both list and detail boundaries', () => {
    expect(
      eventListResponseSchema.safeParse({
        content: [{ ...eventSummary, eventKind: 'NEW_KIND' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }).success,
    ).toBe(false)
    expect(
      eventListResponseSchema.safeParse({
        content: [{ ...eventSummary, status: 'NEW_STATUS' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }).success,
    ).toBe(false)
    expect(
      eventDetailResponseSchema.safeParse({ ...eventDetail, eventKind: 'NEW_KIND' }).success,
    ).toBe(false)
    expect(
      eventDetailResponseSchema.safeParse({ ...eventDetail, status: 'NEW_STATUS' }).success,
    ).toBe(false)
  })

  it('accepts nullable Place list content because the API normalizer maps it to an empty array', () => {
    expect(
      placeListResponseSchema.safeParse({
        content: null,
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      }).success,
    ).toBe(true)
  })

  it('accepts nullable Place activities because the detail normalizer maps them to an empty array', () => {
    expect(
      placeDetailResponseSchema.safeParse({
        ...placeSummary,
        placeId: 201,
        sourceUrl: null,
        postalCode: null,
        openingHours: null,
        closedDays: null,
        menuSummary: null,
        tel: null,
        activities: null,
      }).success,
    ).toBe(true)
  })
})
