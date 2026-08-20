import { httpClient } from '@/shared/api/httpClient'

import type { EventDetail } from '../model/eventDetail'
import type { EventListResponse, EventSearchFilters } from '../model/eventExplore'
import {
  eventDetailResponseSchema,
  eventListResponseSchema,
  exploreItemLikeResponseSchema,
  placeDetailResponseSchema,
  placeListResponseSchema,
} from './exploreResponseSchemas'
import {
  normalizePlaceListResponse,
  type PlaceListResponse,
  type PlaceSearchFilters,
  type PlaceListResponsePayload,
} from '../model/placeExplore'
import {
  normalizePlaceDetail,
  type PlaceDetail,
  type PlaceDetailResponse,
} from '../model/placeDetail'

const EVENT_LIST_PATH = '/api/v1/explore/events'
const EVENT_DETAIL_PATH = '/api/v1/explore/events'
const PLACE_LIST_PATH = '/api/v1/explore/places'
const PLACE_DETAIL_PATH = '/api/v1/explore/places'

export interface ExploreItemLikeResponse {
  saved: boolean
}

function itemLikePath(itemId: number): string {
  return `/api/v1/explore/items/${itemId}/like`
}

function appendList(
  params: URLSearchParams,
  name: string,
  values: readonly (string | number)[] | undefined,
): void {
  values?.forEach((value) => params.append(name, String(value)))
}

function toSearchParams(filters: EventSearchFilters): URLSearchParams {
  const params = new URLSearchParams()

  appendList(params, 'sectorIds', filters.sectorIds)
  appendList(params, 'activityIds', filters.activityIds)
  appendList(params, 'eventKinds', filters.eventKinds)
  appendList(params, 'region1', filters.region1)
  appendList(params, 'region2', filters.region2)
  appendList(params, 'region3', filters.region3)

  if (filters.keyword !== undefined && filters.keyword !== '') {
    params.set('keyword', filters.keyword)
  }
  // datePreset은 달력 선택 가능 범위를 정하는 화면 전용 상태다. 서버로는 항상
  // 프리셋에서 계산된 startDate/endDate만 보낸다.
  if (filters.startDate !== undefined) params.set('startDate', filters.startDate)
  if (filters.endDate !== undefined) params.set('endDate', filters.endDate)
  if (filters.sort !== undefined) params.set('sort', filters.sort)
  if (filters.language !== undefined) params.set('language', filters.language)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))

  const booleanFilters = [
    'freeOnly',
    'openWeekendOnly',
    'opensLateOnly',
    'preReservationOnly',
    'experienceOnly',
    'photoZoneOnly',
    'savedOnly',
    'region2Other',
  ] as const

  booleanFilters.forEach((name) => {
    const value = filters[name]
    if (value !== undefined) params.set(name, String(value))
  })

  return params
}

function toPlaceSearchParams(filters: PlaceSearchFilters): URLSearchParams {
  const params = new URLSearchParams()

  appendList(params, 'sectorIds', filters.sectorIds)
  appendList(params, 'activityIds', filters.activityIds)
  appendList(params, 'placeKinds', filters.placeKinds)
  appendList(params, 'region1', filters.region1)
  appendList(params, 'region2', filters.region2)
  appendList(params, 'region3', filters.region3)

  if (filters.keyword !== undefined && filters.keyword !== '') {
    params.set('keyword', filters.keyword)
  }
  if (filters.sort !== undefined) params.set('sort', filters.sort)
  if (filters.language !== undefined) params.set('language', filters.language)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))

  const booleanFilters = [
    'region2Other',
    'hasForeignLang',
    'hasParking',
    'reservable',
    'takeoutAvailable',
    'cardPaymentAvailable',
    'smokeFree',
    'kidFacility',
    'hasRestroom',
    'savedOnly',
  ] as const

  booleanFilters.forEach((name) => {
    const value = filters[name]
    if (value !== undefined) params.set(name, String(value))
  })

  return params
}

export async function fetchEventList(filters: EventSearchFilters = {}): Promise<EventListResponse> {
  const response = await httpClient.get<EventListResponse>(EVENT_LIST_PATH, {
    params: toSearchParams(filters),
    responseSchema: eventListResponseSchema,
  })

  return response.data
}

export async function fetchEventDetail(
  eventId: number | string,
  language = 'en',
): Promise<EventDetail> {
  const response = await httpClient.get<EventDetail>(`${EVENT_DETAIL_PATH}/${eventId}`, {
    params: { language },
    responseSchema: eventDetailResponseSchema,
  })

  return response.data
}

export async function fetchPlaceList(filters: PlaceSearchFilters = {}): Promise<PlaceListResponse> {
  const response = await httpClient.get<PlaceListResponsePayload>(PLACE_LIST_PATH, {
    params: toPlaceSearchParams(filters),
    responseSchema: placeListResponseSchema,
  })

  return normalizePlaceListResponse(response.data)
}

// 찜 등록·취소는 EVENT·PLACE 공통으로 explore_items의 itemId를 쓴다.
// 두 호출 모두 멱등이라 재시도해도 같은 최종 상태(saved)를 돌려받는다.
export async function likeExploreItem(itemId: number): Promise<ExploreItemLikeResponse> {
  const response = await httpClient.post<ExploreItemLikeResponse>(itemLikePath(itemId), undefined, {
    responseSchema: exploreItemLikeResponseSchema,
  })

  return response.data
}

export async function unlikeExploreItem(itemId: number): Promise<ExploreItemLikeResponse> {
  const response = await httpClient.delete<ExploreItemLikeResponse>(itemLikePath(itemId), {
    responseSchema: exploreItemLikeResponseSchema,
  })

  return response.data
}

export async function fetchPlaceDetail(
  placeId: number | string,
  language = 'en',
): Promise<PlaceDetail> {
  const response = await httpClient.get<PlaceDetailResponse>(`${PLACE_DETAIL_PATH}/${placeId}`, {
    params: { language },
    responseSchema: placeDetailResponseSchema,
  })

  return normalizePlaceDetail(response.data)
}

export {
  EVENT_DETAIL_PATH,
  EVENT_LIST_PATH,
  PLACE_DETAIL_PATH,
  PLACE_LIST_PATH,
  toPlaceSearchParams,
  toSearchParams,
}
