import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchEventDetail } from '../api/exploreApi'
import { exploreKeys } from '../model/exploreKeys'

export function useEventDetailQuery(
  eventId: MaybeRefOrGetter<number | string | undefined>,
  language: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => exploreKeys.eventDetail(String(toValue(eventId)), toValue(language))),
    queryFn: () =>
      fetchEventDetail(toValue(eventId) as number | string, toValue(language), { countView: true }),
    enabled: computed(() => {
      const value = toValue(eventId)
      return value !== undefined && String(value).trim() !== ''
    }),
    staleTime: 30_000,
    /*
     * 창으로 돌아왔다고 다시 부르지 않는다.
     *
     * 이 호출은 조회수를 1 올린다. 자동 재요청을 켜 두면 화면을 켜 둔 채 다른 앱에
     * 다녀오기만 해도 조회수가 오른다 — 사람이 상세를 다시 연 것이 아니다.
     * Event 상세는 자주 바뀌지 않아 조금 오래된 값을 보여 주는 쪽이 낫다.
     */
    refetchOnWindowFocus: false,
  })
}
