import { afterAll, describe, expect, it } from 'vitest'

import { i18n } from '..'

/**
 * 개수 문구의 복수형(#412).
 *
 * 실제 메시지 카탈로그를 그대로 쓴다 — 화면을 마운트하지 않고도 「1일 때 어떻게 읽히는가」를
 * 키 단위로 고정할 수 있고, 로케일 파일이 갈래를 잃으면 여기서 바로 드러난다.
 *
 * 영어만 굴절한다. ja·zh-TW·vi는 CLDR 복수 범주가 하나뿐이라 두 갈래에 같은 문구를 둔다
 * (선례: `settlement.create.paymentCount`). 그래서 세 로케일은 「개수가 달라도 문구가 같다」를 본다.
 */
const t = i18n.global.t
const originalLocale = i18n.global.locale.value

afterAll(() => {
  i18n.global.locale.value = originalLocale
})

const COUNTED = [
  ['report.list.eventCount', '1 event', '2 events', '0 events'],
  ['report.list.placeCount', '1 place', '2 places', '0 places'],
  ['journey.list.eventCount', '1 event', '2 events', '0 events'],
  ['journey.list.placeCount', '1 place', '2 places', '0 places'],
  ['journey.delete.itemCount', '1 itinerary item', '2 itinerary items', '0 itinerary items'],
  ['explore.resultCount', '1 event', '2 events', '0 events'],
  ['explore.placeResultCount', '1 place', '2 places', '0 places'],
  ['appointment.list.resultCount', '1 appointment', '2 appointments', '0 appointments'],
] as const

describe('counted messages', () => {
  it.each(COUNTED)('%s reads singular at one (en)', (key, one, many, none) => {
    i18n.global.locale.value = 'en'

    expect(t(key, { count: 1 })).toBe(one)
    expect(t(key, { count: 2 })).toBe(many)
    // 0은 영어에서 복수다. 「검색 결과 0건」이 `0 event`가 되면 안 된다.
    expect(t(key, { count: 0 })).toBe(none)
  })

  // 도넛 가운데 라벨만 숫자를 따로 그리므로 문구에 `{count}`가 없다.
  it('the donut centre label follows the event count (en)', () => {
    i18n.global.locale.value = 'en'

    expect(t('report.detail.categoryCenterLabel', 1)).toBe('event')
    expect(t('report.detail.categoryCenterLabel', 2)).toBe('events')
  })

  it.each(['ja', 'zh-TW', 'vi'] as const)('%s keeps one wording for every count', (locale) => {
    i18n.global.locale.value = locale

    for (const [key] of COUNTED) {
      // 같은 개수를 넣고 갈래만 바꾼다 — 굴절이 없으므로 결과가 같아야 한다.
      expect(t(key, { count: 3 }, 1)).toBe(t(key, { count: 3 }, 2))
      expect(t(key, { count: 3 }, 1)).toContain('3')
    }

    expect(t('report.detail.categoryCenterLabel', 1)).toBe(
      t('report.detail.categoryCenterLabel', 2),
    )
  })
})
