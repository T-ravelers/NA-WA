import { describe, expect, it } from 'vitest'

import { RECEIPT_MAX_BYTES, rejectReceiptFile } from '../receiptFile'

function file(type: string, size: number): File {
  const created = new File([new Uint8Array(1)], 'receipt', { type })
  Object.defineProperty(created, 'size', { value: size })
  return created
}

describe('rejectReceiptFile', () => {
  it('accepts the formats the server accepts', () => {
    expect(rejectReceiptFile(file('image/jpeg', 1024))).toBeNull()
    expect(rejectReceiptFile(file('image/png', 1024))).toBeNull()
    expect(rejectReceiptFile(file('image/webp', 1024))).toBeNull()
  })

  it('rejects formats outside the allow list', () => {
    // 아이폰 기본 촬영 형식이라 실제로 자주 들어온다.
    expect(rejectReceiptFile(file('image/heic', 1024))).toBe('type')
    expect(rejectReceiptFile(file('application/pdf', 1024))).toBe('type')
    expect(rejectReceiptFile(file('', 1024))).toBe('type')
  })

  it('rejects a photo past the size limit', () => {
    expect(rejectReceiptFile(file('image/png', RECEIPT_MAX_BYTES))).toBeNull()
    expect(rejectReceiptFile(file('image/png', RECEIPT_MAX_BYTES + 1))).toBe('size')
  })

  it('checks the format before the size', () => {
    // 형식부터 틀렸다면 크기를 말해 봐야 사용자가 두 번 고치게 된다.
    expect(rejectReceiptFile(file('image/heic', RECEIPT_MAX_BYTES + 1))).toBe('type')
  })
})
