import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { SUPPORTED_LOCALES } from '@/shared/i18n/locales'

import type { PushCardMode } from '../../model/usePushSubscription'

const state = vi.hoisted(() => ({
  mode: { value: 'off' },
  busy: { value: false },
  failed: { value: false },
  enable: vi.fn(),
  disable: vi.fn(),
}))

vi.mock('../../model/usePushSubscription', async () => {
  const { ref: vueRef } = await import('vue')
  state.mode = vueRef<PushCardMode>('off')
  state.busy = vueRef(false)
  state.failed = vueRef(false)

  return { usePushSubscription: () => state }
})

const PushSubscriptionCard = (await import('../PushSubscriptionCard.vue')).default

function mountCard() {
  return mount(PushSubscriptionCard, { global: { plugins: [i18n] } })
}

function setMode(mode: PushCardMode): void {
  state.mode.value = mode
}

beforeEach(() => {
  i18n.global.locale.value = 'en'
  setMode('off')
  state.busy.value = false
  state.failed.value = false
  state.enable.mockReset()
  state.disable.mockReset()
})

describe('PushSubscriptionCard', () => {
  it('renders nothing when there is nothing to offer', () => {
    setMode('hidden')

    expect(mountCard().find('[data-testid="push-card"]').exists()).toBe(false)
  })

  it('offers to turn push on, and turns it on when pressed', async () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('Alerts on this device')
    expect(wrapper.find('[data-testid="push-disable"]').exists()).toBe(false)

    await wrapper.find('[data-testid="push-enable"]').trigger('click')

    expect(state.enable).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="push-enable"]').text()).toBe('Turn on')
  })

  it('lets the user turn push off when it is on', async () => {
    setMode('on')
    const wrapper = mountCard()

    expect(wrapper.find('[data-testid="push-enable"]').exists()).toBe(false)
    await wrapper.find('[data-testid="push-disable"]').trigger('click')

    expect(state.disable).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="push-disable"]').text()).toBe('Turn off')
  })

  /** 막혀 있는 것은 앱이 풀 수 없고, 설치는 버튼이 아니라 사용자가 하는 일이다. 안내만 한다. */
  it.each([
    ['blocked', 'Notifications are blocked for NA-WA'],
    ['install', 'add NA-WA to your Home Screen first'],
  ] as const)('only explains the %s state, without a button', (mode, text) => {
    setMode(mode)
    const wrapper = mountCard()

    expect(wrapper.text()).toContain(text)
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('says so when turning push on or off did not work', () => {
    state.failed.value = true
    const wrapper = mountCard()

    expect(wrapper.find('[data-testid="push-failed"]').text()).toBe(
      'Something went wrong. Please try again.',
    )
    expect(wrapper.find('[data-testid="push-failed"]').attributes('role')).toBe('alert')
  })

  it('shows no error until something fails', () => {
    expect(mountCard().find('[data-testid="push-failed"]').exists()).toBe(false)
  })
})

describe('push card messages', () => {
  const KEYS = [
    'notification.push.title',
    'notification.push.off.description',
    'notification.push.off.action',
    'notification.push.on.description',
    'notification.push.on.action',
    'notification.push.blocked.description',
    'notification.push.install.description',
    'notification.push.failed',
  ]

  /** 영어로 폴백되면 일본어·베트남어 사용자에게 영어 안내가 섞여 나온다. 네 언어를 모두 확인한다. */
  it.each(SUPPORTED_LOCALES)('is translated into %s', (locale) => {
    for (const key of KEYS) {
      expect(i18n.global.te(key, locale), `${locale}: ${key}`).toBe(true)
    }
  })

  /** 이 문구들은 값을 끼워 넣지 않는다. 중괄호가 남아 있으면 번역 중에 자리 표시자가 섞여 들어온 것이다. */
  it('leaves no key name or placeholder behind in any language', () => {
    for (const locale of SUPPORTED_LOCALES) {
      for (const key of KEYS) {
        const text = i18n.global.t(key, {}, { locale })

        expect(text, `${locale}: ${key}`).not.toBe(key)
        expect(text, `${locale}: ${key}`).not.toContain('{')
      }
    }
  })
})
