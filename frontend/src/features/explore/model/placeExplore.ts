export type PlaceSort = 'LATEST' | 'POPULAR'

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
