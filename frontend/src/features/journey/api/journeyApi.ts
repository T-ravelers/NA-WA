import { httpClient } from '@/shared/api/httpClient'
import type { AppointmentStatus } from '@/shared/lib/appointmentStatus'

export type CompanionPreference = '1' | '2-4' | '5+'
export type JourneyItemType = 'EVENT' | 'PLACE'
export type JourneyItemStatus = 'ADDED' | 'CONFIRMED'

export interface JourneyItemAddRequest {
  itemId: number
  visitDate: string
}

export interface JourneyItemResponse {
  tripItemId: number
  journeyId: number
  itemId: number
  itemType: JourneyItemType
  visitDate: string
  tripItemStatus: JourneyItemStatus
  createdAt: string
}

export interface JourneySummary {
  tripId: number
  title: string
  startDate: string
  endDate: string
  /** 여정에 담긴 EVENT 항목 수. 원본이 삭제된 항목은 빠져 있다. */
  eventCount: number
  /** 여정에 담긴 PLACE 항목 수. 원본이 삭제된 항목은 빠져 있다. */
  placeCount: number
  /**
   * 커버 사진 주소.
   *
   * 타임라인에서 가장 먼저 나오는, 썸네일이 있는 항목의 사진이다. 담긴 항목이 없거나
   * 모두 썸네일이 없으면 `null`이다.
   */
  coverImageUrl: string | null
}

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

export type JourneyUpdateInput = JourneyCreateInput

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
  /** 약속이 아직 잡히지 않은 항목에는 `appointment` 자체가 없다. */
  appointmentId: number
  activityStartAt: string
  activityEndAt: string
  appointmentStatus: AppointmentStatus
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

export async function fetchJourneys(): Promise<JourneySummary[]> {
  const response = await httpClient.get<JourneySummary[]>('/api/v1/journeys')

  return response.data
}

export interface JourneyItemExistsResult {
  /** 그 자리에 살아 있는 항목이 있는지. 여정 담기(POST items)가 거절되는 조건이다. */
  exists: boolean
  /** 그 자리에 다른 약속이 걸려 있는지. 약속 생성이 거절되는 조건이다. */
  appointmentLinked: boolean
}

// 두 값을 함께 돌려준다. 담아만 둔 자리는 약속 항목으로 승격되므로 exists가
// true여도 약속은 만들 수 있다 — 한 값으로 합치면 담아 둔 장소로는 약속을 만들 수
// 없게 된다.
export async function checkJourneyItem(
  tripId: number,
  itemId: number,
  visitDate: string,
): Promise<JourneyItemExistsResult> {
  const response = await httpClient.get<JourneyItemExistsResult>(
    `/api/v1/journeys/${tripId}/items/exists`,
    { params: { itemId, visitDate } },
  )

  return response.data
}

export async function addJourneyItem(
  journeyId: number,
  request: JourneyItemAddRequest,
): Promise<JourneyItemResponse> {
  const response = await httpClient.post<JourneyItemResponse>(
    `/api/v1/journeys/${journeyId}/items`,
    request,
  )

  return response.data
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

export async function updateJourney(tripId: number, input: JourneyUpdateInput): Promise<Journey> {
  const response = await httpClient.put<JourneyResponse>(
    `/api/v1/journeys/${tripId}`,
    buildJourneyCreateRequest(input),
  )

  return normalizeJourney(response.data)
}

export async function deleteJourneyItem(tripId: number, tripItemId: number): Promise<void> {
  await httpClient.delete(`/api/v1/journeys/${tripId}/items/${tripItemId}`)
}

export async function deleteJourney(tripId: number): Promise<void> {
  await httpClient.delete(`/api/v1/journeys/${tripId}`)
}
