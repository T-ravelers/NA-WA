/** 서버가 받아 주는 영수증 이미지 형식. 백엔드의 허용 목록과 같아야 한다. */
export const RECEIPT_ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

/**
 * 영수증 한 장의 최대 크기. 백엔드 기본값(RECEIPT_MAX_UPLOAD_BYTES)과 맞춘다.
 *
 * 서버도 같은 검사를 하지만 여기서 먼저 걸러야 큰 파일을 헛되이 올리지 않는다.
 */
export const RECEIPT_MAX_BYTES = 8 * 1024 * 1024

export type ReceiptFileRejection = 'type' | 'size'

/** 올릴 수 있는 파일인지 본다. 문제가 없으면 null이다. */
export function rejectReceiptFile(file: File): ReceiptFileRejection | null {
  if (!RECEIPT_ALLOWED_TYPES.some((allowed) => allowed === file.type)) {
    return 'type'
  }

  if (file.size > RECEIPT_MAX_BYTES) {
    return 'size'
  }

  return null
}
