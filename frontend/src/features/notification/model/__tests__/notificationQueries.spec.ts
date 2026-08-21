import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { queryClient } from '@/app/query/client'

import { notificationSettlementIntegrationKey } from '../settlementIntegration'

const fetchUnreadNotificationCount = vi.fn()
const readAllNotifications = vi.fn()

vi.mock('../../api/notificationApi', () => ({
  fetchUnreadNotificationCount: () => fetchUnreadNotificationCount(),
  fetchNotifications: vi.fn(),
  readAllNotifications: () => readAllNotifications(),
}))

const {
  UNREAD_COUNT_POLL_INTERVAL_MS,
  notificationKeys,
  useReadAllNotifications,
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
  invalidateSettlements.mockReset()
})

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

describe('useReadAllNotifications', () => {
  /*
   * 목록까지 무효화하면 방금 그린 화면을 곧바로 다시 받아 오는데, 그 응답은 전부 읽음
   * 상태라 안 읽음 표시가 눈앞에서 지워진다. 사용자가 목록을 여는 이유가 바로 무엇이
   * 새로 왔는지 보는 것이라, 요청 한 번을 더 쓰면서 화면은 더 나빠진다.
   */
  it('벨 개수만 다시 받고 보고 있는 목록은 건드리지 않는다', async () => {
    queryClient.setQueryData(notificationKeys.list(), [])
    queryClient.setQueryData(notificationKeys.unreadCount(), 2)

    // defineComponent를 쓰지 않는 것은 한 파일에 컴포넌트를 둘 두지 않기 위해서다.
    const wrapper = mount(
      {
        setup() {
          useReadAllNotifications().mutate()
          return () => ''
        },
      },
      { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
    )
    await flushPromises()

    expect(queryClient.getQueryState(notificationKeys.list())?.isInvalidated).toBe(false)
    expect(queryClient.getQueryState(notificationKeys.unreadCount())?.isInvalidated).toBe(true)
    wrapper.unmount()
  })
})
