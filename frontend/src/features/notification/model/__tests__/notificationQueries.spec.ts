import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { queryClient } from '@/app/query/client'

import { notificationSettlementIntegrationKey } from '../settlementIntegration'

const fetchUnreadNotificationCount = vi.fn()

vi.mock('../../api/notificationApi', () => ({
  fetchUnreadNotificationCount: () => fetchUnreadNotificationCount(),
  fetchNotifications: vi.fn(),
  readAllNotifications: vi.fn(),
}))

const { UNREAD_COUNT_POLL_INTERVAL_MS, useUnreadNotificationCount } =
  await import('../notificationQueries')

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
