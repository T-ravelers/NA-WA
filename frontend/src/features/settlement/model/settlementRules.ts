import type { ItemizedSettlementItem } from './settlement'

function parseQuantity(value: string): bigint | null {
  const match = /^(\d+)(?:\.(\d{1,3}))?$/.exec(value.trim())
  if (match === null) return null
  const integer = match[1] ?? '0'
  const fraction = (match[2] ?? '').padEnd(3, '0')
  try {
    return BigInt(integer) * 1000n + BigInt(fraction)
  } catch {
    return null
  }
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
