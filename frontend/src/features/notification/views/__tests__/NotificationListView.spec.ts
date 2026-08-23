import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

const fetchNotifications = vi.fn()
const readAllNotifications = vi.fn()
const markNotificationRead = vi.fn()
const deleteNotification = vi.fn()
const deleteAllNotifications = vi.fn()

vi.mock('../../api/notificationApi', () => ({
  fetchNotifications: (limit?: number, cursor?: string) => fetchNotifications(limit, cursor),
  readAllNotifications: () => readAllNotifications(),
  markNotificationRead: (id: string) => markNotificationRead(id),
  deleteNotification: (id: string) => deleteNotification(id),
  deleteAllNotifications: () => deleteAllNotifications(),
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

/** 서버가 내리는 한 쪽. 더 볼 것이 없으면 nextCursor가 비어 있다. */
function page(notifications: unknown[], nextCursor: string | null = null) {
  return { notifications, nextCursor }
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/notifications', name: 'notifications', component: { template: '<div />' } },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
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
  markNotificationRead.mockReset()
  markNotificationRead.mockResolvedValue(undefined)
  deleteNotification.mockReset()
  deleteNotification.mockResolvedValue(undefined)
  deleteAllNotifications.mockReset()
  deleteAllNotifications.mockResolvedValue({ deletedCount: 1 })
})

describe('NotificationListView', () => {
  /*
   * 버튼에 aria-label을 걸면 그 말이 버튼 이름을 통째로 덮어써서, 화면을 못 보는 사람은
   * 알림마다 똑같은 한 마디만 듣게 된다. 안의 글이 그대로 이름이 되어야 한다.
   */
  it('알림 문장이 버튼의 이름을 덮어쓰이지 않고 그대로 읽힌다', async () => {
    fetchNotifications.mockResolvedValue(page([REQUESTED]))

    const wrapper = await mountView()
    const button = wrapper.get('li button')

    expect(button.attributes('aria-label')).toBeUndefined()
    expect(button.text()).toContain('Ari asked you for')
  })

  /*
   * 안 읽음을 점 하나로만 말하면 화면을 못 보는 사람에게는 아무 말도 하지 않은 것과 같다.
   */
  it('안 읽은 알림은 눈에 보이지 않는 말로도 안 읽음을 알린다', async () => {
    fetchNotifications.mockResolvedValue(page([REQUESTED]))

    const wrapper = await mountView()

    expect(wrapper.get('li button .sr-only').text()).toBe('Unread')
  })

  /*
   * 화면에 들어온 것만으로는 읽음이 아니다.
   *
   * 예전에는 진입할 때 전부 읽음 처리를 해 버려서, 무엇이 새로 왔는지를 지키려고 첫 응답의
   * 안 읽음 상태를 따로 붙들어 둬야 했다. 진입 자체를 읽음으로 치지 않으면 그 우회가
   * 필요 없고, 벨 숫자도 실제로 본 만큼만 줄어든다.
   */
  it('들어가는 것만으로는 읽음 처리를 부르지 않는다', async () => {
    fetchNotifications.mockResolvedValue(page([REQUESTED]))

    await mountView()

    expect(readAllNotifications).not.toHaveBeenCalled()
    expect(markNotificationRead).not.toHaveBeenCalled()
  })

  it('알림 종류에 맞는 문장으로 그린다', async () => {
    fetchNotifications.mockResolvedValue(
      page([REQUESTED, { ...REQUESTED, id: 2, type: 'SETTLEMENT_COMPLETED', amount: '100' }]),
    )

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Ari asked you for')
    expect(wrapper.text()).toContain('Dinner')
    expect(wrapper.text()).toContain('Everyone has paid for Dinner')
  })

  it('알림이 없으면 빈 상태를 보여준다', async () => {
    fetchNotifications.mockResolvedValue(page([]))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('No notifications yet')
  })

  /*
   * 정산 상세는 뒤로 나올 때 이 화면을 새로 쌓는다. 그래서 뒤로 가기를 브라우저 이력에
   * 맡기면 방금 빠져나온 정산 상세로 되돌아가고, 사용자는 두 화면 사이에 갇힌다.
   */
  it('뒤로 가기는 이력이 아니라 벨이 있는 지갑 홈으로 나간다', async () => {
    fetchNotifications.mockResolvedValue(page([REQUESTED]))
    const router = createTestRouter()

    const wrapper = await mountView(router)
    await wrapper.get('li button').trigger('click')
    await flushPromises()
    // 정산 상세에서 나온 것과 같은 상태 — 이 화면이 이력 맨 위에 다시 쌓인 뒤다.
    await router.push({ name: 'notifications' })
    await flushPromises()

    await wrapper.get('[data-testid="notification-back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet')
  })

  describe('누르기', () => {
    it('그 알림만 읽음으로 바꾸고 정산 상세로 간다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))
      const router = createTestRouter()

      const wrapper = await mountView(router)
      await wrapper.get('li button').trigger('click')
      await flushPromises()

      expect(markNotificationRead).toHaveBeenCalledWith('1')
      expect(router.currentRoute.value.name).toBe('settlement-detail')
      expect(router.currentRoute.value.params.settlementId).toBe('90')
      // 낼 정산이므로 뒤로 갔을 때 "To Pay" 쪽이 열려야 한다.
      expect(router.currentRoute.value.query.side).toBe('received')
    })

    /*
     * 이 표시가 없으면 정산 상세는 뒤로 갈 때 정산 홈으로 보낸다. 벨은 지갑에만 있어서,
     * 벨을 눌러 들어온 사용자가 지갑에서 두 화면이나 떨어진 곳에 서게 된다.
     */
    it('알림에서 왔다는 표시를 주소에 남긴다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))
      const router = createTestRouter()

      const wrapper = await mountView(router)
      await wrapper.get('li button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query.origin).toBe('notifications')
    })

    it('이미 읽은 알림은 다시 읽음 처리하지 않는다', async () => {
      fetchNotifications.mockResolvedValue(page([{ ...REQUESTED, readAt: '2026-08-21T12:05:00' }]))

      const wrapper = await mountView()
      await wrapper.get('li button').trigger('click')
      await flushPromises()

      expect(markNotificationRead).not.toHaveBeenCalled()
    })

    it('받을 정산 알림은 받을 쪽 목록에서 들어온 것으로 넘긴다', async () => {
      fetchNotifications.mockResolvedValue(page([{ ...REQUESTED, type: 'SETTLEMENT_PAID' }]))
      const router = createTestRouter()

      const wrapper = await mountView(router)
      await wrapper.get('li button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query.side).toBe('sent')
    })

    it('완료 알림은 어느 쪽인지 알 수 없으므로 정산 상세의 기본값에 맡긴다', async () => {
      fetchNotifications.mockResolvedValue(page([{ ...REQUESTED, type: 'SETTLEMENT_COMPLETED' }]))
      const router = createTestRouter()

      const wrapper = await mountView(router)
      await wrapper.get('li button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query.side).toBeUndefined()
    })
  })

  describe('지우기', () => {
    /* X는 아이콘뿐이라, 어느 알림을 지우는 버튼인지 이름으로 구분되어야 한다. */
    it('카드마다 그 알림을 가리키는 이름의 X가 있다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))

      const wrapper = await mountView()
      const dismiss = wrapper.get('[data-testid="notification-dismiss"]')

      expect(dismiss.attributes('aria-label')).toContain('Ari asked you for')
    })

    it('X를 누르면 그 카드가 바로 사라진다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED, { ...REQUESTED, id: 2 }]))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-dismiss"]').trigger('click')
      await flushPromises()

      expect(deleteNotification).toHaveBeenCalledWith('1')
      expect(wrapper.findAll('li')).toHaveLength(1)
    })

    it('모두 지우면 빈 상태가 된다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))

      const wrapper = await mountView()
      // 지우고 나면 서버도 빈 목록을 돌려준다. 목록은 성공 뒤 서버 값으로 다시 채워진다.
      fetchNotifications.mockResolvedValue(page([]))
      await wrapper.get('[data-testid="notification-dismiss-all"]').trigger('click')
      await flushPromises()

      expect(deleteAllNotifications).toHaveBeenCalledTimes(1)
      expect(wrapper.text()).toContain('No notifications yet')
    })

    /*
     * 낙관적으로 지웠는데 서버가 거절하면 카드가 돌아와야 한다.
     *
     * 화면이 목록을 따로 베껴 두던 시절에는 뮤테이션이 캐시를 되돌려도 그 사본이 그대로
     * 남아, 알림이 하나도 없는 것처럼 보인 채 굳었다.
     */
    it('X가 실패하면 지웠던 카드가 돌아온다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED, { ...REQUESTED, id: 2 }]))
      deleteNotification.mockRejectedValue(new Error('boom'))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-dismiss"]').trigger('click')
      await flushPromises()
      await flushPromises()

      expect(wrapper.findAll('li')).toHaveLength(2)
      expect(wrapper.text()).not.toContain('No notifications yet')
    })

    it('모두 지우기가 실패하면 목록이 돌아온다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED, { ...REQUESTED, id: 2 }]))
      deleteAllNotifications.mockRejectedValue(new Error('boom'))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-dismiss-all"]').trigger('click')
      await flushPromises()
      await flushPromises()

      expect(wrapper.findAll('li')).toHaveLength(2)
    })

    /* 지울 것이 없으면 누를 것도 없어야 한다. */
    it('목록이 비어 있으면 일괄 버튼을 내지 않는다', async () => {
      fetchNotifications.mockResolvedValue(page([]))

      const wrapper = await mountView()

      expect(wrapper.find('[data-testid="notification-dismiss-all"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="notification-mark-all-read"]').exists()).toBe(false)
    })
  })

  describe('모두 읽음', () => {
    it('안 읽은 것이 있을 때만 보이고, 누르면 점이 모두 사라진다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))

      const wrapper = await mountView()
      // 읽고 나면 서버도 읽은 상태로 돌려준다.
      fetchNotifications.mockResolvedValue(page([{ ...REQUESTED, readAt: '2026-08-21T12:05:00' }]))
      await wrapper.get('[data-testid="notification-mark-all-read"]').trigger('click')
      await flushPromises()

      expect(readAllNotifications).toHaveBeenCalledTimes(1)
      expect(wrapper.find('li button .sr-only').exists()).toBe(false)
    })

    it('모두 읽음이 실패하면 안 읽음 표시가 돌아온다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))
      readAllNotifications.mockRejectedValue(new Error('boom'))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-mark-all-read"]').trigger('click')
      await flushPromises()
      await flushPromises()

      expect(wrapper.find('li button .sr-only').exists()).toBe(true)
    })

    it('전부 읽은 목록에는 버튼이 없다', async () => {
      fetchNotifications.mockResolvedValue(page([{ ...REQUESTED, readAt: '2026-08-21T12:05:00' }]))

      const wrapper = await mountView()

      expect(wrapper.find('[data-testid="notification-mark-all-read"]').exists()).toBe(false)
    })
  })

  describe('더 보기', () => {
    it('다음 쪽이 있을 때만 버튼을 낸다', async () => {
      fetchNotifications.mockResolvedValue(page([REQUESTED]))

      const wrapper = await mountView()

      expect(wrapper.find('[data-testid="notification-load-more"]').exists()).toBe(false)
    })

    /*
     * 다음 쪽을 기다리는 동안 화면을 로딩으로 덮으면, 읽고 있던 목록이 통째로 사라졌다가
     * 돌아온다. 첫 쪽이 아직 없을 때만 덮어야 한다.
     */
    it('다음 쪽을 받는 동안에도 이미 받은 쪽이 그대로 있다', async () => {
      fetchNotifications.mockResolvedValueOnce(page([REQUESTED], '1'))
      let release: (value: unknown) => void = () => {}
      fetchNotifications.mockReturnValueOnce(
        new Promise((resolve) => {
          release = resolve
        }),
      )

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-load-more"]').trigger('click')
      await flushPromises()

      expect(wrapper.findAll('li')).toHaveLength(1)

      release(page([{ ...REQUESTED, id: 2 }]))
      await flushPromises()

      expect(wrapper.findAll('li')).toHaveLength(2)
    })

    /* 다음 쪽만 실패한 것이라 앞 쪽까지 잃으면 안 된다. */
    it('다음 쪽이 실패해도 앞 쪽은 남고 그 자리에서만 알린다', async () => {
      fetchNotifications.mockResolvedValueOnce(page([REQUESTED], '1'))
      fetchNotifications.mockRejectedValue(new Error('boom'))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-load-more"]').trigger('click')
      await flushPromises()
      await flushPromises()

      expect(wrapper.findAll('li')).toHaveLength(1)
      expect(wrapper.text()).not.toContain('went wrong')
    })

    it('누르면 커서를 실어 다음 쪽을 받아 이어 붙인다', async () => {
      fetchNotifications.mockResolvedValueOnce(page([REQUESTED], '1'))
      fetchNotifications.mockResolvedValueOnce(page([{ ...REQUESTED, id: 2 }]))

      const wrapper = await mountView()
      await wrapper.get('[data-testid="notification-load-more"]').trigger('click')
      await flushPromises()

      expect(fetchNotifications).toHaveBeenLastCalledWith(undefined, '1')
      // 앞 쪽을 버리지 않는다. 읽고 있던 자리를 잃으면 안 된다.
      expect(wrapper.findAll('li')).toHaveLength(2)
      expect(wrapper.find('[data-testid="notification-load-more"]').exists()).toBe(false)
    })
  })
})
