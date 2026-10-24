/**
 * 푸시 구독 서버 DTO 타입 정의
 *
 * 백엔드 `PushSubscriptionController`가 주고받는 모양 그대로다.
 */

/** 구독에 쓸 VAPID 공개키. 서버의 푸시가 꺼져 있으면 비어 있다. */
export interface PushPublicKeyDto {
  publicKey?: string | null
}

/** 브라우저의 `PushSubscription`을 서버가 받는 모양으로 옮긴 것. 키는 base64url이다. */
export interface PushSubscribeBody {
  endpoint: string
  p256dh: string
  auth: string
  /** 알림 문구를 만들 화면 언어. 서버가 푸시 본문에 그대로 실어 보낸다. */
  language: string
}
