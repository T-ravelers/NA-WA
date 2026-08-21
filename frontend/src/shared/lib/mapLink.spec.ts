import { describe, expect, it } from 'vitest'

import {
  buildGoogleMapsDirectionsUrl,
  buildGoogleMapsSearchUrl,
  buildNaverMapPlaceUrl,
  buildNaverMapTransitRouteUrl,
  detectMapPlatform,
} from './mapLink'

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

describe('detectMapPlatform', () => {
  it('detects Android from the user agent', () => {
    expect(detectMapPlatform('Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36')).toBe(
      'android',
    )
  })

  it('treats iOS and desktop as other', () => {
    expect(detectMapPlatform('Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)')).toBe(
      'other',
    )
    expect(detectMapPlatform('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)')).toBe('other')
    expect(detectMapPlatform('')).toBe('other')
  })
})

describe('buildNaverMapPlaceUrl', () => {
  it('builds an app scheme URL outside Android', () => {
    expect(buildNaverMapPlaceUrl(37.5665, 126.978, 'Seongsu Onsil', 'other')).toBe(
      'nmap://place?lat=37.5665&lng=126.978&name=Seongsu%20Onsil&appname=NA-WA',
    )
  })

  it('wraps the scheme in an intent URL on Android so a missing app falls back to the store', () => {
    expect(buildNaverMapPlaceUrl(37.5665, 126.978, 'Seongsu Onsil', 'android')).toBe(
      'intent://place?lat=37.5665&lng=126.978&name=Seongsu%20Onsil&appname=NA-WA#Intent' +
        ';scheme=nmap' +
        ';action=android.intent.action.VIEW' +
        ';category=android.intent.category.BROWSABLE' +
        ';package=com.nhn.android.nmap' +
        ';end',
    )
  })

  it('encodes names that would otherwise break the query', () => {
    expect(buildNaverMapPlaceUrl(37.5665, 126.978, '성수 Cafe & Bar', 'other')).toBe(
      'nmap://place?lat=37.5665&lng=126.978&name=%EC%84%B1%EC%88%98%20Cafe%20%26%20Bar&appname=NA-WA',
    )
  })

  it('returns null when either coordinate is missing', () => {
    expect(buildNaverMapPlaceUrl(null, 126.978, 'Seongsu Onsil', 'other')).toBeNull()
    expect(buildNaverMapPlaceUrl(37.5665, undefined, 'Seongsu Onsil', 'other')).toBeNull()
  })

  it('returns null when a coordinate is not a finite number', () => {
    expect(buildNaverMapPlaceUrl(Number.NaN, 126.978, 'Seongsu Onsil', 'other')).toBeNull()
    expect(
      buildNaverMapPlaceUrl(37.5665, Number.POSITIVE_INFINITY, 'Seongsu Onsil', 'other'),
    ).toBeNull()
  })
})

describe('buildNaverMapTransitRouteUrl', () => {
  it('builds a transit route URL without a departure so the app uses the current location', () => {
    const url = buildNaverMapTransitRouteUrl(37.5665, 126.978, 'Seongsu Onsil', 'other')

    expect(url).toBe(
      'nmap://route/public?dlat=37.5665&dlng=126.978&dname=Seongsu%20Onsil&appname=NA-WA',
    )
    expect(url).not.toContain('slat')
    expect(url).not.toContain('slng')
  })

  it('wraps the transit route in an intent URL on Android', () => {
    expect(buildNaverMapTransitRouteUrl(37.5665, 126.978, 'Seongsu Onsil', 'android')).toBe(
      'intent://route/public?dlat=37.5665&dlng=126.978&dname=Seongsu%20Onsil&appname=NA-WA#Intent' +
        ';scheme=nmap' +
        ';action=android.intent.action.VIEW' +
        ';category=android.intent.category.BROWSABLE' +
        ';package=com.nhn.android.nmap' +
        ';end',
    )
  })

  it('returns null when either coordinate is missing', () => {
    expect(buildNaverMapTransitRouteUrl(null, null, 'Seongsu Onsil', 'other')).toBeNull()
    expect(buildNaverMapTransitRouteUrl(37.5665, undefined, 'Seongsu Onsil', 'other')).toBeNull()
  })
})
