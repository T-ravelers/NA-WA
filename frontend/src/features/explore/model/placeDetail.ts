import type { PlaceKind, PlaceSummaryResponse } from './placeExplore'
import { normalizePlaceKind, toImageUrls } from './placeExplore'

export interface PlaceActivity {
  activityId: number
  activityCode: string | null
  activityName: string | null
  sectorId: number | null
  sectorCode: string | null
  sectorName: string | null
  isPrimary: boolean | null
}

/**
 * Place 상세 API 응답입니다.
 *
 * 백엔드 상세 계약은 목록의 기본 정보에 V3/V7 상세 컬럼을 더해 반환합니다. `placeId`와
 * `itemId`는 서버 구현 시점의 명칭 차이를 안전하게 수용하기 위해 둘 다 읽습니다.
 */
export interface PlaceDetailResponse extends PlaceSummaryResponse {
  placeId?: number
  sourceUrl: string | null
  addressDetail: string | null
  postalCode: string | null
  openingHours: unknown
  closedDays: unknown
  menuSummary: string | null
  tel: string | null
  activities: PlaceActivity[] | null
}

export interface PlaceDetail extends Omit<
  PlaceDetailResponse,
  'placeId' | 'itemId' | 'imageUrls' | 'placeKind' | 'isActive' | 'activities'
> {
  placeId: number
  itemId: number
  imageUrls: string[]
  placeKind: PlaceKind
  isActive: boolean
  activities: PlaceActivity[]
}

export interface DetailEntry {
  label: string
  value: string
}

export function normalizePlaceDetail(payload: PlaceDetailResponse): PlaceDetail {
  const placeId = payload.placeId ?? payload.itemId

  return {
    ...payload,
    placeId,
    itemId: payload.itemId ?? placeId,
    placeKind: normalizePlaceKind(payload.placeKind),
    imageUrls: toImageUrls(payload.imageUrls),
    isActive: payload.isActive ?? false,
    activities: payload.activities ?? [],
  }
}

/**
 * 원본 문자열에 섞여 오는 `<br>`을 줄바꿈으로 되돌린다.
 *
 * 수집한 영업시간의 3분의 1가량이 `- 12:00~22:00<br>- 준비시간 15:00~18:00`처럼
 * 태그를 그대로 달고 온다. 화면은 문자열을 이스케이프하므로 그대로 두면 `<br>`이
 * 글자로 보인다. HTML로 렌더링하지 않고 문자열만 바꾸므로 태그가 실행될 여지는 없다.
 */
function unescapeLineBreaks(value: string): string {
  return value
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .trim()
}

export function toDetailEntries(value: unknown): DetailEntry[] {
  if (typeof value === 'string' && value.trim() !== '') {
    return [{ label: 'hours', value: unescapeLineBreaks(value) }]
  }

  if (!isRecord(value)) return []

  return Object.entries(value)
    .filter(([, item]) => typeof item === 'string' || typeof item === 'number')
    .map(([label, item]) => ({ label, value: unescapeLineBreaks(String(item)) }))
}

export function toClosedDays(value: unknown): string {
  if (Array.isArray(value)) {
    return value
      .filter((item): item is string => typeof item === 'string' && item.trim() !== '')
      .join(', ')
  }

  return toDetailEntries(value)
    .map((entry) => `${entry.label}: ${entry.value}`)
    .join(', ')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
