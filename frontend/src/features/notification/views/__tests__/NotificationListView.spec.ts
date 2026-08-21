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
  /*
   * 버튼에 aria-label을 걸면 그 말이 버튼 이름을 통째로 덮어써서, 화면을 못 보는 사람은
   * 알림마다 똑같은 한 마디만 듣게 된다. 안의 글이 그대로 이름이 되어야 한다.
   */
  it('알림 문장이 버튼의 이름을 덮어쓰이지 않고 그대로 읽힌다', async () => {
    fetchNotifications.mockResolvedValue([REQUESTED])

    const wrapper = await mountView()
    const button = wrapper.get('li button')

    expect(button.attributes('aria-label')).toBeUndefined()
    expect(button.text()).toContain('Ari asked you for')
  })

  /*
   * 안 읽음을 점 하나로만 말하면 화면을 못 보는 사람에게는 아무 말도 하지 않은 것과 같다.
   */
  it('안 읽은 알림은 눈에 보이지 않는 말로도 안 읽음을 알린다', async () => {
    fetchNotifications.mockResolvedValue([REQUESTED])

    const wrapper = await mountView()

    expect(wrapper.get('li button .sr-only').text()).toBe('Unread')
  })

  /*
   * 들어오자마자 전부 읽음으로 바꾸기 때문에, 서버 응답을 그대로 따라가면 점이 찍히자마자
   * 지워진다. 사용자가 목록을 여는 이유가 바로 무엇이 새로 왔는지 보는 것이다.
   *
   * 그래서 목록을 다시 받아 와 전부 읽음으로 바뀌더라도 이번 방문의 표시는 유지되어야
   * 한다. 두 번째 응답을 읽음 상태로 두어 그 상황을 그대로 만든다.
   */
  it('읽음 처리 뒤 목록을 다시 받아도 이번 방문에서는 안 읽음 표시가 남는다', async () => {
    fetchNotifications.mockResolvedValueOnce([REQUESTED])
    fetchNotifications.mockResolvedValue([{ ...REQUESTED, readAt: '2026-08-21T12:05:00' }])

    const wrapper = await mountView()
    await queryClient.refetchQueries({ queryKey: ['notifications', 'list'] })
    await flushPromises()

    expect(wrapper.find('li button .sr-only').exists()).toBe(true)
  })

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
    await wrapper.get('li button').trigger('click')
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
    await wrapper.get('li button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('완료 알림은 어느 쪽인지 알 수 없으므로 정산 상세의 기본값에 맡긴다', async () => {
    fetchNotifications.mockResolvedValue([{ ...REQUESTED, type: 'SETTLEMENT_COMPLETED' }])
    const router = createTestRouter()

    const wrapper = await mountView(router)
    await wrapper.get('li button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.side).toBeUndefined()
  })
})
