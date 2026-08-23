/**
 * 지도 앱 진입 URL 조립.
 *
 * 구글 지도 범용 URL(Maps URLs)은 모바일 웹에서 앱이 설치돼 있으면 앱으로, 없으면
 * 웹으로 열리므로 설치 여부 분기가 필요 없다. 네이버가 공식 지원하는 진입은 `nmap://`
 * 앱 스킴뿐이라, 스토어 폴백을 얻을 수 있는 Android만 `intent://`로 감싼다(#269).
 * 좌표가 없는 항목은 버튼을 렌더링하지 않는 계약(#221)이라 null을 반환한다.
 */
export type MapCoordinate = number | null | undefined

/** 네이버 지도가 모든 URL에 요구하는 호출자 식별 문자열. 네이버가 인증하지는 않는다. */
const NAVER_APP_NAME = 'NA-WA'

/** 네이버 지도 앱의 Android 패키지명. intent 스킴의 스토어 폴백이 이 값으로 동작한다. */
const NAVER_MAP_ANDROID_PACKAGE = 'com.nhn.android.nmap'

function isFiniteCoordinate(value: MapCoordinate): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

/**
 * 좌표 두 개가 모두 유효한 수인가.
 *
 * 네 빌더가 null을 돌려주는 조건과 버튼 묶음을 렌더링하지 않는 조건(#221)이 같은 검사를
 * 써야 해서 내보낸다. 화면 쪽이 `Number.isFinite`를 다시 쓰면 규칙이 두 곳으로 갈린다.
 */
export function hasMapCoordinates(latitude: MapCoordinate, longitude: MapCoordinate): boolean {
  return isFiniteCoordinate(latitude) && isFiniteCoordinate(longitude)
}

function toCoordinateQuery(latitude: MapCoordinate, longitude: MapCoordinate): string | null {
  if (!hasMapCoordinates(latitude, longitude)) return null

  return encodeURIComponent(`${latitude},${longitude}`)
}

/** 해당 좌표에 핀을 찍은 지도 화면을 연다. */
export function buildGoogleMapsSearchUrl(
  latitude: MapCoordinate,
  longitude: MapCoordinate,
): string | null {
  const query = toCoordinateQuery(latitude, longitude)

  return query === null ? null : `https://www.google.com/maps/search/?api=1&query=${query}`
}

/**
 * 현재 위치에서 해당 좌표까지의 대중교통 길찾기를 연다.
 *
 * `travelmode`를 비우면 구글이 기본값인 자동차 경로로 연다. 한국에서 구글은 자동차·도보
 * 경로를 제공하지 않아 사용자가 대중교통 탭을 다시 눌러야 하므로 `transit`으로 고정한다.
 */
export function buildGoogleMapsTransitRouteUrl(
  latitude: MapCoordinate,
  longitude: MapCoordinate,
): string | null {
  const query = toCoordinateQuery(latitude, longitude)

  return query === null
    ? null
    : `https://www.google.com/maps/dir/?api=1&destination=${query}&travelmode=transit`
}

/** 네이버 지도 진입 방식을 가르는 플랫폼. intent 스킴을 쓸 수 있는 곳은 Android뿐이다. */
export type MapPlatform = 'android' | 'other'

export function detectMapPlatform(
  userAgent: string = typeof navigator === 'undefined' ? '' : navigator.userAgent,
): MapPlatform {
  return /android/i.test(userAgent) ? 'android' : 'other'
}

/**
 * 조립한 쿼리를 플랫폼에 맞는 진입 URL로 감싼다.
 *
 * Android는 `package`를 지정한 intent 스킴이 앱 미설치 시 Play 스토어로 보내준다.
 * iOS에는 공식 폴백이 없어 스킴을 그대로 호출하며, 앱이 없으면 무반응이거나 브라우저
 * 알림이 뜬다(#269에서 감수하기로 한 제한).
 */
function toNaverAppUrl(actionPath: string, query: string, platform: MapPlatform): string {
  if (platform !== 'android') return `nmap://${actionPath}?${query}`

  return [
    `intent://${actionPath}?${query}#Intent`,
    'scheme=nmap',
    'action=android.intent.action.VIEW',
    'category=android.intent.category.BROWSABLE',
    `package=${NAVER_MAP_ANDROID_PACKAGE}`,
    'end',
  ].join(';')
}

/** 해당 좌표에 핀을 찍은 네이버 지도 앱 화면을 연다. 앱이 `name`을 필수로 요구한다. */
export function buildNaverMapPlaceUrl(
  latitude: MapCoordinate,
  longitude: MapCoordinate,
  name: string,
  platform: MapPlatform = detectMapPlatform(),
): string | null {
  if (!hasMapCoordinates(latitude, longitude)) return null

  const query = `lat=${latitude}&lng=${longitude}&name=${encodeURIComponent(name)}&appname=${NAVER_APP_NAME}`

  return toNaverAppUrl('place', query, platform)
}

/**
 * 현재 위치에서 해당 좌표까지의 대중교통 길찾기를 연다.
 *
 * 출발지(`slat`·`slng`)를 비우면 네이버 지도 앱이 사용자의 현재 위치를 출발지로 쓴다.
 */
export function buildNaverMapTransitRouteUrl(
  latitude: MapCoordinate,
  longitude: MapCoordinate,
  name: string,
  platform: MapPlatform = detectMapPlatform(),
): string | null {
  if (!hasMapCoordinates(latitude, longitude)) return null

  const query = `dlat=${latitude}&dlng=${longitude}&dname=${encodeURIComponent(name)}&appname=${NAVER_APP_NAME}`

  return toNaverAppUrl('route/public', query, platform)
}

/**
 * 웹 URL 전용 진입.
 *
 * 새 탭으로 열되 `noopener,noreferrer`를 붙여 열린 문서에 원본 창을 넘겨주지 않는다.
 * 앱 스킴은 현재 문서를 옮기는 `openMapAppUrl`이 따로 맡는다.
 */
export function openMapWebUrl(url: string | null): void {
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

/**
 * 앱 스킴·intent URL 전용 진입.
 *
 * 새 창으로 열면 Android Chrome에 빈 탭이 남고 iOS Safari는 팝업으로 막으므로, 웹
 * URL을 여는 `window.open`과 달리 현재 문서를 이동시킨다.
 */
export function openMapAppUrl(url: string | null): void {
  if (url) window.location.assign(url)
}
