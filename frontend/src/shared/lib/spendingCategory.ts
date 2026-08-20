/**
 * 소비 카테고리.
 *
 * 백엔드 `SpendingCategory` enum과 1:1이고, 값 집합의 정본은
 * `backend/docs/SPENDING_CATEGORY.md`다.
 *
 * **`shared/ui/category.ts`의 `Category`와 다른 타입이다.** 앞의 네 값이 Explore
 * 소비영역(`food`·`shopping`·`beauty`·`show`)과 같은 어휘를 쓰는 것은 사용자가 두
 * 화면에서 같은 말을 보게 하려는 것뿐이다. Explore는 탐색 아이템의 분류고 이쪽은 결제
 * 건의 분류라, 한쪽을 늘리는 결정이 다른 쪽을 끌고 가면 안 된다.
 *
 * Wallet(결제·거래 상세)과 Report(카테고리 구성·칭호)가 함께 쓰므로 shared에 둔다.
 */
export const SPENDING_CATEGORIES = [
  'FOOD',
  'SHOPPING',
  'BEAUTY',
  'SHOW',
  'TRANSPORT',
  'STAY',
  'OTHER',
] as const

export type SpendingCategory = (typeof SPENDING_CATEGORIES)[number]

/** 카테고리를 고르지 않은 결제도 있다. 서버가 저장할 때 접는 값과 같다. */
export const DEFAULT_SPENDING_CATEGORY: SpendingCategory = 'OTHER'

export function isSpendingCategory(value: string): value is SpendingCategory {
  return (SPENDING_CATEGORIES as readonly string[]).includes(value)
}

/**
 * 서버가 보낸 값을 카테고리로 좁힌다.
 *
 * 서버가 allow-list로 막고 있어 목록 밖의 값은 오지 않아야 하지만, 이 기능 이전에
 * 만들어진 거래는 컬럼이 비어 있다. 좁히지 못한 값은 `OTHER`로 접는다 — 화면에 코드가
 * 날것으로 보이는 것보다 낫다.
 */
export function toSpendingCategory(value: string | null | undefined): SpendingCategory {
  const normalized = value?.trim().toUpperCase() ?? ''

  return isSpendingCategory(normalized) ? normalized : DEFAULT_SPENDING_CATEGORY
}

/** 카테고리 표시명의 i18n key. 문구는 `shared/i18n`이 소유한다. */
export function spendingCategoryLabelKey(value: string | null | undefined): string {
  return `spendingCategory.${toSpendingCategory(value)}`
}
