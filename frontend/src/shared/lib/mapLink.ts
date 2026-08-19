/**
 * 지도 앱 진입 URL 조립.
 *
 * 구글 지도 범용 URL(Maps URLs)은 모바일 웹에서 앱이 설치돼 있으면 앱으로, 없으면
 * 웹으로 열리므로 설치 여부 분기가 필요 없다. 좌표가 없는 항목은 버튼을 렌더링하지
 * 않는 계약(#221)이라 null을 반환한다.
 */
export type MapCoordinate = number | null | undefined

function toCoordinateQuery(latitude: MapCoordinate, longitude: MapCoordinate): string | null {
  if (typeof latitude !== 'number' || !Number.isFinite(latitude)) return null
  if (typeof longitude !== 'number' || !Number.isFinite(longitude)) return null

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

/** 현재 위치에서 해당 좌표까지의 길찾기 화면을 연다. 한국에서는 대중교통만 동작한다. */
export function buildGoogleMapsDirectionsUrl(
  latitude: MapCoordinate,
  longitude: MapCoordinate,
): string | null {
  const query = toCoordinateQuery(latitude, longitude)

  return query === null ? null : `https://www.google.com/maps/dir/?api=1&destination=${query}`
}
