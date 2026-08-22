/**
 * 알림 백엔드 호출
 *
 * 백엔드 `NotificationController`의 엔드포인트와 1:1로 대응한다
 * 여기서는 경로와 스키마만 다룬다
 * 서버 DTO를 화면 모델로 바꾸는 일은 `model/notification.ts`에서 한다
 * httpClient가 ApiResponse를 벗겨 주므로 각 함수는 data만 반환한다
 */

import { httpClient } from '@/shared/api/httpClient'

import type {
  NotificationDeleteAllDto,
  NotificationPageDto,
  NotificationReadAllDto,
  UnreadNotificationCountDto,
} from './notificationApi.types'
import {
  notificationListResponseSchema,
  unreadNotificationCountResponseSchema,
} from './notificationResponseSchemas'

/**
 * 알림 한 쪽 조회
 *
 * 최신순 정렬은 서버 몫이라 받은 순서를 그대로 쓴다
 * limit은 범위를 벗어나도 서버가 오류 대신 가능한 값으로 맞춰 준다
 * cursor에는 직전 응답의 nextCursor를 그대로 돌려보낸다
 */
export async function fetchNotifications(
  limit?: number,
  cursor?: string,
): Promise<NotificationPageDto> {
  const params = {
    ...(limit === undefined ? {} : { limit }),
    ...(cursor === undefined ? {} : { cursor }),
  }

  const { data } = await httpClient.get<NotificationPageDto>('/api/v1/notifications', {
    params: Object.keys(params).length === 0 ? undefined : params,
    responseSchema: notificationListResponseSchema,
  })
  return data
}

/**
 * 안 읽은 알림 개수 조회
 *
 * 벨 배지가 주기적으로 부르는 요청이다
 */
export async function fetchUnreadNotificationCount(): Promise<UnreadNotificationCountDto> {
  const { data } = await httpClient.get<UnreadNotificationCountDto>(
    '/api/v1/notifications/unread-count',
    { responseSchema: unreadNotificationCountResponseSchema },
  )
  return data
}

/**
 * 알림 하나 읽음 처리
 *
 * 목록에서 알림을 누를 때 부른다
 * 이미 읽었거나 없는 알림이어도 서버가 성공으로 답한다
 */
export async function markNotificationRead(notificationId: string): Promise<void> {
  await httpClient.post(`/api/v1/notifications/${notificationId}/read`)
}

/**
 * 알림 전체 읽음 처리
 *
 * 목록 화면의 "모두 읽음"이 부른다
 */
export async function readAllNotifications(): Promise<NotificationReadAllDto> {
  const { data } = await httpClient.post<NotificationReadAllDto>('/api/v1/notifications/read-all')
  return data
}

/**
 * 알림 하나 지우기
 *
 * 카드의 X가 부른다. 서버는 행을 없애지 않고 지운 표시만 남긴다
 */
export async function deleteNotification(notificationId: string): Promise<void> {
  await httpClient.delete(`/api/v1/notifications/${notificationId}`)
}

/** 알림 모두 지우기 */
export async function deleteAllNotifications(): Promise<NotificationDeleteAllDto> {
  const { data } = await httpClient.delete<NotificationDeleteAllDto>('/api/v1/notifications')
  return data
}
