import { describe, expect, it } from 'vitest'

import routes from '../routes'

describe('settlement routes', () => {
  it('exports the supported settlement routes for automatic collection', () => {
    expect(routes.map((route) => route.name)).toEqual([
      'settlements',
      'settlement-new',
      'settlement-detail',
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
      { path: '/settlements/:settlementId', requiresAuth: true },
    ])
  })
})
