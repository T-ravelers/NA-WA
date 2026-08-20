// 종류 칩이 이 순서 그대로 그려진다. PLACE_KINDS처럼 Other(ETC)를 마지막에 둔다.
export const EVENT_KINDS = ['POPUP', 'CONCERT', 'FESTIVAL', 'EXHIBITION', 'ETC'] as const
export const EVENT_STATUSES = ['SCHEDULED', 'ONGOING', 'ENDED'] as const

export type EventKind = (typeof EVENT_KINDS)[number]
export type EventStatus = (typeof EVENT_STATUSES)[number]
export type EventSort = 'NEWEST' | 'POPULAR' | 'ENDING_SOON'

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
  saved: boolean
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
  /** 화면 전용 — 달력의 선택 가능 범위를 정하는 프리셋. 서버로 보내지 않는다. */
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
