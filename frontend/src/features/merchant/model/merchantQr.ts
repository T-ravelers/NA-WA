/**
 * QR 생성 화면의 품목 입력.
 *
 * 품목·수량·단가는 **서버에 보내지 않는다.** 가맹점주가 합계를 손으로 더하지 않게 돕는
 * 입력 보조일 뿐이고, QR에는 계산된 합계 금액만 실린다. 그래서 품목 모양을 백엔드 계약과
 * 맞출 필요가 없고, 화면을 벗어나면 사라진다.
 */
export interface MerchantQrItem {
  /** v-for key 전용. 화면 안에서만 쓰는 값이라 서버 id와 무관하다. */
  id: number
  name: string
  quantity: number | null
  unitPrice: number | null
}

let nextItemId = 0

export function createEmptyItem(): MerchantQrItem {
  nextItemId += 1

  return { id: nextItemId, name: '', quantity: null, unitPrice: null }
}

/** 한 줄의 금액. 수량이나 단가가 비어 있으면 아직 0으로 본다. */
export function itemSubtotal(item: MerchantQrItem): number {
  const quantity = item.quantity ?? 0
  const unitPrice = item.unitPrice ?? 0

  if (quantity <= 0 || unitPrice <= 0) {
    return 0
  }

  return quantity * unitPrice
}

export function calculateTotal(items: MerchantQrItem[]): number {
  return items.reduce((total, item) => total + itemSubtotal(item), 0)
}

/**
 * QR을 만들 수 있는 합계인지 본다.
 *
 * 백엔드가 `amount > 0`을 요구하고 정수부 15자리를 넘는 금액을 거절한다. 화면에서 먼저
 * 걸러 서버 왕복 없이 알려준다.
 */
const MAX_AMOUNT = 10 ** 15 - 1

export function isValidTotal(total: number): boolean {
  return Number.isFinite(total) && total > 0 && total <= MAX_AMOUNT
}

/** 수량 입력. 숫자가 아닌 글자는 버리고, 비면 0이 아니라 `null`로 둔다. */
export function parseQuantity(value: string): number | null {
  const digits = value.replace(/\D/g, '')

  if (digits === '') {
    return null
  }

  return Number.parseInt(digits, 10)
}
