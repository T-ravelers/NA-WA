export type PlaceSort = 'LATEST' | 'POPULAR'

export const PLACE_KINDS = ['RESTAURANT', 'CAFE', 'MARKET', 'BEAUTY', 'ETC'] as const

export type PlaceKind = (typeof PLACE_KINDS)[number]

export function isPlaceKind(value: string): value is PlaceKind {
  return PLACE_KINDS.includes(value as PlaceKind)
}

/**
 * operational_v9 stores the source provider's Korean place-kind labels (for
 * example, 관광식당·카페·상설시장·뷰티매장). The public API keeps the
 * normalized five-value contract used by the UI; ETC is rendered as Other.
 */
const SOURCE_PLACE_KIND_ALIASES: Record<string, PlaceKind> = {
  관광식당: 'RESTAURANT',
  서양식: 'RESTAURANT',
  일식: 'RESTAURANT',
  중식: 'RESTAURANT',
  기타외국식: 'RESTAURANT',
  '김밥 분식': 'RESTAURANT',
  분식: 'RESTAURANT',
  퓨전음식: 'RESTAURANT',
  이동음식: 'RESTAURANT',
  모범음식점: 'RESTAURANT',
  카페: 'CAFE',
  찻집: 'CAFE',
  제과: 'CAFE',
  기타음료점: 'CAFE',
  상설시장: 'MARKET',
  비상설시장: 'MARKET',
  복합쇼핑몰: 'MARKET',
  백화점: 'MARKET',
  '관광기념품/특산물판매점': 'MARKET',
  아웃렛: 'MARKET',
  '공방/공예품점': 'MARKET',
  '바/펍': 'RESTAURANT',
  뷰티매장: 'BEAUTY',
}

export function normalizePlaceKind(value: string | null | undefined): PlaceKind {
  const trimmed = value?.trim()
  if (!trimmed) return 'ETC'

  const normalized = trimmed.toUpperCase()
  if (isPlaceKind(normalized)) return normalized

  return SOURCE_PLACE_KIND_ALIASES[trimmed] ?? 'ETC'
}

export interface PlaceSummaryResponse {
  itemId: number
  name: string
  brand: string | null
  branch: string | null
  placeKind: string | null
  thumbnailUrl: string | null
  imageUrls: unknown
  region1: string | null
  region2: string | null
  region3: string | null
  addressRoad: string | null
  addressDetail: string | null
  latitude: number | null
  longitude: number | null
  hasForeignLang?: boolean | null
  hasParking?: boolean | null
  reservable?: boolean | null
  takeoutAvailable?: boolean | null
  cardPaymentAvailable?: boolean | null
  smokeFree?: boolean | null
  kidFacility?: boolean | null
  hasRestroom?: boolean | null
  isActive: boolean | null
  viewCount: number
  favoriteCount: number
}

export interface PlaceSummary extends Omit<PlaceSummaryResponse, 'imageUrls' | 'isActive'> {
  imageUrls: string[]
  isActive: boolean
}

export interface PlaceListResponse {
  content: PlaceSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface PlaceListResponsePayload {
  content: PlaceSummaryResponse[] | null
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface PlaceSearchFilters {
  sectorIds?: number[]
  activityIds?: number[]
  placeKinds?: PlaceKind[]
  region1?: string[]
  region2?: string[]
  region3?: string[]
  keyword?: string
  hasForeignLang?: boolean
  hasParking?: boolean
  reservable?: boolean
  takeoutAvailable?: boolean
  cardPaymentAvailable?: boolean
  smokeFree?: boolean
  kidFacility?: boolean
  hasRestroom?: boolean
  savedOnly?: boolean
  sort?: PlaceSort
  language?: string
  page?: number
  size?: number
}

export function toImageUrls(value: unknown): string[] {
  if (!Array.isArray(value)) return []

  return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
}

export function normalizePlaceSummary(place: PlaceSummaryResponse): PlaceSummary {
  return {
    ...place,
    imageUrls: toImageUrls(place.imageUrls),
    isActive: place.isActive ?? false,
  }
}

export function normalizePlaceListResponse(payload: PlaceListResponsePayload): PlaceListResponse {
  return {
    ...payload,
    content: (payload.content ?? []).map(normalizePlaceSummary),
  }
}
