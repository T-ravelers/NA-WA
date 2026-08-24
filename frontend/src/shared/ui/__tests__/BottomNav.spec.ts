import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import BottomNav from '../BottomNav.vue'

async function mountAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/explore', component: { template: '<div />' } },
      { path: '/reports', component: { template: '<div />' } },
      { path: '/reports/:reportId', component: { template: '<div />' } },
      { path: '/profile', component: { template: '<div />' } },
      { path: '/wallet', component: { template: '<div />' } },
      { path: '/journeys', component: { template: '<div />' } },
    ],
  })

  await router.push(path)
  await router.isReady()

  return mount(BottomNav, { global: { plugins: [i18n, router] } })
}

describe('BottomNav', () => {
  it('orders the tabs as the V2 design does', async () => {
    const wrapper = await mountAt('/explore')

    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).toEqual([
      '/journeys',
      '/explore',
      '/wallet',
      '/reports',
      '/profile',
    ])
  })

  it('sends the last tab to the profile screen', async () => {
    const wrapper = await mountAt('/explore')

    expect(wrapper.get('a[href="/profile"]').text()).toBe('My')
  })

  it('marks the Report tab active on the report list and on a report detail route', async () => {
    const onList = await mountAt('/reports')
    expect(onList.get('a[href="/reports"]').attributes('aria-current')).toBe('page')

    const onDetail = await mountAt('/reports/42')
    expect(onDetail.get('a[href="/reports"]').attributes('aria-current')).toBe('page')
  })

  /**
   * 유리 면과 투명도 감소 대응은 조형 계약이다(#496).
   *
   * `tokens.spec.ts`가 이 클래스에서 면 색과 알파를 읽어 대비를 계산하므로, 여기서
   * 이름을 고정하지 않으면 잉크를 되돌려도 대비 계산이 옛 값 그대로 통과한다.
   *
   * 보이는 면은 `nav`가 아니라 안쪽 `ul`이 가진다(#516). `nav`는 자리만 잡는다.
   */
  it('draws the pill as canvas-backed glass with a reduced-transparency fallback', async () => {
    const wrapper = await mountAt('/explore')
    const classes = wrapper.get('nav > ul').classes()

    expect(classes).toContain('bg-canvas/90')
    expect(classes).toContain('backdrop-blur-xl')
    expect(classes).toContain('reduce-transparency:bg-canvas')
    expect(classes).toContain('reduce-transparency:backdrop-blur-none')
  })

  /** 떠 있는 알약이다(#516). 바닥에 붙는 각진 바로 되돌리면 여기서 걸린다. */
  it('floats the pill above the bottom edge instead of filling the width', async () => {
    const wrapper = await mountAt('/explore')

    const pill = wrapper.get('nav > ul').classes()
    expect(pill).toContain('rounded-pill')
    expect(pill).toContain('mb-4')
    expect(pill).toContain('shadow-raised')

    // `nav`는 자리만 잡으므로 면 색도 테두리도 갖지 않는다.
    const shell = wrapper.get('nav').classes()
    expect(shell).toContain('px-4')
    expect(shell.some((name) => name.startsWith('bg-'))).toBe(false)
    expect(shell.some((name) => name.startsWith('border'))).toBe(false)
  })

  /** 유리 면 위 잉크. 밝은 면 위 잉크(`text-on-paper`)로 되돌리면 대비가 1.68:1이 된다. */
  it('inks the active and inactive tabs for the glass surface', async () => {
    const wrapper = await mountAt('/explore')

    const active = wrapper.get('a[href="/explore"]')
    expect(active.get('span').classes()).toContain('text-ink')

    const inactive = wrapper.get('a[href="/wallet"]')
    expect(inactive.get('span').classes()).toContain('text-ink-2')
    expect(inactive.get('span').classes()).not.toContain('font-normal')
  })
  /** 라벨이 보이므로 이름은 글자가 맡는다. `aria-label`을 겹쳐 붙이면 두 번 읽힌다. */
  it('names every tab with its visible label', async () => {
    const wrapper = await mountAt('/explore')

    for (const link of wrapper.findAll('li a')) {
      expect(link.text()).not.toBe('')
      expect(link.attributes('aria-label')).toBeUndefined()
    }
  })
})
