/**
 * 푸시 구독 백엔드 호출
 *
 * 백엔드 `PushSubscriptionController`의 엔드포인트와 1:1로 대응한다
 * 브라우저 API를 다루는 일은 `model/pushSubscription.ts`에서 한다
 */

import { httpClient } from '@/shared/api/httpClient'

import type { PushPublicKeyDto, PushSubscribeBody } from './pushSubscriptionApi.types'
import { pushPublicKeyResponseSchema } from './notificationResponseSchemas'

/**
 * 구독에 쓸 공개키 조회
 *
 * 환경 변수로 들고 있지 않고 서버에서 받는다. Vercel과 EC2 두 곳의 값이 어긋나면 구독은
 * 되는데 푸시가 한 건도 도착하지 않는다. 비어 있으면 서버의 푸시가 꺼져 있다는 뜻이다
 */
export async function fetchPushPublicKey(): Promise<string | null> {
  const { data } = await httpClient.get<PushPublicKeyDto>('/api/v1/push-subscriptions/public-key', {
    responseSchema: pushPublicKeyResponseSchema,
  })
  return data.publicKey ?? null
}

/**
 * 이 기기의 푸시 구독 등록
 *
 * 같은 기기를 다시 보내도 결과가 같다. 다른 계정이 같은 기기로 등록하면 서버가 소유자를 옮긴다
 */
export async function savePushSubscription(body: PushSubscribeBody): Promise<void> {
  await httpClient.put('/api/v1/push-subscriptions', body)
}

/**
 * 이 기기의 푸시 구독 해지
 *
 * 없거나 남의 구독이어도 서버가 성공으로 답한다
 */
export async function deletePushSubscription(endpoint: string): Promise<void> {
  await httpClient.delete('/api/v1/push-subscriptions', { data: { endpoint } })
}
