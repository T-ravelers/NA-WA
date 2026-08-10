import { describe, expect, it } from 'vitest'

import routes from '../routes'

describe('journey routes', () => {
  it('registers authenticated list, create, and detail screens', () => {
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
    ])
  })
})
