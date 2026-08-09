import { describe, expect, it } from 'vitest'

import { normalizePlaceListResponse, toImageUrls } from '../placeExplore'

describe('placeExplore model helpers', () => {
  it('normalizes nullable and invalid image entries', () => {
    expect(toImageUrls(null)).toEqual([])
    expect(toImageUrls([])).toEqual([])
    expect(toImageUrls(['place.jpg', '', 42, ' detail.jpg '])).toEqual([
      'place.jpg',
      ' detail.jpg ',
    ])
  })

  it('normalizes a nullable Place list payload for the screen', () => {
    expect(
      normalizePlaceListResponse({
        content: [
          {
            itemId: 880001,
            name: 'Seongsu Market',
            brand: null,
            branch: null,
            placeKind: 'MARKET',
            thumbnailUrl: null,
            imageUrls: ['market.jpg', null],
            region1: 'Seoul',
            region2: 'Seongsu',
            region3: null,
            addressRoad: null,
            addressDetail: null,
            latitude: null,
            longitude: null,
            isActive: null,
            viewCount: 0,
            favoriteCount: 0,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    ).toMatchObject({
      content: [{ itemId: 880001, imageUrls: ['market.jpg'], isActive: false }],
    })

    expect(
      normalizePlaceListResponse({
        content: null,
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      }).content,
    ).toEqual([])
  })
})
