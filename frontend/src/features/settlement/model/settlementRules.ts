import type { ItemizedSettlementItem } from './settlement'

const QUANTITY_DECIMALS = 3
const PRICE_DECIMALS = 4
/** 단가 × 수량이라 소수 자릿수도 더해진다. */
const TOTAL_DECIMALS = PRICE_DECIMALS + QUANTITY_DECIMALS

/**
 * 소수를 정수로 바꿔 센다.
 *
 * 0.1 + 0.2가 0.3이 아닌 부동소수점으로는 금액을 비교할 수 없다. 정해진 자릿수만큼 곱해
 * 정수로 만든 뒤 정수끼리 비교한다.
 */
function parseScaled(value: string, decimals: number): bigint | null {
  const match = new RegExp(`^(\\d+)(?:\\.(\\d{1,${decimals}}))?$`).exec(value.trim())
  if (match === null) return null
  const integer = match[1] ?? '0'
  const fraction = (match[2] ?? '').padEnd(decimals, '0')
  try {
    return BigInt(integer) * 10n ** BigInt(decimals) + BigInt(fraction)
  } catch {
    return null
  }
}

function parseQuantity(value: string): bigint | null {
  return parseScaled(value, QUANTITY_DECIMALS)
}

function hasValidPrice(value: string): boolean {
  return (
    /^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/.test(value.trim()) && !/^0(?:\.0+)?$/.test(value.trim())
  )
}

/** Validates only client-entered ITEMIZED data; it never calculates money shares. */
export function validateItemizedItems(
  items: ItemizedSettlementItem[],
  participantIds?: Set<string>,
): { valid: boolean; invalidItemIndexes: number[] } {
  const invalidItemIndexes = items.flatMap((item, index) => {
    const total = parseQuantity(item.quantity)
    const allocations = item.allocations.map((allocation) => parseQuantity(allocation.quantity))
    const allocationTotal = allocations.every((quantity) => quantity !== null)
      ? allocations.reduce((sum, quantity) => sum + (quantity ?? 0n), 0n)
      : null
    const invalid =
      item.name.trim().length === 0 ||
      item.name.length > 200 ||
      !hasValidPrice(item.unitPrice) ||
      total === null ||
      total <= 0n ||
      allocationTotal === null ||
      allocationTotal !== total ||
      item.allocations.some(
        (allocation, allocationIndex) =>
          allocations[allocationIndex] === null ||
          allocations[allocationIndex]! <= 0n ||
          (participantIds !== undefined && !participantIds.has(allocation.appointmentMemberId)),
      )
    return invalid ? [index] : []
  })

  return { valid: invalidItemIndexes.length === 0 && items.length > 0, invalidItemIndexes }
}

/**
 * 품목 금액의 합이 원거래 금액과 맞는지 본다.
 *
 * 서버는 둘이 정확히 같을 때만 정산을 만든다. 화면에서 걸러 주지 않으면 사용자가 마지막
 * 제출 단계까지 다 채운 뒤에야 거절당한다.
 *
 * 값을 읽을 수 없으면 `null`을 돌려주고 막지 않는다. 형식 오류는 품목별 검증이 이미
 * 잡으며, 여기서 겹쳐 막으면 원인이 헷갈린다.
 */
export function compareItemizedTotal(
  items: ItemizedSettlementItem[],
  sourceAmount: string,
): { matches: boolean; total: string } | null {
  const source = parseScaled(sourceAmount, TOTAL_DECIMALS)
  if (source === null || items.length === 0) return null

  let total = 0n

  for (const item of items) {
    const price = parseScaled(item.unitPrice, PRICE_DECIMALS)
    const quantity = parseQuantity(item.quantity)
    if (price === null || quantity === null) return null
    total += price * quantity
  }

  return { matches: total === source, total: formatScaled(total, TOTAL_DECIMALS) }
}

/** 정수로 세던 값을 다시 소수 문자열로 돌린다. 뒤에 남는 0은 떼어 읽기 쉽게 둔다. */
function formatScaled(value: bigint, decimals: number): string {
  const unit = 10n ** BigInt(decimals)
  const fraction = (value % unit).toString().padStart(decimals, '0').replace(/0+$/, '')
  return fraction === '' ? `${value / unit}` : `${value / unit}.${fraction}`
}

export interface ItemizedShare {
  appointmentMemberId: string
  amount: string
}

/**
 * 품목별 정산에서 사람마다 얼마를 맡는지 계산한다.
 *
 * 단가 × 배분 수량을 더할 뿐이라 나누어떨어지지 않는 문제가 없다. 서버도 같은 식으로
 * 계산하므로 화면에서 미리 보여줘도 어긋나지 않는다. 균등 분할(EQUAL)은 나머지를 누구에게
 * 줄지가 통화 단위에 달려 있는데 화면은 그 단위를 모르므로 여기서 다루지 않는다.
 *
 * `requested`는 원결제자 몫을 뺀 금액이다. 자기 자신에게 청구하지는 않기 때문이다.
 */
export function summarizeItemizedShares(
  items: ItemizedSettlementItem[],
  payerAppointmentMemberId: string,
): { shares: ItemizedShare[]; total: string; requested: string } | null {
  if (items.length === 0) return null

  const scaled = new Map<string, bigint>()
  let total = 0n

  for (const item of items) {
    const price = parseScaled(item.unitPrice, PRICE_DECIMALS)
    if (price === null) return null

    for (const allocation of item.allocations) {
      const quantity = parseQuantity(allocation.quantity)
      if (quantity === null) return null

      const amount = price * quantity
      total += amount
      scaled.set(
        allocation.appointmentMemberId,
        (scaled.get(allocation.appointmentMemberId) ?? 0n) + amount,
      )
    }
  }

  return {
    shares: [...scaled].map(([appointmentMemberId, amount]) => ({
      appointmentMemberId,
      amount: formatScaled(amount, TOTAL_DECIMALS),
    })),
    total: formatScaled(total, TOTAL_DECIMALS),
    requested: formatScaled(total - (scaled.get(payerAppointmentMemberId) ?? 0n), TOTAL_DECIMALS),
  }
}
