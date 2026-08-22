import { z } from 'zod'

/** 서버가 보내는 금액은 숫자일 수도 문자열일 수도 있다. 화면이 쓰는 형태로 바꾸는 일은 매퍼가 한다. */
const apiAmountSchema = z.union([z.string(), z.number().finite()])

/**
 * 알림 한 줄.
 *
 * 알림 종류는 문자열 그대로 받는다. 서버가 정산 밖의 새 종류를 먼저 내보내더라도 목록
 * 전체가 검증에서 막히면 안 되기 때문이다. 모르는 종류를 어떻게 보여줄지는 model이 정한다.
 *
 * 서버가 필드를 더 붙여도 깨지지 않도록 passthrough를 쓴다.
 */
const notificationSchema = z
  .object({
    id: z.union([z.string(), z.number()]),
    type: z.string(),
    settlementId: z.union([z.string(), z.number()]),
    actorName: z.string(),
    gatheringName: z.string(),
    amount: apiAmountSchema,
    currencyCode: z.string(),
    readAt: z.string().nullish(),
    createdAt: z.string(),
  })
  .passthrough()

/**
 * 알림 한 쪽.
 *
 * 이 응답을 런타임에서 확인하는 이유는, 모양이 어긋나도 오류 없이 빈 목록처럼 보이기
 * 때문이다. 사용자는 알림이 없는 것인지 화면이 못 읽은 것인지 구분할 수 없다.
 *
 * `nextCursor`는 없을 수도, `null`일 수도 있다. 둘 다 "더 볼 것이 없다"는 같은 뜻이라
 * 굳이 갈라 받지 않는다.
 */
export const notificationListResponseSchema = z
  .object({
    notifications: z.array(notificationSchema),
    nextCursor: z.string().nullish(),
  })
  .passthrough()

/** 벨 배지가 이 숫자 하나만 본다. 모양이 어긋나면 배지가 조용히 사라진다. */
export const unreadNotificationCountResponseSchema = z
  .object({ count: z.number().int().nonnegative() })
  .passthrough()
