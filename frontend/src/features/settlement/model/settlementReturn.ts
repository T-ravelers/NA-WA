import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'

import { resolveDateRange, type SettlementDateRange } from './settlementHistoryFilter'
import type { SettlementSide } from './settlementList'

/**
 * 상세 화면이 "어디서 들어왔는지"를 주소에 남기는 표시.
 *
 * 고른 기간(`from`·`to`)만으로는 판단할 수 없다. 기간을 고르지 않고 전체 내역에서 바로
 * 들어온 경우에도 나갈 때는 전체 내역으로 돌아가야 하는데, 그때는 주소에 기간이 없다.
 */
const HISTORY_ORIGIN = 'history'

/** 주소 값은 같은 이름이 두 번 적히면 배열로 온다. 첫 값만 본다. */
function first(value: unknown): unknown {
  return Array.isArray(value) ? value[0] : value
}

/**
 * 전체 내역에서 상세를 열 때 함께 실어 보낼 주소 값.
 *
 * 보고 있던 쪽(`side`)과 좁혀 보던 기간을 그대로 들려 보낸다. 이것을 두고 오면 상세에서
 * 뒤로 갔을 때 사용자는 자기가 만들어 둔 화면을 잃고 정산 홈에서 다시 시작해야 한다.
 */
export function historyDetailQuery(
  side: SettlementSide,
  range: SettlementDateRange | null,
): LocationQueryRaw {
  return {
    side,
    origin: HISTORY_ORIGIN,
    ...(range === null ? {} : { from: range.from, to: range.to }),
  }
}

/**
 * 상세에서 뒤로 갈 곳.
 *
 * 전체 내역에서 들어왔으면 그 화면으로, 그렇지 않으면 정산 홈으로 돌아간다.
 *
 * 기간은 받은 값을 그대로 쓰지 않고 다시 읽는다. 사용자가 주소를 직접 고쳐 한쪽만 남기거나
 * 순서를 뒤집어 놨을 수 있는데, 그대로 돌려주면 돌아간 화면이 보여 주는 것과 주소에 적힌
 * 것이 어긋난다. 전체 내역 화면이 읽는 방식과 같은 규칙으로 맞춰 돌려준다.
 */
export function resolveDetailBackTarget(
  query: Record<string, unknown>,
  side: SettlementSide,
): RouteLocationRaw {
  if (first(query.origin) !== HISTORY_ORIGIN) {
    return { name: 'settlements', query: { side } }
  }

  const range = resolveDateRange(query.from, query.to)

  return {
    name: 'settlement-history',
    query: { side, ...(range === null ? {} : { from: range.from, to: range.to }) },
  }
}
