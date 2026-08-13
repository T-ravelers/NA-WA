import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

const applyLocale = vi.fn()
const requestSignOut = vi.fn()

vi.mock('@/app/i18n/applyLocale', () => ({
  applyLocale: (...args: unknown[]) => applyLocale(...args),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  requestSignOut: () => requestSignOut(),
}))

const SignInView = (await import('../SignInView.vue')).default

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/sign-in', name: 'sign-in', component: SignInView }],
  })
}

async function mountView() {
  const router = createTestRouter()

  await router.push('/sign-in')
  await router.isReady()

  const wrapper = mount(SignInView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })

  await flushPromises()

  return wrapper
}

describe('SignInView', () => {
  beforeEach(() => {
    localStorage.clear()
    queryClient.clear()
    queryClient.setDefaultOptions({ mutations: { retry: false } })
    applyLocale.mockReset()
    requestSignOut.mockReset()
    requestSignOut.mockResolvedValue(undefined)
  })

  it('offers both providers', async () => {
    const text = (await mountView()).text()

    expect(text).toContain('Continue with Google')
    expect(text).toContain('Continue with LINE')
  })

  it('explains an uncertain sign-out and offers a retry', async () => {
    localStorage.setItem('nawa.auth.signOutBarrier', 'active')
    const wrapper = await mountView()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'We could not confirm that you signed out',
    )

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(requestSignOut).toHaveBeenCalledOnce()
  })

  it('prevents a provider redirect while sign-out retry is pending', async () => {
    localStorage.setItem('nawa.auth.signOutBarrier', 'active')
    requestSignOut.mockImplementation(() => new Promise(() => undefined))
    const wrapper = await mountView()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    const providerButtons = wrapper
      .findAll('button')
      .filter((button) => /Google|LINE/.test(button.text()))

    expect(providerButtons).toHaveLength(2)
    expect(providerButtons.every((button) => button.attributes('disabled') !== undefined)).toBe(
      true,
    )
  })

  // LINE 로그인은 백엔드가 이미 지원한다. 안내만 붙이고 버튼은 살려 둔다.
  it('keeps LINE usable while flagging its status', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('LINE sign-in is still being verified')
    expect(wrapper.findAll('button').some((b) => b.text().includes('LINE'))).toBe(true)
  })

  it('shows the consent line with both policy links', async () => {
    const wrapper = await mountView()
    const links = wrapper.findAll('a').map((a) => a.text())

    expect(links).toContain('Terms of Service')
    expect(links).toContain('Privacy Policy')
  })

  it('names the current screen language', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Screen language · English')
  })

  it('keeps the language sheet closed until asked', async () => {
    expect((await mountView()).find('[role="dialog"]').exists()).toBe(false)
  })

  it('opens the language sheet from the header control', async () => {
    const wrapper = await mountView()

    await wrapper.get('[aria-label="Change screen language"]').trigger('click')

    expect(wrapper.get('[role="dialog"]').isVisible()).toBe(true)
  })

  it('applies and stores the chosen language, then closes the sheet', async () => {
    const wrapper = await mountView()

    await wrapper.get('[aria-label="Change screen language"]').trigger('click')
    await wrapper.findAll('[role="dialog"] [role="radio"]')[1]?.trigger('click')

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  // 로그인 이전이라 서버에 저장할 곳이 없다. 선택은 브라우저에만 남는다.
  it('reflects the new language in the header label without a session', async () => {
    const wrapper = await mountView()

    await wrapper.get('[aria-label="Change screen language"]').trigger('click')
    await wrapper.findAll('[role="dialog"] [role="radio"]')[4]?.trigger('click')

    expect(wrapper.text()).toContain('Screen language · Tiếng Việt')
  })
})
