import { httpClient } from '@/shared/api/httpClient'

export type CompanionPreference = '1' | '2-4' | '5+'
export type JourneyItemType = 'EVENT' | 'PLACE'
export type JourneyItemStatus = 'ADDED' | 'CONFIRMED'

export interface JourneyRegion {
  regionCode: string
  regionName: string
  displayOrder: number
}

export interface Journey {
  tripId: number
  title: string
  startDate: string
  endDate: string
  budgetAmount: number | null
  companionPreference: string | null
  regions: JourneyRegion[]
}

interface JourneyResponse extends Omit<Journey, 'regions'> {
  regions?: JourneyRegion[] | null
}

export interface JourneyCreateInput {
  title: string
  startDate: string
  endDate: string
  budgetAmount: number | null
  companionPreference: CompanionPreference | null
  regions: JourneyRegion[]
}

export interface JourneyTimelineLocation {
  region1: string | null
  region2: string | null
  region3: string | null
  addressRoad: string | null
  addressDetail: string | null
  latitude: number | null
  longitude: number | null
}

export interface JourneyTimelineExploreItem {
  itemType: JourneyItemType
  title: string
  thumbnailUrl: string | null
  imageUrls: string[]
  location: JourneyTimelineLocation
}

export interface JourneyTimelineAppointment {
  activityStartAt: string
  activityEndAt: string
  appointmentStatus: string
}

export interface JourneyTimelineEventDetail {
  eventKind: string | null
  startDate: string | null
  endDate: string | null
  organizer: string | null
  reservationUrl: string | null
  venueName: string | null
}

export interface JourneyTimelinePlaceDetail {
  placeKind: string | null
  addressDetail: string | null
  menuSummary: string | null
  isActive: boolean | null
}

export interface JourneyTimelineItem {
  tripItemId: number
  itemId: number
  status: JourneyItemStatus
  displayOrder: number
  note: string | null
  exploreItem: JourneyTimelineExploreItem
  eventDetail?: JourneyTimelineEventDetail
  placeDetail?: JourneyTimelinePlaceDetail
  appointment?: JourneyTimelineAppointment
}

export interface JourneyTimelineDay {
  visitDate: string
  items: JourneyTimelineItem[]
}

export interface JourneyTimeline {
  tripId: number
  timeline: JourneyTimelineDay[]
}

interface JourneyTimelineDayResponse extends Omit<JourneyTimelineDay, 'items'> {
  items?: JourneyTimelineItem[] | null
}

interface JourneyTimelineResponse extends Omit<JourneyTimeline, 'timeline'> {
  timeline?: JourneyTimelineDayResponse[] | null
}

function normalizeJourney(journey: JourneyResponse): Journey {
  return {
    ...journey,
    regions: [...(journey.regions ?? [])].sort(
      (first, second) =>
        first.displayOrder - second.displayOrder ||
        first.regionCode.localeCompare(second.regionCode),
    ),
  }
}

function normalizeTimeline(response: JourneyTimelineResponse): JourneyTimeline {
  return {
    ...response,
    timeline: (response.timeline ?? []).map((day) => ({
      ...day,
      items: day.items ?? [],
    })),
  }
}

export function buildJourneyCreateRequest(input: JourneyCreateInput): JourneyCreateInput {
  return {
    title: input.title.trim(),
    startDate: input.startDate,
    endDate: input.endDate,
    budgetAmount: input.budgetAmount,
    companionPreference: input.companionPreference,
    regions: input.regions.map((region, displayOrder) => ({
      regionCode: region.regionCode.trim(),
      regionName: region.regionName.trim(),
      displayOrder,
    })),
  }
}

export async function createJourney(input: JourneyCreateInput): Promise<Journey> {
  const response = await httpClient.post<JourneyResponse>(
    '/api/v1/journeys',
    buildJourneyCreateRequest(input),
  )

  return normalizeJourney(response.data)
}

export async function fetchJourney(tripId: number): Promise<Journey> {
  const response = await httpClient.get<JourneyResponse>(`/api/v1/journeys/${tripId}`)

  return normalizeJourney(response.data)
}

export async function fetchJourneyTimeline(tripId: number): Promise<JourneyTimeline> {
  const response = await httpClient.get<JourneyTimelineResponse>(
    `/api/v1/journeys/${tripId}/timeline`,
  )

  return normalizeTimeline(response.data)
}
