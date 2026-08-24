/** 담을 수 있는 날짜 구간. 양 끝을 포함한다. */
export interface JourneyAddDateRange {
  start: string
  end: string
}

/** 기간을 가진 것. Place처럼 운영 기간이 없으면 양쪽이 `null`이다. */
interface ItemPeriod {
  startDate: string | null
  endDate: string | null
}

interface JourneyPeriod {
  startDate: string
  endDate: string
}

/**
 * 항목 운영 기간 ∩ 여정 기간. 겹치는 날이 하루도 없으면 `null`.
 *
 * 담기가 성립하려면 방문 날짜가 **두 기간을 모두** 만족해야 한다. 서버도 둘을 따로
 * 보고 각각 `JOURNEY-007`과 `JOURNEY-012`로 거절한다. 달력이 여정 기간만 보고
 * 열어주면 사용자는 확정 버튼을 누른 뒤에야 실패를 알게 된다.
 *
 * Discover의 담기와 약속 생성이 같은 규칙을 쓴다. 서버에서도 두 경로가
 * `JourneyExploreItem.coversVisitDate` 하나를 공유하므로, 달력도 한 곳에서 계산해야
 * 두 화면이 어긋나지 않는다 — 그래서 feature가 아니라 shared에 있다.
 *
 * `YYYY-MM-DD`는 사전순 비교가 곧 날짜 비교라 문자열로 비교한다. `Date`로 바꾸면
 * 타임존이 끼어들어 하루가 밀린다.
 *
 * 항목 쪽 경계가 `null`이면 그쪽으로는 제한이 없다는 뜻이라 여정 경계를 그대로 쓴다.
 * 운영 기간이 없는 Place는 양쪽이 `null`이라 여정 기간 전체가 열리고, 상시 이벤트는
 * `endDate`만 `null`이라 시작일 이후만 열린다 — 상한이 없다고 하한까지 없는 것이
 * 아니다.
 */
export function intersectItemJourneyPeriod(
  item: ItemPeriod,
  journey: JourneyPeriod,
): JourneyAddDateRange | null {
  const start = maxDate(item.startDate ?? journey.startDate, journey.startDate)
  const end = minDate(item.endDate ?? journey.endDate, journey.endDate)

  return start <= end ? { start, end } : null
}

function maxDate(left: string, right: string): string {
  return left >= right ? left : right
}

function minDate(left: string, right: string): string {
  return left <= right ? left : right
}
