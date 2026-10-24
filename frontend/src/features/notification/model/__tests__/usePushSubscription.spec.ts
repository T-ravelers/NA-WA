import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

import type { PushEnvironment } from '../pushSubscription'

const fetchPushPublicKey = vi.fn()
const enablePush = vi.fn()
const disablePush = vi.fn()
const readPushSubscription = vi.fn()
const browserPushEnvironment = vi.fn()

vi.mock('../../api/pushSubscriptionApi', () => ({
  fetchPushPublicKey: () => fetchPushPublicKey(),
}))

// 브라우저 API를 만지는 함수만 바꾸고, 지원 여부 판정은 진짜를 쓴다.
vi.mock('../pushSubscription', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../pushSubscription')>()),
  browserPushEnvironment: () => browserPushEnvironment(),
  enablePush: (key: string, language: string) => enablePush(key, language),
  disablePush: () => disablePush(),
  readPushSubscription: () => readPushSubscription(),
}))

const { usePushSubscription } = await import('../usePushSubscription')

const SUPPORTED: PushEnvironment = {
  hasServiceWorker: true,
  hasPushManager: true,
  hasNotification: true,
  userAgent: 'Mozilla/5.0 Chrome/130',
  platform: 'Linux x86_64',
  maxTouchPoints: 0,
  standalone: false,
}

/** 컴포저블을 부르는 가장 작은 컴포넌트. 반환값을 꺼내 쓸 수 있게 밖에 잡아 둔다. */
async function setUp(environment: PushEnvironment = SUPPORTED) {
  browserPushEnvironment.mockReturnValue(environment)

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

function stubPermission(permission: NotificationPermission): void {
  vi.stubGlobal('Notification', { permission })
}

beforeEach(() => {
  queryClient.clear()
  fetchPushPublicKey.mockReset()
  fetchPushPublicKey.mockResolvedValue('BKey')
  enablePush.mockReset()
  disablePush.mockReset()
  disablePush.mockResolvedValue(undefined)
  readPushSubscription.mockReset()
  readPushSubscription.mockResolvedValue(null)
  stubPermission('default')
  i18n.global.locale.value = 'en'
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('usePushSubscription', () => {
  it('stays hidden, and asks the server nothing, where the browser cannot subscribe', async () => {
    const { mode } = await setUp({ ...SUPPORTED, hasPushManager: false })

    expect(mode.value).toBe('hidden')
    expect(fetchPushPublicKey).not.toHaveBeenCalled()
  })

  /** 서버의 푸시가 꺼져 있으면(키가 비어 있으면) 켤 수 없는 버튼을 보여 주지 않는다. */
  it('stays hidden when the server has push turned off', async () => {
    fetchPushPublicKey.mockResolvedValue(null)

    expect((await setUp()).mode.value).toBe('hidden')
  })

  it('stays hidden when the public key cannot be loaded', async () => {
    fetchPushPublicKey.mockRejectedValue(new Error('network'))

    expect((await setUp()).mode.value).toBe('hidden')
  })

  it('offers to turn push on when this device has no subscription yet', async () => {
    expect((await setUp()).mode.value).toBe('off')
  })

  it('shows push as on when the device is subscribed and allowed', async () => {
    stubPermission('granted')
    readPushSubscription.mockResolvedValue({ endpoint: 'https://fcm.googleapis.com/x' })

    expect((await setUp()).mode.value).toBe('on')
  })

  it('explains that notifications are blocked instead of offering a button that cannot work', async () => {
    stubPermission('denied')

    expect((await setUp()).mode.value).toBe('blocked')
  })

  it('tells an iPhone browser tab to install the app first', async () => {
    const iphoneTab = {
      ...SUPPORTED,
      hasPushManager: false,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X)',
    }

    expect((await setUp(iphoneTab)).mode.value).toBe('install')
  })

  it('turns push on with the server key and the current screen language', async () => {
    i18n.global.locale.value = 'ja'
    enablePush.mockResolvedValue({ permission: 'granted', enabled: true })
    const state = await setUp()

    await state.enable()

    expect(enablePush).toHaveBeenCalledWith('BKey', 'ja')
    expect(state.mode.value).toBe('on')
    expect(state.failed.value).toBe(false)
  })

  it('goes back to blocked when the user refuses the permission prompt', async () => {
    enablePush.mockResolvedValue({ permission: 'denied', enabled: false })
    const state = await setUp()

    await state.enable()

    expect(state.mode.value).toBe('blocked')
  })

  it('stays off, without an error, when the prompt is dismissed', async () => {
    enablePush.mockResolvedValue({ permission: 'default', enabled: false })
    const state = await setUp()

    await state.enable()

    expect(state.mode.value).toBe('off')
    expect(state.failed.value).toBe(false)
  })

  it('reports a failed attempt and lets the user try again', async () => {
    enablePush.mockRejectedValue(new Error('server down'))
    const state = await setUp()

    await state.enable()

    expect(state.mode.value).toBe('off')
    expect(state.failed.value).toBe(true)
    expect(state.busy.value).toBe(false)

    enablePush.mockResolvedValue({ permission: 'granted', enabled: true })
    await state.enable()

    expect(state.mode.value).toBe('on')
    expect(state.failed.value).toBe(false)
  })

  it('does not start a second attempt while one is running', async () => {
    let finish: (value: unknown) => void = () => undefined
    enablePush.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const state = await setUp()

    const first = state.enable()
    await state.enable()
    finish({ permission: 'granted', enabled: true })
    await first

    expect(enablePush).toHaveBeenCalledOnce()
  })

  it('turns push off', async () => {
    stubPermission('granted')
    readPushSubscription.mockResolvedValue({ endpoint: 'https://fcm.googleapis.com/x' })
    const state = await setUp()

    await state.disable()

    expect(disablePush).toHaveBeenCalledOnce()
    expect(state.mode.value).toBe('off')
  })

  it('keeps push on and reports it when turning it off fails', async () => {
    stubPermission('granted')
    readPushSubscription.mockResolvedValue({ endpoint: 'https://fcm.googleapis.com/x' })
    disablePush.mockRejectedValue(new Error('browser refused'))
    const state = await setUp()

    await state.disable()

    expect(state.mode.value).toBe('on')
    expect(state.failed.value).toBe(true)
  })
})
