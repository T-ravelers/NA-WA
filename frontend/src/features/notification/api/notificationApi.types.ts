/**
 * 알림 서버 DTO 타입 정의
 *
 * 백엔드 `NotificationController`가 내려주는 모양 그대로다. 화면이 쓰는 모양으로
 * 바꾸는 일은 `model/notification.ts`가 맡는다.
 */

/** 서버가 보내는 금액. 숫자로 올 수도 문자열로 올 수도 있다. */
export type ApiAmount = string | number

export type NotificationTypeDto =
  'SETTLEMENT_REQUESTED' | 'SETTLEMENT_PAID' | 'SETTLEMENT_COMPLETED'

export interface NotificationDto {
  id: string | number
  type: string
  settlementId: string | number
  actorName: string
  gatheringName: string
  amount: ApiAmount
  currencyCode: string
  /** 아직 안 읽었으면 비어 있다. */
  readAt?: string | null
  createdAt: string
}

/**
 * 알림 한 쪽.
 *
 * 목록을 배열 그대로 받지 않고 감싼 모양인 것은 `nextCursor`를 실을 자리가 필요해서다.
 * 이 값이 비어 있으면 더 볼 것이 없다는 뜻이고, 있으면 그대로 다음 요청에 돌려보낸다.
 */
export interface NotificationPageDto {
  notifications: NotificationDto[]
  nextCursor?: string | null
}

export interface UnreadNotificationCountDto {
  count: number
}

export interface NotificationReadAllDto {
  updatedCount: number
}

export interface NotificationDeleteAllDto {
  deletedCount: number
}
