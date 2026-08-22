import { describe, expect, it } from 'vitest'

import { historyDetailQuery, resolveDetailBackTarget } from '../settlementReturn'

describe('historyDetailQuery', () => {
  it('carries the side and the period the viewer had narrowed to', () => {
    expect(historyDetailQuery('sent', { from: '2026-07-01', to: '2026-07-31' })).toEqual({
      side: 'sent',
      origin: 'history',
      from: '2026-07-01',
      to: '2026-07-31',
    })
  })

  /*
   * 기간을 고르지 않고 들어온 경우에도 나갈 때는 전체 내역으로 돌아가야 한다. 그래서
   * 기간이 없어도 어디서 왔는지는 남긴다.
   */
  it('still marks where it came from when no period was chosen', () => {
    expect(historyDetailQuery('received', null)).toEqual({
      side: 'received',
      origin: 'history',
    })
  })
})

describe('resolveDetailBackTarget', () => {
  it('goes back to the settlement home when it was not opened from the history screen', () => {
    expect(resolveDetailBackTarget({ side: 'sent' }, 'sent')).toEqual({
      name: 'settlements',
      query: { side: 'sent' },
    })
  })

  it('goes back to the history screen with the same period', () => {
    expect(
      resolveDetailBackTarget(
        { side: 'sent', origin: 'history', from: '2026-07-01', to: '2026-07-31' },
        'sent',
      ),
    ).toEqual({
      name: 'settlement-history',
      query: { side: 'sent', from: '2026-07-01', to: '2026-07-31' },
    })
  })

  it('goes back to the whole history when it was opened without a period', () => {
    expect(resolveDetailBackTarget({ side: 'received', origin: 'history' }, 'received')).toEqual({
      name: 'settlement-history',
      query: { side: 'received' },
    })
  })

  /*
   * 주소는 사용자가 직접 고칠 수 있다. 받은 값을 그대로 돌려주면 돌아간 화면이 보여 주는
   * 것과 주소에 적힌 것이 어긋난다. 전체 내역 화면이 읽는 규칙으로 맞춰 돌려준다.
   */
  it('turns a hand edited period around instead of handing it back as it is', () => {
    expect(
      resolveDetailBackTarget(
        { origin: 'history', from: '2026-07-31', to: '2026-07-01' },
        'received',
      ),
    ).toEqual({
      name: 'settlement-history',
      query: { side: 'received', from: '2026-07-01', to: '2026-07-31' },
    })
  })

  it('reads a half written period as that one day', () => {
    expect(resolveDetailBackTarget({ origin: 'history', from: '2026-07-05' }, 'sent')).toEqual({
      name: 'settlement-history',
      query: { side: 'sent', from: '2026-07-05', to: '2026-07-05' },
    })
  })

  it('drops a period that is not a date', () => {
    expect(
      resolveDetailBackTarget({ origin: 'history', from: 'yesterday', to: '' }, 'sent'),
    ).toEqual({
      name: 'settlement-history',
      query: { side: 'sent' },
    })
  })

  /** 같은 이름이 두 번 적히면 주소 값은 배열로 온다. 첫 값만 본다. */
  it('reads a repeated origin from its first value', () => {
    expect(resolveDetailBackTarget({ origin: ['history', 'nowhere'] }, 'sent')).toEqual({
      name: 'settlement-history',
      query: { side: 'sent' },
    })
  })
})
