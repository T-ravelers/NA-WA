/**
 * 카드 하트 옆에 보이는 찜한 사람 수를 짧게 적는다.
 *
 * 999까지는 그대로, 천 단위부터는 `K`에 소수 첫째 자리(백의 자리)까지 붙인다. 백만부터는
 * `M`이다. 반올림하지 않고 버린다 — 1,950명을 `2K`로 올려 적으면 실제보다 많다고 단언한다.
 * 소수 자리가 0이면 떼어 `1K`로 적는다.
 *
 * 0은 빈 문자열이다. 아무도 찜하지 않은 항목에 `0`을 띄우면 인기 없음만 강조된다.
 */
export function formatFavoriteCount(count: number | null | undefined): string {
  if (count === null || count === undefined || !Number.isFinite(count) || count <= 0) return ''
  if (count < 1_000) return String(Math.floor(count))
  if (count < 1_000_000) return `${oneDecimal(count / 1_000)}K`
  return `${oneDecimal(count / 1_000_000)}M`
}

function oneDecimal(value: number): string {
  return String(Math.floor(value * 10) / 10)
}
