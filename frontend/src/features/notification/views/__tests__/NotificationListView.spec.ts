import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

const fetchNotifications = vi.fn()
const readAllNotifications = vi.fn()

vi.mock('../../api/notificationApi', () => ({
  fetchNotifications: () => fetchNotifications(),
  readAllNotifications: () => readAllNotifications(),
  fetchUnreadNotificationCount: vi.fn(),
}))

const NotificationListView = (await import('../NotificationListView.vue')).default

const REQUESTED = {
  id: 1,
  type: 'SETTLEMENT_REQUESTED',
  settlementId: 90,
  actorName: 'Ari',
  gatheringName: 'Dinner',
  amount: '30',
  currencyCode: 'KRW',
  readAt: null,
  createdAt: '2026-08-21T12:00:00',
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/notifications', name: 'notifications', component: { template: '<div />' } },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountView(router: Router = createTestRouter()) {
  await router.push('/notifications')
  await router.isReady()

  const wrapper = mount(NotificationListView, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }], router] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  queryClient.clear()
  fetchNotifications.mockReset()
  readAllNotifications.mockReset()
  readAllNotifications.mockResolvedValue({ updatedCount: 1 })
})

describe('NotificationListView', () => {
  it('알림 종류에 맞는 문장으로 그린다', async () => {
    fetchNotifications.mockResolvedValue([
      REQUESTED,
      { ...REQUESTED, id: 2, type: 'SETTLEMENT_COMPLETED', amount: '100' },
    ])

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Ari asked you for')
    expect(wrapper.text()).toContain('Dinner')
    expect(wrapper.text()).toContain('Everyone has paid for Dinner')
  })

  it('화면에 들어가면 읽음 처리를 한 번 부른다', async () => {
    fetchNotifications.mockResolvedValue([REQUESTED])

    await mountView()

    expect(readAllNotifications).toHaveBeenCalledTimes(1)
  })

  /*
   * 목록은 이미 보이고 있는데 읽음 처리만 실패한 상황이다. 배지가 잠시 남는 것은 알림을
   * 아예 못 보는 것보다 가벼운 문제라, 화면을 오류로 덮지 않는다.
   */
  it('읽음 처리가 실패해도 목록은 그대로 보여준다', async () => {
    fetchNotifications.mockResolvedValue([REQUESTED])
    readAllNotifications.mockRejectedValue(new Error('read-all failed'))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Ari asked you for')
  })

  it('알림이 없으면 빈 상태를 보여준다', async () => {
    fetchNotifications.mockResolvedValue([])

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('No notifications yet')
  })

  it('알림을 누르면 그 정산 상세로 간다', async () => {
    fetchNotifications.mockResolvedValue([REQUESTED])
    const router = createTestRouter()

    const wrapper = await mountView(router)
    await wrapper.get('button[aria-label="Open this split"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-detail')
    expect(router.currentRoute.value.params.settlementId).toBe('90')
    // 낼 정산이므로 뒤로 갔을 때 "To Pay" 쪽이 열려야 한다.
    expect(router.currentRoute.value.query.side).toBe('received')
  })

  it('받을 정산 알림은 받을 쪽 목록에서 들어온 것으로 넘긴다', async () => {
    fetchNotifications.mockResolvedValue([{ ...REQUESTED, type: 'SETTLEMENT_PAID' }])
    const router = createTestRouter()

    const wrapper = await mountView(router)
    await wrapper.get('button[aria-label="Open this split"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('완료 알림은 어느 쪽인지 알 수 없으므로 정산 상세의 기본값에 맡긴다', async () => {
    fetchNotifications.mockResolvedValue([{ ...REQUESTED, type: 'SETTLEMENT_COMPLETED' }])
    const router = createTestRouter()

    const wrapper = await mountView(router)
    await wrapper.get('button[aria-label="Open this split"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.side).toBeUndefined()
  })
})
