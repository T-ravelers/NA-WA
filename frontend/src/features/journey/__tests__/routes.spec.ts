import { describe, expect, it } from 'vitest'

import routes from '../routes'

describe('journey routes', () => {
  it('registers authenticated list, create, detail, invite, and settings screens', () => {
    expect(routes.map(({ path, name, meta }) => ({ path, name, meta }))).toEqual([
      {
        path: '/journeys',
        name: 'journey-list',
        meta: { requiresAuth: true },
      },
      {
        path: '/journeys/new',
        name: 'journey-create',
        meta: { requiresAuth: true },
      },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        meta: { requiresAuth: true },
      },
      {
        path: '/journeys/:tripId/invite',
        name: 'journey-invite',
        meta: { requiresAuth: true, hideBottomNav: true },
      },
      {
        path: '/journeys/:tripId/settings',
        name: 'journey-settings',
        meta: { requiresAuth: true },
      },
    ])
  })
})
