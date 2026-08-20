import { z } from 'zod'

/**
 * 서버가 보내는 금액은 숫자일 수도 문자열일 수도 있다. 화면이 쓰는 형태로 통일하는 일은
 * 매퍼가 하므로 여기서는 둘 다 받아 준다.
 */
const apiAmountSchema = z.union([z.string(), z.number().finite()])

/**
 * 영수증에서 읽어낸 품목 한 줄.
 *
 * 못 읽은 자리는 null로 온다. 그 줄을 버리지 않아야 사용자가 빈 칸만 채워 넣을 수 있다.
 */
const receiptOcrItemSchema = z
  .object({
    name: z.string().nullable(),
    unitPrice: apiAmountSchema.nullable(),
    quantity: apiAmountSchema.nullable(),
  })
  .passthrough()

/**
 * 영수증 글자 인식 응답.
 *
 * 이 응답만 런타임에서 확인하는 이유는, 값이 그대로 품목 입력란에 들어가기 때문이다. 모양이
 * 어긋나면 오류가 나는 대신 빈 품목 카드가 조용히 만들어지고, 사용자는 인식이 실패한 것인지
 * 영수증이 흐린 것인지 알 수 없다.
 *
 * 서버가 필드를 더 붙여도 깨지지 않도록 passthrough를 쓴다.
 */
export const settlementReceiptOcrResponseSchema = z
  .object({
    items: z.array(receiptOcrItemSchema),
    recognizedTotal: apiAmountSchema.nullable(),
  })
  .passthrough()
