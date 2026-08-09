import type { EventKind, EventStatus } from './eventExplore'

export interface EventActivity {
  activityId: number
  activityCode: string | null
  activityName: string | null
  sectorId: number | null
  sectorCode: string | null
  sectorName: string | null
  isPrimary: boolean | null
}

export interface EventDetail {
  eventId: number
  eventType: string | null
  eventKind: EventKind
  title: string
  subtitle: string | null
  description: string | null
  programText: string | null
  thumbnailUrl: string | null
  imageUrls: unknown
  links: unknown
  reservationUrl: string | null
  preReservation: unknown
  status: EventStatus
  isPermanent: boolean | null
  startDate: string | null
  endDate: string | null
  operatingHours: unknown
  openDays: unknown
  openWeekend: boolean | null
  opensLate: boolean | null
  venueName: string | null
  region1: string | null
  region2: string | null
  region3: string | null
  addressRoad: string | null
  latitude: number | null
  longitude: number | null
  hasPhotoZone: boolean | null
  isExperience: boolean | null
  ageLimit: string | null
  isFree: boolean | null
  priceText: string | null
  hasBenefit: boolean | null
  reservable: boolean | null
  contact: string | null
  organizer: string | null
  activities: EventActivity[]
}

export interface DetailEntry {
  label: string
  value: string
}

export function toStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return []

  return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
}

export function toImageUrls(value: unknown): string[] {
  if (Array.isArray(value)) return toStringList(value)
  if (!isRecord(value)) return []

  return [value.url, value.src].filter(
    (item): item is string => typeof item === 'string' && item.trim() !== '',
  )
}

export function toDetailEntries(value: unknown): DetailEntry[] {
  if (!isRecord(value)) return []

  return Object.entries(value)
    .filter(([, item]) => typeof item === 'string' || typeof item === 'number')
    .map(([label, item]) => ({ label, value: String(item) }))
}

export function resolveReservationUrl(event: EventDetail): string | null {
  if (event.reservationUrl?.trim()) return event.reservationUrl

  if (hasReservation(event.preReservation)) {
    const link = readString(event.preReservation, 'link')
    if (link) return link
  }

  if (isRecord(event.links)) {
    return (
      [event.links.reservationUrl, event.links.reservation_url, event.links.url].find(
        (item): item is string => typeof item === 'string' && item.trim() !== '',
      ) ?? null
    )
  }

  return null
}

export function resolveHomepageUrl(event: EventDetail): string | null {
  if (!isRecord(event.links)) return null

  return (
    [event.links.homepageUrl, event.links.homepage_url].find(
      (item): item is string => typeof item === 'string' && item.trim() !== '',
    ) ?? null
  )
}

function hasReservation(value: unknown): boolean {
  return isRecord(value) && value.has === true && typeof value.link === 'string'
}

function readString(value: unknown, key: string): string | null {
  if (!isRecord(value)) return null
  return typeof value[key] === 'string' && value[key].trim() !== '' ? value[key] : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
