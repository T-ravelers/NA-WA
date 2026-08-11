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
      expect.objectContaining({
        path: '/appointments/new',
        name: 'appointment-create',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
      expect.objectContaining({
        path: '/appointments/:appointmentId/members',
        name: 'appointment-members',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
      expect.objectContaining({
        path: '/appointments/:appointmentId/attendance',
        name: 'appointment-attendance',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
      expect.objectContaining({
        path: '/appointments/:appointmentId/reviews',
        name: 'appointment-reviews',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
      expect.objectContaining({
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        meta: { requiresAuth: true, hideBottomNav: true },
      }),
    ])
  })
})
