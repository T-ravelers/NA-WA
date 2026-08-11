export const EVENT_KINDS = ['POPUP', 'CONCERT', 'ETC', 'FESTIVAL', 'EXHIBITION'] as const

export type EventKind = (typeof EVENT_KINDS)[number]
export type EventStatus = 'SCHEDULED' | 'ONGOING' | 'ENDED'
export type EventSort = 'LATEST' | 'POPULAR' | 'ENDING_SOON'

export interface EventSummary {
  itemId: number
  eventKind: EventKind
  status: EventStatus
  title: string
  subtitle: string | null
  thumbnailUrl: string | null
  region1: string | null
  region2: string | null
  region3: string | null
  latitude: number | null
  longitude: number | null
  // `EventSummaryResponse`의 `LocalDate`는 널을 허용한다. 실제로 종료일이 없는 Event가 있다.
  startDate: string | null
  endDate: string | null
}

export interface EventListResponse {
  content: EventSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface EventSearchFilters {
  sectorIds?: number[]
  activityIds?: number[]
  eventKinds?: EventKind[]
  region1?: string[]
  region2?: string[]
  region2Other?: boolean
  region3?: string[]
  keyword?: string
  datePreset?: string
  startDate?: string
  endDate?: string
  freeOnly?: boolean
  openWeekendOnly?: boolean
  opensLateOnly?: boolean
  preReservationOnly?: boolean
  experienceOnly?: boolean
  photoZoneOnly?: boolean
  savedOnly?: boolean
  sort?: EventSort
  language?: string
  page?: number
  size?: number
}
