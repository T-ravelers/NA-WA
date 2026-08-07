import { describe, expect, it } from 'vitest'

import {
  resolveHomepageUrl,
  resolveReservationUrl,
  toDetailEntries,
  toImageUrls,
  toStringList,
  type EventDetail,
} from '../eventDetail'

const event = (overrides: Partial<EventDetail> = {}): EventDetail => ({
  eventId: 1,
  eventType: 'OFFICIAL',
  eventKind: 'POPUP',
  title: 'Sample event',
  subtitle: null,
  description: null,
  programText: null,
  thumbnailUrl: null,
  imageUrls: null,
  links: null,
  reservationUrl: null,
  preReservation: null,
  status: 'SCHEDULED',
  isPermanent: false,
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  operatingHours: null,
  openDays: null,
  openWeekend: null,
  opensLate: null,
  venueName: null,
  region1: null,
  region2: null,
  region3: null,
  addressRoad: null,
  latitude: null,
  longitude: null,
  hasPhotoZone: null,
  isExperience: null,
  ageLimit: null,
  isFree: null,
  priceText: null,
  hasBenefit: null,
  reservable: null,
  contact: null,
  organizer: null,
  activities: [],
  ...overrides,
})

describe('eventDetail model helpers', () => {
  it('normalizes JSON arrays and scalar object entries for presentation', () => {
    expect(toImageUrls(['one.jpg', '', 3])).toEqual(['one.jpg'])
    expect(toStringList(['Mon', null, ''])).toEqual(['Mon'])
    expect(toDetailEntries({ monday: '10:00–20:00', hidden: true })).toEqual([
      { label: 'monday', value: '10:00–20:00' },
    ])
  })

  it('keeps the reservation URL priority defined by the API contract', () => {
    expect(
      resolveReservationUrl(
        event({
          preReservation: { has: true, link: 'https://pre.example' },
          reservationUrl: 'https://column.example',
          links: { reservationUrl: 'https://links.example' },
        }),
      ),
    ).toBe('https://column.example')

    expect(
      resolveReservationUrl(
        event({
          reservationUrl: 'https://column.example',
          links: { reservationUrl: 'https://links.example' },
        }),
      ),
    ).toBe('https://column.example')

    expect(resolveHomepageUrl(event({ links: { homepage_url: 'https://homepage.example' } }))).toBe(
      'https://homepage.example',
    )
  })
})
