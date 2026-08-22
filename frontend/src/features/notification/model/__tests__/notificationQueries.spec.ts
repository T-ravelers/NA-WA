import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { queryClient } from '@/app/query/client'

import { notificationSettlementIntegrationKey } from '../settlementIntegration'

const fetchUnreadNotificationCount = vi.fn()
const readAllNotifications = vi.fn()
const markNotificationRead = vi.fn()
const deleteNotification = vi.fn()
const deleteAllNotifications = vi.fn()

vi.mock('../../api/notificationApi', () => ({
  fetchUnreadNotificationCount: () => fetchUnreadNotificationCount(),
  fetchNotifications: vi.fn(),
  readAllNotifications: () => readAllNotifications(),
  markNotificationRead: (id: string) => markNotificationRead(id),
  deleteNotification: (id: string) => deleteNotification(id),
  deleteAllNotifications: () => deleteAllNotifications(),
}))

const {
  UNREAD_COUNT_POLL_INTERVAL_MS,
  notificationKeys,
  useDeleteAllNotifications,
  useDeleteNotification,
  useReadAllNotifications,
  useReadNotification,
  useUnreadNotificationCount,
} = await import('../notificationQueries')

const invalidateSettlements = vi.fn()

const BellHost = defineComponent({
  setup() {
    const query = useUnreadNotificationCount()
    return () => query.data.value ?? '-'
  },
})

function mountBell() {
  return mount(BellHost, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: {
        [notificationSettlementIntegrationKey as symbol]: { invalidateSettlements },
      },
    },
  })
}

beforeEach(() => {
  queryClient.clear()
  fetchUnreadNotificationCount.mockReset()
  readAllNotifications.mockReset()
  readAllNotifications.mockResolvedValue({ updatedCount: 2 })
  markNotificationRead.mockReset()
  markNotificationRead.mockResolvedValue(undefined)
  deleteNotification.mockReset()
  deleteNotification.mockResolvedValue(undefined)
  deleteAllNotifications.mockReset()
  deleteAllNotifications.mockResolvedValue({ deletedCount: 2 })
  invalidateSettlements.mockReset()
})

/** 캐시에 들어 있는 알림 한 쪽. 서버 DTO 모양 그대로다. */
function page(...ids: string[]) {
  return {
    notifications: ids.map((id) => ({
      id,
      type: 'SETTLEMENT_REQUESTED',
      settlementId: '1',
      actorName: 'Ari',
      gatheringName: 'Dinner',
      amount: 30,
      currencyCode: 'KRW',
      readAt: null,
      createdAt: '2026-08-21T12:00:00',
    })),
    nextCursor: null,
  }
}

/** 뮤테이션 하나를 부르고 정리까지 기다린다. */
async function run(mutate: () => void) {
  const wrapper = mount(
    {
      setup: () => {
        mutate()
        return () => ''
      },
    },
    { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
  )
  await flushPromises()
  wrapper.unmount()
}

afterEach(() => {
  vi.useRealTimers()
})

describe('useUnreadNotificationCount', () => {
  /*
   * 이 값은 이슈 본문의 30초에서 내린 것이라 실수로 되돌아가기 쉽다. 근거는 코드 주석에
   * 남겨 두고, 값 자체는 여기서 붙잡는다.
   */
  it('폴링 주기를 15초로 고정한다', () => {
    expect(UNREAD_COUNT_POLL_INTERVAL_MS).toBe(15_000)
  })

  it('개수가 늘어나면 정산 캐시를 낡은 것으로 표시한다', async () => {
    fetchUnreadNotificationCount.mockResolvedValueOnce({ count: 0 })
    const wrapper = mountBell()
    await flushPromises()

    expect(invalidateSettlements).not.toHaveBeenCalled()

    // 새 알림이 왔다는 것은 정산 상태가 바뀌었다는 뜻이다.
    fetchUnreadNotificationCount.mockResolvedValueOnce({ count: 2 })
    await queryClient.refetchQueries()
    await flushPromises()

    expect(invalidateSettlements).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('개수가 줄어드는 것은 읽었다는 뜻이라 정산을 건드리지 않는다', async () => {
    fetchUnreadNotificationCount.mockResolvedValueOnce({ count: 3 })
    const wrapper = mountBell()
    await flushPromises()

    fetchUnreadNotificationCount.mockResolvedValueOnce({ count: 0 })
    await queryClient.refetchQueries()
    await flushPromises()

    expect(invalidateSettlements).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('첫 응답만으로는 정산을 무효화하지 않는다', async () => {
    // 화면을 열자마자 5가 오는 것은 "지금 막 5건이 늘었다"는 뜻이 아니다.
    fetchUnreadNotificationCount.mockResolvedValueOnce({ count: 5 })
    const wrapper = mountBell()
    await flushPromises()

    expect(invalidateSettlements).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('useReadNotification', () => {
  /*
   * 서버 응답을 기다렸다 점을 지우면 이미 정산 상세로 넘어간 뒤라 사용자는 아무 반응도
   * 보지 못한다. 눌렀는데 아무 일도 없어 보이는 것이 이번에 고치는 문제의 출발점이다.
   */
  it('응답을 기다리지 않고 그 알림만 읽음으로 바꾼다', async () => {
    queryClient.setQueryData(notificationKeys.page(undefined), page('1', '2'))

    await run(() => useReadNotification().mutate('1'))

    const cached = queryClient.getQueryData<ReturnType<typeof page>>(
      notificationKeys.page(undefined),
    )
    expect(cached?.notifications[0]?.readAt).not.toBeNull()
    // 누르지 않은 알림은 그대로 안 읽음이다.
    expect(cached?.notifications[1]?.readAt).toBeNull()
  })

  it('안 읽은 알림이 하나 줄었으므로 벨 개수를 다시 받는다', async () => {
    queryClient.setQueryData(notificationKeys.unreadCount(), 2)

    await run(() => useReadNotification().mutate('1'))

    expect(queryClient.getQueryState(notificationKeys.unreadCount())?.isInvalidated).toBe(true)
  })
})

describe('useDeleteNotification', () => {
  it('응답을 기다리지 않고 그 카드를 목록에서 뺀다', async () => {
    queryClient.setQueryData(notificationKeys.page(undefined), page('1', '2'))

    await run(() => useDeleteNotification().mutate('1'))

    const cached = queryClient.getQueryData<ReturnType<typeof page>>(
      notificationKeys.page(undefined),
    )
    expect(cached?.notifications.map((notification) => notification.id)).toEqual(['2'])
  })

  /* 낙관적으로 지웠는데 서버가 거절하면, 사용자는 사라진 알림을 되찾을 방법이 없다. */
  it('실패하면 지웠던 카드를 되돌린다', async () => {
    queryClient.setQueryData(notificationKeys.page(undefined), page('1', '2'))
    deleteNotification.mockRejectedValueOnce(new Error('boom'))

    await run(() => useDeleteNotification().mutate('1'))

    const cached = queryClient.getQueryData<ReturnType<typeof page>>(
      notificationKeys.page(undefined),
    )
    expect(cached?.notifications.map((notification) => notification.id)).toEqual(['1', '2'])
  })

  /* 안 읽은 알림을 지우면 그만큼 벨 숫자도 줄어야 한다. */
  it('벨 개수를 다시 받는다', async () => {
    queryClient.setQueryData(notificationKeys.unreadCount(), 2)

    await run(() => useDeleteNotification().mutate('1'))

    expect(queryClient.getQueryState(notificationKeys.unreadCount())?.isInvalidated).toBe(true)
  })
})

/*
 * 일괄 동작은 사용자가 스스로 누른 것이라, 목록이 눈앞에서 바뀌어도 놀랄 일이 없다.
 * 그래서 개별 읽음과 달리 캐시에 쌓인 쪽을 통째로 버리고 새로 받는다 — 남겨 두면 다음에
 * 목록을 열 때 지운 알림이 되살아나 보인다.
 */
describe('useReadAllNotifications · useDeleteAllNotifications', () => {
  it('모두 읽음은 목록과 벨 개수를 함께 다시 받는다', async () => {
    queryClient.setQueryData(notificationKeys.page(undefined), page('1'))
    queryClient.setQueryData(notificationKeys.unreadCount(), 2)

    await run(() => useReadAllNotifications().mutate())

    expect(queryClient.getQueryState(notificationKeys.page(undefined))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(notificationKeys.unreadCount())?.isInvalidated).toBe(true)
  })

  it('모두 지우기도 목록과 벨 개수를 함께 다시 받는다', async () => {
    queryClient.setQueryData(notificationKeys.page(undefined), page('1'))
    queryClient.setQueryData(notificationKeys.unreadCount(), 2)

    await run(() => useDeleteAllNotifications().mutate())

    expect(queryClient.getQueryState(notificationKeys.page(undefined))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(notificationKeys.unreadCount())?.isInvalidated).toBe(true)
  })
})
