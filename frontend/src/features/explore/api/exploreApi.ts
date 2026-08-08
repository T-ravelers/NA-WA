import { httpClient } from '@/shared/api/httpClient'

import type { EventDetail } from '../model/eventDetail'
import type { EventListResponse, EventSearchFilters } from '../model/eventExplore'

const EVENT_LIST_PATH = '/api/v1/explore/events'
const EVENT_DETAIL_PATH = '/api/v1/explore/events'

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
  if (filters.datePreset !== undefined) params.set('datePreset', filters.datePreset)
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

export async function fetchEventList(filters: EventSearchFilters = {}): Promise<EventListResponse> {
  const response = await httpClient.get<EventListResponse>(EVENT_LIST_PATH, {
    params: toSearchParams(filters),
  })

  return response.data
}

export async function fetchEventDetail(
  eventId: number | string,
  language = 'en',
): Promise<EventDetail> {
  const response = await httpClient.get<EventDetail>(`${EVENT_DETAIL_PATH}/${eventId}`, {
    params: { language },
  })

  return response.data
}

export { EVENT_DETAIL_PATH, EVENT_LIST_PATH, toSearchParams }
