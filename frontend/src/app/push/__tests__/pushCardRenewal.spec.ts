import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

import {
  OLD_SERVER_KEY,
  SERVER_KEY,
  createTestRouter,
  installBrowserWithSubscription,
} from './fakePushBrowser'

/**
 * 알림 화면의 카드·라이프사이클·구독 모델을 실제로 연결하고, 서버와 httpClient만 흉내 낸다.
 * 서버가 VAPID 키를 바꾼 뒤 앱을 열면 예전 키로 만든 구독을 자동으로 다시 만드는데, 이 갱신이
 * 사용자가 카드에서 하는 끄기·켜기와 만나는 순서를 다룬다.
 */

const savePushSubscription = vi.fn()
const deletePushSubscription = vi.fn()
const fetchPushPublicKey = vi.fn()

vi.mock('@/features/notification/api/pushSubscriptionApi', () => ({
  savePushSubscription: (...args: unknown[]) => savePushSubscription(...args),
  deletePushSubscription: (...args: unknown[]) => deletePushSubscription(...args),
  fetchPushPublicKey: (...args: unknown[]) => fetchPushPublicKey(...args),
}))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { post: vi.fn().mockResolvedValue(undefined) },
}))

const { registerPushSubscriptionLifecycle } = await import('../pushSubscriptionLifecycle')
const { usePushSubscription } = await import('@/features/notification/model/usePushSubscription')
const { readPushSubscription } = await import('@/features/notification/model/pushSubscription')

/** 알림 화면의 카드를 그려 상태를 꺼내 쓴다. */
async function mountCard() {
  let state!: ReturnType<typeof usePushSubscription>
  mount(
    defineComponent({
      setup() {
        state = usePushSubscription()
        return () => h('div')
      },
    }),
    { global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] } },
  )
  await flushPromises()

  return state
}

let stop: () => void = () => undefined

beforeEach(() => {
  queryClient.clear()
  savePushSubscription.mockReset()
  savePushSubscription.mockResolvedValue(undefined)
  deletePushSubscription.mockReset()
  deletePushSubscription.mockResolvedValue(undefined)
  fetchPushPublicKey.mockReset()
  fetchPushPublicKey.mockResolvedValue(SERVER_KEY)
  localStorage.clear()
  i18n.global.locale.value = 'en'
})

afterEach(() => {
  stop()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'serviceWorker')
})

describe('turning push off while the key renewal waits on the old server row', () => {
  /**
   * 갱신은 예전 서버 행의 삭제가 끝나야 새로 구독한다. 그 삭제가 느린 동안 사용자가 푸시를 끄면,
   * 끄기가 끝난 뒤에 갱신이 새 구독을 만들어 저장해서는 안 된다. 카드는 꺼짐인데 푸시가 오게 된다.
   */
  it('does not bring the subscription back', async () => {
    const { subscribe } = installBrowserWithSubscription({ staleKey: true })
    let answerStaleDelete: () => void = () => undefined
    deletePushSubscription.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          answerStaleDelete = resolve
        }),
    )
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    const card = await mountCard()
    expect(card.mode.value).toBe('on')

    await router.push('/wallet')
    await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledTimes(1))
    await card.disable()
    expect(card.mode.value).toBe('off')

    answerStaleDelete()
    await flushPromises()

    expect(subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
    await expect(readPushSubscription()).resolves.toBeNull()
    expect(card.mode.value).toBe('off')
  })
})

describe('turning push back on after the renewal found a new server key', () => {
  /**
   * 카드는 공개키를 한 번 받아 두고 계속 쓴다. 앱이 열려 있는 동안 서버가 키를 바꾸고 언어 변경으로
   * 갱신이 새 키를 확인했다면, 그 뒤에 카드에서 끄고 켤 때도 새 키로 구독해야 한다. 예전 키로
   * 구독하면 켜짐으로 보이지만 푸시가 오지 않는다.
   */
  it('subscribes with the new key', async () => {
    const { subscribe } = installBrowserWithSubscription({ staleKey: true })
    let serverKey = OLD_SERVER_KEY
    fetchPushPublicKey.mockImplementation(() => Promise.resolve(serverKey))
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    const card = await mountCard()
    await router.push('/wallet')
    await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalledTimes(1))

    // 페이지를 연 채로 서버가 키를 바꾸고, 언어 변경이 갱신을 돌린다.
    serverKey = SERVER_KEY
    i18n.global.locale.value = 'ja'
    await vi.waitFor(() => expect(subscribe).toHaveBeenCalledTimes(1))
    await flushPromises()

    await card.disable()
    await card.enable()

    expect(subscribe).toHaveBeenCalledTimes(2)
    expect(Array.from(subscribe.mock.calls[1]?.[0].applicationServerKey ?? [])).toEqual([251, 255])
    expect(card.mode.value).toBe('on')
  })
})
