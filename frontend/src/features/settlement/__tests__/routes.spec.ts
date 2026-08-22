import { describe, expect, it } from 'vitest'

import routes from '../routes'

describe('settlement routes', () => {
  it('exports the supported settlement routes for automatic collection', () => {
    expect(routes.map((route) => route.name)).toEqual([
      'settlements',
      'settlement-new',
      'settlement-history',
      'settlement-detail',
      'settlement-requested',
      'settlement-pay',
      'settlement-pay-complete',
    ])
  })

  it('requires authentication for every supported settlement route', () => {
    expect(
      routes.map((route) => ({
        path: route.path,
        requiresAuth: route.meta?.requiresAuth,
      })),
    ).toEqual([
      { path: '/settlements', requiresAuth: true },
      { path: '/settlements/new', requiresAuth: true },
      { path: '/settlements/history', requiresAuth: true },
      { path: '/settlements/:settlementId', requiresAuth: true },
      { path: '/settlements/:settlementId/requested', requiresAuth: true },
      { path: '/settlements/:settlementId/pay', requiresAuth: true },
      { path: '/settlements/:settlementId/pay/complete', requiresAuth: true },
    ])
  })

  it('hides the bottom tab bar on every settlement screen', () => {
    // 정산은 하단 탭의 목적지가 아니라 지갑에서 들어오는 하위 흐름이다.
    // 한 화면이라도 탭이 남으면 그 화면에서만 흐름을 벗어나는 길이 생긴다.
    expect(routes.map((route) => route.meta?.hideBottomNav)).toEqual(routes.map(() => true))
  })

  it('registers the fixed segments before the settlement id parameter', () => {
    const paths = routes.map((route) => route.path)

    expect(paths.indexOf('/settlements/new')).toBeLessThan(
      paths.indexOf('/settlements/:settlementId'),
    )
    expect(paths.indexOf('/settlements/history')).toBeLessThan(
      paths.indexOf('/settlements/:settlementId'),
    )
  })
})
