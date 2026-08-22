import { describe, expect, it } from 'vitest'

import { intersectItemJourneyPeriod } from '../journeyPeriod'

const AUGUST = { startDate: '2026-08-10', endDate: '2026-08-20' }

describe('intersectItemJourneyPeriod', () => {
  it('여정 기간 안에 완전히 들어가는 이벤트는 이벤트 기간을 그대로 돌려준다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-08-12', endDate: '2026-08-14' }, AUGUST),
    ).toEqual({ start: '2026-08-12', end: '2026-08-14' })
  })

  it('여정보다 넓은 이벤트는 여정 기간으로 잘린다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-07-01', endDate: '2026-09-30' }, AUGUST),
    ).toEqual({ start: '2026-08-10', end: '2026-08-20' })
  })

  it('앞쪽만 겹치면 겹치는 구간만 남는다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-08-01', endDate: '2026-08-12' }, AUGUST),
    ).toEqual({ start: '2026-08-10', end: '2026-08-12' })
  })

  it('뒤쪽만 겹치면 겹치는 구간만 남는다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-08-18', endDate: '2026-08-30' }, AUGUST),
    ).toEqual({ start: '2026-08-18', end: '2026-08-20' })
  })

  it('하루만 맞닿아도 담을 수 있다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-08-20', endDate: '2026-08-25' }, AUGUST),
    ).toEqual({ start: '2026-08-20', end: '2026-08-20' })
  })

  it('이벤트가 여정보다 나중이면 null이다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-09-04', endDate: '2026-09-06' }, AUGUST),
    ).toBeNull()
  })

  it('이벤트가 여정보다 먼저 끝났으면 null이다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-07-01', endDate: '2026-08-09' }, AUGUST),
    ).toBeNull()
  })

  // 상시 이벤트는 endDate가 null이다. 상한만 없고 하한은 지켜야 한다.
  it('상시 이벤트는 시작일 이후만 열린다', () => {
    expect(intersectItemJourneyPeriod({ startDate: '2026-08-15', endDate: null }, AUGUST)).toEqual({
      start: '2026-08-15',
      end: '2026-08-20',
    })
  })

  it('여정이 끝난 뒤에 시작하는 상시 이벤트는 null이다', () => {
    expect(
      intersectItemJourneyPeriod({ startDate: '2026-08-21', endDate: null }, AUGUST),
    ).toBeNull()
  })

  // Place는 운영 기간이라는 개념 자체가 없다.
  it('기간이 없는 항목은 여정 기간 전체가 열린다', () => {
    expect(intersectItemJourneyPeriod({ startDate: null, endDate: null }, AUGUST)).toEqual({
      start: '2026-08-10',
      end: '2026-08-20',
    })
  })

  it('하루짜리 여정도 다룬다', () => {
    expect(
      intersectItemJourneyPeriod(
        { startDate: '2026-08-01', endDate: '2026-08-31' },
        { startDate: '2026-08-15', endDate: '2026-08-15' },
      ),
    ).toEqual({ start: '2026-08-15', end: '2026-08-15' })
  })
})
