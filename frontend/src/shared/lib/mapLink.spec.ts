import { describe, expect, it } from 'vitest'

import { buildGoogleMapsDirectionsUrl, buildGoogleMapsSearchUrl } from './mapLink'

describe('buildGoogleMapsSearchUrl', () => {
  it('builds a pin URL from finite coordinates', () => {
    expect(buildGoogleMapsSearchUrl(37.5665, 126.978)).toBe(
      'https://www.google.com/maps/search/?api=1&query=37.5665%2C126.978',
    )
  })

  it('keeps negative coordinates intact', () => {
    expect(buildGoogleMapsSearchUrl(-33.8688, -151.2093)).toBe(
      'https://www.google.com/maps/search/?api=1&query=-33.8688%2C-151.2093',
    )
  })

  it('returns null when either coordinate is missing', () => {
    expect(buildGoogleMapsSearchUrl(null, 126.978)).toBeNull()
    expect(buildGoogleMapsSearchUrl(37.5665, null)).toBeNull()
    expect(buildGoogleMapsSearchUrl(undefined, undefined)).toBeNull()
  })

  it('returns null when a coordinate is not a finite number', () => {
    expect(buildGoogleMapsSearchUrl(Number.NaN, 126.978)).toBeNull()
    expect(buildGoogleMapsSearchUrl(37.5665, Number.POSITIVE_INFINITY)).toBeNull()
  })
})

describe('buildGoogleMapsDirectionsUrl', () => {
  it('builds a directions URL from finite coordinates', () => {
    expect(buildGoogleMapsDirectionsUrl(37.5665, 126.978)).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=37.5665%2C126.978',
    )
  })

  it('returns null when either coordinate is missing', () => {
    expect(buildGoogleMapsDirectionsUrl(null, null)).toBeNull()
    expect(buildGoogleMapsDirectionsUrl(37.5665, undefined)).toBeNull()
  })
})
