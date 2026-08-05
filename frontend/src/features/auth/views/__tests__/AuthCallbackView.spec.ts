import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

const ensureAuthSession = vi.fn()
const clearAuthSession = vi.fn()

vi.mock('../../model/authQueries', () => ({
  ensureAuthSession: () => ensureAuthSession(),
  clearAuthSession: () => clearAuthSession(),
}))

const { storeReturnPath } = await import('../../model/returnPath')
const AuthCallbackView = (await import('../AuthCallbackView.vue')).default

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sign-in', name: 'sign-in', component: { template: '<div />' } },
      { path: '/auth/callback', name: 'callback', component: AuthCallbackView },
      { path: '/explore', name: 'explore', component: { template: '<div />' } },
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
    ],
  })
}

async function mountAt(fullPath: string) {
  const router = createTestRouter()

  await router.push(fullPath)
  await router.isReady()

  const wrapper = mount(AuthCallbackView, { global: { plugins: [i18n, router] } })

  await flushPromises()

  return { wrapper, router }
}

describe('AuthCallbackView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    ensureAuthSession.mockReset()
    clearAuthSession.mockReset()
  })

  describe('when the provider callback reports an error', () => {
    // 회귀: 재시도해도 원래 가려던 화면으로 돌아가야 한다.
    it('keeps the stored return path instead of consuming it', async () => {
      storeReturnPath('/wallet')

      await mountAt('/auth/callback?error=AUTH-014')

      expect(sessionStorage.getItem('nawa.auth.returnPath')).toBe('/wallet')
    })

    it('carries the return path into the retry link', async () => {
      storeReturnPath('/wallet')

      const { wrapper, router } = await mountAt('/auth/callback?error=AUTH-014')

      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/sign-in')
      expect(router.currentRoute.value.query.returnPath).toBe('/wallet')
    })

    it('goes to a bare sign-in when nothing was stored', async () => {
      const { wrapper, router } = await mountAt('/auth/callback?error=AUTH-014')

      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/sign-in')
      expect(router.currentRoute.value.query.returnPath).toBeUndefined()
    })

    it('shows the message for the reported error code', async () => {
      const { wrapper } = await mountAt('/auth/callback?error=AUTH-014')

      expect(wrapper.text()).toContain('This sign-in link is no longer valid')
    })

    it('does not probe the session', async () => {
      await mountAt('/auth/callback?error=AUTH-014')

      expect(ensureAuthSession).not.toHaveBeenCalled()
    })
  })

  describe('when the provider callback succeeds', () => {
    it('sends the member to the stored return path and consumes it', async () => {
      ensureAuthSession.mockResolvedValue({ memberId: 1 })
      storeReturnPath('/wallet?tab=history')

      const { router } = await mountAt('/auth/callback')

      expect(router.currentRoute.value.fullPath).toBe('/wallet?tab=history')
      expect(sessionStorage.getItem('nawa.auth.returnPath')).toBeNull()
    })

    it('falls back to the default screen when nothing was stored', async () => {
      ensureAuthSession.mockResolvedValue({ memberId: 1 })

      const { router } = await mountAt('/auth/callback')

      expect(router.currentRoute.value.path).toBe('/explore')
    })

    // 회귀: 쿠키 설정이 실패해도 복귀 경로를 잃지 않아야 한다.
    it('keeps the return path when the session probe comes back empty', async () => {
      ensureAuthSession.mockResolvedValue(null)
      storeReturnPath('/wallet')

      const { router } = await mountAt('/auth/callback')

      expect(router.currentRoute.value.path).toBe('/sign-in')
      expect(router.currentRoute.value.query.returnPath).toBe('/wallet')
      expect(sessionStorage.getItem('nawa.auth.returnPath')).toBe('/wallet')
    })
  })
})
