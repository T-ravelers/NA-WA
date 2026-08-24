import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchEventDetail, fetchPlaceDetail } from '../api/exploreApi'

/**
 * 약속 생성 폼이 "이 항목 자리에서 그대로 만난다"를 보여 주기 위해 읽는 위치다.
 *
 * Event는 공연장 이름(`venueName`)이, Place는 상호명(`name`)이 사람이 찾아갈 이름이다.
 * 둘 다 없으면 도로명 주소를 대신 쓴다 — 이름 없는 장소보다는 주소가 낫다.
 */
export function useExploreItemDetailQuery(
  itemId: MaybeRefOrGetter<number | null>,
  itemType: MaybeRefOrGetter<'EVENT' | 'PLACE' | null>,
) {
  return useQuery({
    queryKey: computed(() => ['explore', 'itemDetail', toValue(itemType), toValue(itemId)]),
    queryFn: async () => {
      const id = toValue(itemId)
      const type = toValue(itemType)
      if (id === null || type === null) throw new Error('An item is required.')

      if (type === 'EVENT') {
        const event = await fetchEventDetail(id)
        return {
          placeName: event.venueName ?? event.addressRoad,
          addressRoad: event.addressRoad,
          startDate: event.startDate,
          endDate: event.endDate,
        }
      }

      // Place는 운영 기간이 없다. 서버도 PLACE에는 기간 검사를 걸지 않는다.
      const place = await fetchPlaceDetail(id)
      return {
        placeName: place.name ?? place.addressRoad,
        addressRoad: place.addressRoad,
        startDate: null,
        endDate: null,
      }
    },
    enabled: computed(() => toValue(itemId) !== null && toValue(itemType) !== null),
    staleTime: 5 * 60_000,
    retry: false,
  })
}
