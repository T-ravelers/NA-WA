import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from '@tanstack/vue-query'
import { computed, watch } from 'vue'

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
  /** 받아 온 쪽 전부가 이 키 하나에 함께 들어 있다. */
  list: () => [...notificationKeys.all, 'list'] as const,
  unreadCount: () => [...notificationKeys.all, 'unread-count'] as const,
}

/** 캐시에 들어 있는 알림 목록. 쪽이 여러 개여도 항목 하나다. */
type CachedPages = InfiniteData<NotificationPageDto, string | undefined>

/**
 * 캐시에 쌓인 모든 쪽의 알림을 한 번에 고쳐 쓰고, 되돌릴 수 있게 원본을 돌려준다.
 *
 * 화면이 목록을 따로 베껴 두지 않고 이 캐시만 보기 때문에, 여기만 고치면 눈앞의 목록도
 * 같이 바뀐다. 실패했을 때 돌려놓을 곳도 한 군데뿐이다.
 */
function patchCachedPages(
  queryClient: QueryClient,
  patch: (
    notifications: NotificationPageDto['notifications'],
  ) => NotificationPageDto['notifications'],
): CachedPages | undefined {
  const snapshot = queryClient.getQueryData<CachedPages>(notificationKeys.list())
  if (snapshot === undefined) return undefined

  queryClient.setQueryData<CachedPages>(notificationKeys.list(), {
    ...snapshot,
    pages: snapshot.pages.map((page) => ({
      ...page,
      notifications: patch(page.notifications),
    })),
  })

  return snapshot
}

function restoreCachedPages(queryClient: QueryClient, snapshot: CachedPages | undefined): void {
  if (snapshot !== undefined) queryClient.setQueryData(notificationKeys.list(), snapshot)
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
 * 알림 목록. 화면에 들어갈 때 첫 쪽을 받고, "더 보기"로 그 뒤를 이어 받는다.
 *
 * 받아 온 쪽을 화면이 따로 베껴 두지 않고 **캐시 한 항목에 모아 둔다.** 화면 쪽에 목록을
 * 하나 더 들고 있으면, 지우기가 실패해 캐시를 되돌려도 그 사본은 그대로 남아 눈앞의 목록과
 * 실제가 어긋난다. 사본을 없애면 되돌리기가 곧 화면 복구가 된다.
 */
export function useNotifications() {
  const query = useInfiniteQuery({
    queryKey: notificationKeys.list(),
    queryFn: ({ pageParam }) => fetchNotifications(undefined, pageParam),
    initialPageParam: undefined as string | undefined,
    // null과 빈 문자열은 둘 다 "더 없다"는 뜻이다. undefined를 주면 hasNextPage가 false가 된다.
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor === null ||
      lastPage.nextCursor === undefined ||
      lastPage.nextCursor === ''
        ? undefined
        : lastPage.nextCursor,
  })

  const notifications = computed<AppNotification[]>(() =>
    (query.data.value?.pages ?? []).flatMap((page) => page.notifications.map(toAppNotification)),
  )

  return { ...query, notifications }
}

/**
 * 캐시에 쌓인 알림을 전부 버리고 첫 쪽부터 다시 받는다.
 *
 * 일괄 동작이 끝난 뒤에 쓴다. 사용자가 스스로 누른 것이라 목록이 새로 그려져도 놀랄 일이
 * 없고, 서버가 실제로 무엇을 바꿨는지 그대로 받아 오는 편이 맞다.
 */
function invalidateAll(queryClient: QueryClient): void {
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
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })
      const readAt = new Date().toISOString()

      return {
        snapshot: patchCachedPages(queryClient, (notifications) =>
          notifications.map((notification) =>
            String(notification.id) === notificationId && !notification.readAt
              ? { ...notification, readAt }
              : notification,
          ),
        ),
      }
    },
    onError: (_error, _notificationId, context) =>
      restoreCachedPages(queryClient, context?.snapshot),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() })
    },
  })
}

/** 목록 화면의 "모두 읽음". 점을 먼저 지우고, 실패하면 되돌린다. */
export function useReadAllNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: readAllNotifications,
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })
      const readAt = new Date().toISOString()

      return {
        snapshot: patchCachedPages(queryClient, (notifications) =>
          notifications.map((notification) =>
            notification.readAt ? notification : { ...notification, readAt },
          ),
        ),
      }
    },
    onError: (_error, _variables, context) => restoreCachedPages(queryClient, context?.snapshot),
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
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })

      return {
        snapshot: patchCachedPages(queryClient, (notifications) =>
          notifications.filter((notification) => String(notification.id) !== notificationId),
        ),
      }
    },
    onError: (_error, _notificationId, context) =>
      restoreCachedPages(queryClient, context?.snapshot),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() })
    },
  })
}

/** 목록 화면의 "모두 지우기". 목록을 먼저 비우고, 실패하면 되돌린다. */
export function useDeleteAllNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: deleteAllNotifications,
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })

      return { snapshot: patchCachedPages(queryClient, () => []) }
    },
    onError: (_error, _variables, context) => restoreCachedPages(queryClient, context?.snapshot),
    onSuccess: () => invalidateAll(queryClient),
  })
}
