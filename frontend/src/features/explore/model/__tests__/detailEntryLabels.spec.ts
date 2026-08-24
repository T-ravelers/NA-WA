import { describe, expect, it } from 'vitest'

import { formatDetailEntry, isSyntheticDetailLabel } from '../detailEntryLabels'

describe('detailEntryLabels', () => {
  // 크롤러가 붙인 키 이름이라 화면 라벨이 아니다. 행에는 이미 "Hours"·"Closed"가 적혀 있다.
  it('hides the crawler-made labels and keeps the value alone', () => {
    expect(formatDetailEntry({ label: 'raw', value: '12:00 ~ 22:00' })).toBe('12:00 ~ 22:00')
    expect(formatDetailEntry({ label: 'hours', value: '12:00 ~ 22:00' })).toBe('12:00 ~ 22:00')
  })

  it('keeps meaningful labels', () => {
    expect(formatDetailEntry({ label: 'mon', value: '11:30–21:00' })).toBe('mon: 11:30–21:00')
    expect(formatDetailEntry({ label: 'regular', value: 'Mondays' })).toBe('regular: Mondays')
  })

  // 백엔드가 어떤 대소문자로 보내든 같은 판단이어야 한다.
  it('ignores casing and surrounding spaces', () => {
    expect(isSyntheticDetailLabel('RAW')).toBe(true)
    expect(isSyntheticDetailLabel('  Raw  ')).toBe(true)
    expect(isSyntheticDetailLabel('Hours')).toBe(true)
    expect(isSyntheticDetailLabel('mon')).toBe(false)
  })
})
