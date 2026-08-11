import { describe, expect, it } from 'vitest'

import routes from '../routes'

describe('appointment routes', () => {
  it('registers the authenticated appointment list screen', () => {
    expect(routes).toEqual([
      expect.objectContaining({
        path: '/appointments',
        name: 'appointment-list',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
    ])
  })
})
