import { useQuery } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { fetchEventList, fetchPlaceList } from '../api/exploreApi'
import { findExploreRegionLabelKey } from './exploreRegions'

/**
 * 프로필의 찜 탭이 읽는 목록.
 *
 * 찜 여부는 서버가 소유한다(#230). 목록 조회에 `savedOnly`를 실어 그대로 재사용하므로
 * 찜 전용 엔드포인트를 따로 만들지 않는다.
 *
 * 프로필은 넘겨보기용 목록이라 첫 쪽만 읽는다. 더 담은 사람은 Discover의 찜 필터에서
 * 쪽을 넘겨 본다 — 여기에 무한 스크롤을 또 만들지 않는다.
 */
const SAVED_PAGE_SIZE = 30

/** Event·Place가 공통으로 쓰는 지역 표기. `EventCard`·`PlaceCard`와 같은 규칙이다. */
function useRegionLabel() {
  const { t } = useI18n()

  return (parts: (string | null)[]): string | null => {
    const label = parts
      .filter((value): value is string => Boolean(value))
      .map((value) => {
        const labelKey = findExploreRegionLabelKey(value)
        return labelKey ? t(labelKey) : value
      })
      .join(' · ')

    return label === '' ? null : label
  }
}

export function useSavedExploreItemsQuery(kind: Ref<'EVENT' | 'PLACE'>, enabled: Ref<boolean>) {
  const regionLabel = useRegionLabel()
  const { locale } = useI18n()

  const query = useQuery({
    // 제목은 서버가 요청 언어로 번역해 내려주므로(#531) 로케일이 바뀌면 다시 받아 온다.
    // Discover가 같은 목록에 `language`를 실어 보내므로, 여기서 빼면 두 화면이 같은
    // 항목을 서로 다른 언어로 보여 준다(#461).
    //
    // 지역명은 아직 원문 코드로 내려온다. 캐시에는 그 코드를 그대로 두고 표시할 때
    // 번역한다 — 번역한 문자열을 넣으면 언어를 바꾼 뒤에도 이전 언어가 남는다.
    queryKey: computed(() => ['explore', 'saved', kind.value, locale.value] as const),
    queryFn: async () => {
      if (kind.value === 'EVENT') {
        const page = await fetchEventList({
          savedOnly: true,
          size: SAVED_PAGE_SIZE,
          language: locale.value,
        })
        return page.content.map((event) => ({
          itemId: event.itemId,
          title: event.title,
          regionParts: [event.region1, event.region2, event.region3],
          thumbnailUrl: event.thumbnailUrl,
        }))
      }

      const page = await fetchPlaceList({
        savedOnly: true,
        size: SAVED_PAGE_SIZE,
        language: locale.value,
      })
      return page.content.map((place) => ({
        itemId: place.itemId,
        title: place.name,
        regionParts: [place.region1, place.region2, place.region3],
        thumbnailUrl: place.thumbnailUrl,
      }))
    },
    enabled,
    staleTime: 30_000,
  })

  // `t()`를 computed 안에서 부르므로 로케일이 바뀌면 지역명도 다시 계산된다.
  const data = computed(() =>
    query.data.value?.map(({ regionParts, ...item }) => ({
      ...item,
      subtitle: regionLabel(regionParts),
    })),
  )

  return {
    data,
    isPending: query.isPending,
    isError: query.isError,
    refetch: () => void query.refetch(),
  }
}
