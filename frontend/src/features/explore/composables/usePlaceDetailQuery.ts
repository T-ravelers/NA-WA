import { useQuery } from '@tanstack/vue-query'
import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchPlaceDetail } from '../api/exploreApi'
import { exploreKeys } from '../model/exploreKeys'

export function usePlaceDetailQuery(
  placeId: MaybeRefOrGetter<number | string | undefined>,
  language: MaybeRefOrGetter<string>,
) {
  const countedItemId = ref<string | null>(null)

  return useQuery({
    queryKey: computed(() => exploreKeys.placeDetail(String(toValue(placeId)), toValue(language))),
    queryFn: async () => {
      const id = toValue(placeId) as number | string
      /*
       * 조회수는 한 항목당 한 번만 센다. queryKey에 language가 들어 있어 언어를 바꾸면
       * 새 요청이 나가는데, 같은 사람이 같은 화면을 계속 보고 있는 것이라 조회가 아니다.
       *
       * 화면을 떠나지 않고 다른 항목으로 넘어가는 길이 있어 boolean이 아니라 센 항목을
       * 기억한다. 하나로 두면 두 번째 항목부터 영영 세지 않는다. 성공한 뒤에 기록해서
       * 첫 호출이 실패하면 다시 시도할 때 세도록 한다.
       */
      const shouldCount = countedItemId.value !== String(id)
      const detail = await fetchPlaceDetail(id, toValue(language), { countView: shouldCount })
      if (shouldCount) countedItemId.value = String(id)
      return detail
    },
    enabled: computed(() => {
      const value = toValue(placeId)
      return value !== undefined && String(value).trim() !== ''
    }),
    staleTime: 30_000,
    /*
     * 창으로 돌아왔다고 다시 부르지 않는다.
     *
     * 이 호출은 조회수를 1 올린다. 자동 재요청을 켜 두면 화면을 켜 둔 채 다른 앱에
     * 다녀오기만 해도 조회수가 오른다 — 사람이 상세를 다시 연 것이 아니다.
     * Place 상세는 자주 바뀌지 않아 조금 오래된 값을 보여 주는 쪽이 낫다.
     */
    refetchOnWindowFocus: false,
  })
}
