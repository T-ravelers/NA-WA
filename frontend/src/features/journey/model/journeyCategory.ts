import type { Category } from '@/shared/ui/category'

import type { JourneyTimelineItem } from '../api/journeyApi'

const PLACE_KIND_CATEGORY: Record<string, Category> = {
  BEAUTY: 'beauty',
  MARKET: 'shopping',
  RESTAURANT: 'food',
  CAFE: 'food',
  뷰티매장: 'beauty',
  상설시장: 'shopping',
  비상설시장: 'shopping',
  복합쇼핑몰: 'shopping',
  백화점: 'shopping',
  '관광기념품/특산물판매점': 'shopping',
  아웃렛: 'shopping',
  '공방/공예품점': 'shopping',
  관광식당: 'food',
  서양식: 'food',
  일식: 'food',
  중식: 'food',
  기타외국식: 'food',
  '김밥 분식': 'food',
  분식: 'food',
  퓨전음식: 'food',
  이동음식: 'food',
  모범음식점: 'food',
  카페: 'food',
  찻집: 'food',
  제과: 'food',
  기타음료점: 'food',
  '바/펍': 'food',
}

/** Explore feature를 import하지 않고 같은 표시 규칙을 Journey 응답에 적용한다. */
export function categoryForJourneyItem(item: JourneyTimelineItem): Category {
  if (item.exploreItem.itemType === 'EVENT') {
    const eventKind = item.eventDetail?.eventKind?.trim().toUpperCase()
    if (eventKind === 'POPUP') return 'shopping'
    if (eventKind === 'FESTIVAL') return 'food'
    return 'show'
  }

  const placeKind = item.placeDetail?.placeKind?.trim()
  if (!placeKind) return 'show'

  return PLACE_KIND_CATEGORY[placeKind.toUpperCase()] ?? PLACE_KIND_CATEGORY[placeKind] ?? 'show'
}

export function categoryLabelKey(category: Category): string {
  return `spendingCategory.${category === 'show' ? 'SHOW' : category.toUpperCase()}`
}
