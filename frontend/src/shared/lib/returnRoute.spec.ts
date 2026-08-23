import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import {
  readReturnParams,
  readReturnRouteName,
  serializeReturnParams,
  withoutReturnContract,
} from './returnRoute'

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

describe('readReturnRouteName', () => {
  it('reads a name the router actually has', () => {
    expect(readReturnRouteName({ returnRouteName: 'appointment-detail' }, testRouter())).toBe(
      'appointment-detail',
    )
  })

  it('ignores a name that is not in the route table', () => {
    // 주소창에 그대로 노출되는 값이라 누구나 바꿔 넣을 수 있다. 그대로 넘기면 매칭에
    // 실패해 복귀도 뒤로 가기도 동작하지 않고 그 화면에 갇힌다. 호출자가 없는 것으로
    // 치면 각 화면의 폴백으로 빠져나간다.
    expect(readReturnRouteName({ returnRouteName: 'nope' }, testRouter())).toBeNull()
  })

  it('treats a missing or empty name as no caller', () => {
    expect(readReturnRouteName({}, testRouter())).toBeNull()
    expect(readReturnRouteName({ returnRouteName: '' }, testRouter())).toBeNull()
  })

  it('takes the first value when the key repeats', () => {
    expect(readReturnRouteName({ returnRouteName: ['wallet', 'nope'] }, testRouter())).toBe(
      'wallet',
    )
  })
})

describe('readReturnParams', () => {
  it('reads one pair', () => {
    expect(readReturnParams({ returnParams: 'appointmentId:7' })).toEqual({ appointmentId: '7' })
  })

  it('reads several pairs joined by commas', () => {
    expect(readReturnParams({ returnParams: 'eventId:42,tab:reviews' })).toEqual({
      eventId: '42',
      tab: 'reviews',
    })
  })

  it('keeps a colon that belongs to the value', () => {
    expect(readReturnParams({ returnParams: 'at:10:30' })).toEqual({ at: '10:30' })
  })

  it('drops a pair with no key instead of making an empty one', () => {
    // 키가 비면 params에 빈 이름이 들어가 주소를 못 만든다. 조각째 버린다.
    expect(readReturnParams({ returnParams: ':42' })).toEqual({})
    expect(readReturnParams({ returnParams: ':42,eventId:7' })).toEqual({ eventId: '7' })
  })

  it('drops a piece that has no colon at all', () => {
    expect(readReturnParams({ returnParams: 'nope' })).toEqual({})
  })

  it('returns nothing when the key is missing', () => {
    expect(readReturnParams({})).toEqual({})
  })
})

describe('withoutReturnContract', () => {
  it('drops both contract keys and keeps the caller query', () => {
    expect(
      withoutReturnContract({
        returnRouteName: 'appointment-detail',
        returnParams: 'appointmentId:7',
        tripId: '9',
      }),
    ).toEqual({ tripId: '9' })
  })
})

describe('serializeReturnParams', () => {
  it('builds the format the reader expects', () => {
    expect(serializeReturnParams({ appointmentId: 7 })).toBe('appointmentId:7')
    expect(serializeReturnParams({ eventId: 42, tab: 'reviews' })).toBe('eventId:42,tab:reviews')
  })

  it('round-trips through the reader', () => {
    const serialized = serializeReturnParams({ eventId: 42, tab: 'reviews' })
    expect(readReturnParams({ returnParams: serialized })).toEqual({
      eventId: '42',
      tab: 'reviews',
    })
  })
})
