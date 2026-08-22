import type { LocationQuery } from 'vue-router'

/**
 * "여기로 돌아와 달라"며 보낼 때 쓰는 query 규약. 여정 생성과 충전이 받는 쪽이다.
 *
 * 돌아갈 화면이 route param을 쓰면 `returnParams=<key>:<value>`도 함께 싣는다(여럿이면
 * `,`로 잇는다). 받는 쪽은 이것을 `params`로 풀어 주소를 만들 뿐이고, 값의 뜻은 여전히
 * 호출자만 안다 — 그래서 이 규약을 받는 feature는 호출자를 몰라도 된다.
 *
 * 파싱이 두 화면에 각각 있으면 한쪽만 고쳐져 어긋난다. 규칙은 여기 하나만 둔다.
 */
export const RETURN_ROUTE_NAME_KEY = 'returnRouteName'
export const RETURN_PARAMS_KEY = 'returnParams'

function firstValue(value: unknown): string | null {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' && raw !== '' ? raw : null
}

/** 돌아갈 route 이름. 없으면 호출자가 없는 것으로 친다. */
export function readReturnRouteName(query: LocationQuery): string | null {
  return firstValue(query[RETURN_ROUTE_NAME_KEY])
}

/** 돌아갈 화면의 path param. 형식이 어긋난 조각은 조용히 버린다. */
export function readReturnParams(query: LocationQuery): Record<string, string> {
  const value = firstValue(query[RETURN_PARAMS_KEY])
  if (value === null) return {}

  const entries = value.split(',').flatMap((pair) => {
    const separator = pair.indexOf(':')
    if (separator <= 0) return []
    return [[pair.slice(0, separator), pair.slice(separator + 1)] as const]
  })

  return Object.fromEntries(entries)
}

/** 규약 key는 호출자 화면의 것이 아니므로 돌려줄 query에서 뺀다. */
export function withoutReturnContract(query: LocationQuery): LocationQuery {
  const rest = { ...query }
  delete rest[RETURN_ROUTE_NAME_KEY]
  delete rest[RETURN_PARAMS_KEY]
  return rest
}

/** 보내는 쪽이 자기 param을 규약 형식으로 만든다. */
export function serializeReturnParams(params: Record<string, string | number>): string {
  return Object.entries(params)
    .map(([key, value]) => `${key}:${String(value)}`)
    .join(',')
}
