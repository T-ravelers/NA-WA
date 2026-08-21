import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, watch } from 'vue'

import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  readAllNotifications,
} from '../api/notificationApi'
import { toAppNotification, type AppNotification } from './notification'
import { useNotificationSettlementIntegration } from './settlementIntegration'

/**
 * 벨 숫자를 다시 물어보는 주기.
 *
 * 이 폴링은 탭이 백그라운드로 가면 멈춘다(TanStack의 `refetchIntervalInBackground` 기본값이
 * `false`다). 즉 사용자가 화면을 실제로 보고 있는 동안에만 도는데, 같은 자리에서 정산
 * 요청을 주고받는 그 상황이 바로 빨리 와야 하는 상황이다. 요청 하나는 인덱스를 타는
 * 개수 세기 한 번이라 이 주기의 비용이 문제가 되지 않는다.
 *
 * 백엔드 스케줄러의 60초를 그대로 가져오지 않는다. 그 값은 "아무도 실시간으로 보고 있지
 * 않은 쓰기 작업이라 주기가 길어도 된다"는 이유로 고른 것이라 전제가 정반대다.
 */
export const UNREAD_COUNT_POLL_INTERVAL_MS = 15_000

export const notificationKeys = {
  all: ['notifications'] as const,
  list: () => [...notificationKeys.all, 'list'] as const,
  unreadCount: () => [...notificationKeys.all, 'unread-count'] as const,
}

/**
 * 벨에 띄울 안 읽은 알림 개수.
 *
 * `staleTime`을 두지 않는 것이 중요하다. 이 저장소는 대부분의 쿼리에 30초를 붙이지만,
 * 여기에 붙이면 창 포커스 재조회가 그만큼 죽어서 앱으로 돌아와도 배지가 옛날 값으로 남는다.
 * 체감의 대부분은 주기적 폴링이 아니라 "다시 열었을 때 바로 맞는가"에서 나온다.
 */
export function useUnreadNotificationCount() {
  const { invalidateSettlements } = useNotificationSettlementIntegration()

  const query = useQuery({
    queryKey: notificationKeys.unreadCount(),
    queryFn: async () => (await fetchUnreadNotificationCount()).count,
    refetchInterval: UNREAD_COUNT_POLL_INTERVAL_MS,
    staleTime: 0,
  })

  /*
   * 숫자가 늘어난 순간에만 정산 캐시를 낡은 것으로 표시한다.
   *
   * 줄어드는 것은 사용자가 읽었다는 뜻이라 정산에는 아무 일도 일어나지 않았다. 그때까지
   * 무효화하면 목록을 열 때마다 서버를 괜히 다시 부른다.
   */
  let previousCount: number | null = null
  watch(query.data, (count) => {
    if (count === undefined) return
    if (previousCount !== null && count > previousCount) invalidateSettlements()
    previousCount = count
  })

  return query
}

/** 알림 목록. 화면에 들어갈 때만 부른다. */
export function useNotifications() {
  const query = useQuery({
    queryKey: notificationKeys.list(),
    queryFn: () => fetchNotifications(),
  })

  const notifications = computed<AppNotification[]>(() =>
    (query.data.value ?? []).map(toAppNotification),
  )

  return { ...query, notifications }
}

/**
 * 목록에 들어갈 때 전부 읽음으로 바꾼다.
 *
 * 벨 개수만 다시 받고 **목록은 건드리지 않는다.** 목록까지 무효화하면 방금 그린 화면을
 * 곧바로 다시 받아 오는데, 그 응답은 전부 읽음 상태라 안 읽음 표시가 눈앞에서 지워진다.
 * 사용자가 알림 목록을 여는 이유가 바로 "무엇이 새로 왔는지" 보는 것이라, 그걸 지워 버리면
 * 요청 한 번을 더 쓰고 화면은 더 나빠진다. 목록은 다음에 들어올 때 새로 받는다.
 */
export function useReadAllNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: readAllNotifications,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() })
    },
  })
}
