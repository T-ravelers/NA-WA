import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, watch, type Ref } from 'vue'

import {
  deleteAllNotifications,
  deleteNotification,
  fetchNotifications,
  fetchUnreadNotificationCount,
  markNotificationRead,
  readAllNotifications,
} from '../api/notificationApi'
import type { NotificationPageDto } from '../api/notificationApi.types'
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
  /** 쪽 전체를 한 번에 지목할 때 쓴다. 개별 쪽 키는 이 뒤에 커서가 붙는다. */
  lists: () => [...notificationKeys.all, 'list'] as const,
  page: (cursor: string | undefined) => [...notificationKeys.lists(), cursor ?? 'first'] as const,
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

/**
 * 알림 한 쪽. 화면에 들어갈 때, 그리고 "더 보기"를 누를 때 부른다.
 *
 * 쪽마다 캐시 키가 달라야 이미 본 쪽을 다시 받아 오지 않는다. 받은 쪽을 화면 쪽에서
 * 이어 붙이는 것은 지갑 거래 내역이 쓰는 방식과 같다 — 이 저장소에는 `useInfiniteQuery`
 * 선례가 없어 같은 모양을 따른다.
 */
export function useNotifications(cursor: Ref<string | undefined>) {
  const query = useQuery({
    queryKey: computed(() => notificationKeys.page(cursor.value)),
    queryFn: () => fetchNotifications(undefined, cursor.value),
  })

  const notifications = computed<AppNotification[]>(() =>
    (query.data.value?.notifications ?? []).map(toAppNotification),
  )

  const nextCursor = computed<string | null>(() => query.data.value?.nextCursor ?? null)

  return { ...query, notifications, nextCursor }
}

/**
 * 캐시에 쌓인 알림 쪽을 전부 버린다.
 *
 * 읽음·지우기가 성공하면 화면은 이미 낙관적으로 고쳐져 있지만, 캐시에 남은 쪽들은 서버가
 * 바뀌기 전 모습이다. 다음에 목록을 열 때 지운 알림이 되살아나 보이지 않도록 함께 버린다.
 */
function invalidateAll(queryClient: ReturnType<typeof useQueryClient>): void {
  void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
}

/**
 * 알림 하나를 읽음으로 바꾼다.
 *
 * 눌린 카드의 점을 **먼저** 지우고 요청을 보낸다. 서버를 기다렸다 지우면 이미 정산 상세로
 * 넘어간 뒤라 사용자는 아무 반응도 못 본다. 실패하면 되돌린다.
 *
 * 안 읽은 알림이 하나 줄었으므로 벨 개수도 함께 다시 받는다.
 */
export function useReadNotification() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (notificationId: string) => markNotificationRead(notificationId),
    onMutate: async (notificationId: string) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.all })
      const snapshot = queryClient.getQueriesData<NotificationPageDto>({
        queryKey: notificationKeys.lists(),
      })

      for (const [key, page] of snapshot) {
        if (page === undefined) continue
        queryClient.setQueryData<NotificationPageDto>(key, {
          ...page,
          notifications: page.notifications.map((notification) =>
            String(notification.id) === notificationId && !notification.readAt
              ? { ...notification, readAt: new Date().toISOString() }
              : notification,
          ),
        })
      }

      return { snapshot }
    },
    onError: (_error, _notificationId, context) => {
      for (const [key, page] of context?.snapshot ?? []) {
        queryClient.setQueryData(key, page)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() })
    },
  })
}

/** 목록 화면의 "모두 읽음". 목록과 벨 개수를 함께 다시 받는다. */
export function useReadAllNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: readAllNotifications,
    onSuccess: () => invalidateAll(queryClient),
  })
}

/**
 * 알림 하나를 지운다.
 *
 * 카드를 먼저 없애고 요청을 보낸다. 눌렀는데 아무 일도 일어나지 않는 것이 이번에 고치는
 * 문제의 출발점이라, 여기서 서버를 기다리게 두면 같은 인상을 준다. 실패하면 되돌린다.
 *
 * 안 읽은 알림을 지웠다면 벨 숫자도 줄어야 하므로 개수를 함께 다시 받는다.
 */
export function useDeleteNotification() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (notificationId: string) => deleteNotification(notificationId),
    onMutate: async (notificationId: string) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.all })
      const snapshot = queryClient.getQueriesData<NotificationPageDto>({
        queryKey: notificationKeys.lists(),
      })

      for (const [key, page] of snapshot) {
        if (page === undefined) continue
        queryClient.setQueryData<NotificationPageDto>(key, {
          ...page,
          notifications: page.notifications.filter(
            (notification) => String(notification.id) !== notificationId,
          ),
        })
      }

      return { snapshot }
    },
    onError: (_error, _notificationId, context) => {
      for (const [key, page] of context?.snapshot ?? []) {
        queryClient.setQueryData(key, page)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() })
    },
  })
}

/** 목록 화면의 "모두 지우기". */
export function useDeleteAllNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: deleteAllNotifications,
    onSuccess: () => invalidateAll(queryClient),
  })
}
