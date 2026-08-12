import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

const clear = vi.fn()

vi.mock('@/app/query/client', () => ({
  queryClient: { clear: () => clear() },
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
    localStorage.clear()
    sessionStorage.clear()
    clear.mockReset()
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

    it('leaves the cache alone', async () => {
      localStorage.setItem('nawa.auth.signOutBarrier', 'active')

      await mountAt('/auth/callback?error=AUTH-014')

      expect(clear).not.toHaveBeenCalled()
      expect(localStorage.getItem('nawa.auth.signOutBarrier')).toBe('active')
    })
  })

  describe('when the provider callback succeeds', () => {
    it('sends the member to the stored return path and consumes it', async () => {
      storeReturnPath('/wallet?tab=history')

      const { router } = await mountAt('/auth/callback')

      expect(router.currentRoute.value.fullPath).toBe('/wallet?tab=history')
      expect(sessionStorage.getItem('nawa.auth.returnPath')).toBeNull()
    })

    it('falls back to the default screen when nothing was stored', async () => {
      const { router } = await mountAt('/auth/callback')

      expect(router.currentRoute.value.path).toBe('/explore')
    })

    /*
     * 쿠키 설정이 실패했을 때 복귀 경로를 잃지 않는지는 이제 라우터 guard가 보장한다.
     * `guard.spec.ts`의 "sends an unauthenticated visitor to sign-in and keeps the
     * return path"가 그 회귀를 덮는다.
     */

    it('drops responses cached before authentication', async () => {
      localStorage.setItem('nawa.auth.signOutBarrier', 'active')

      await mountAt('/auth/callback')

      expect(clear).toHaveBeenCalled()
      expect(localStorage.getItem('nawa.auth.signOutBarrier')).toBeNull()
    })
  })
})
