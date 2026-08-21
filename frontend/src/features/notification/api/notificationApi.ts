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
  NotificationDto,
  NotificationReadAllDto,
  UnreadNotificationCountDto,
} from './notificationApi.types'
import {
  notificationListResponseSchema,
  unreadNotificationCountResponseSchema,
} from './notificationResponseSchemas'

/**
 * 알림 목록 조회
 *
 * 최신순 정렬은 서버 몫이라 받은 순서를 그대로 쓴다
 * limit은 범위를 벗어나도 서버가 오류 대신 가능한 값으로 맞춰 준다
 */
export async function fetchNotifications(limit?: number): Promise<NotificationDto[]> {
  const { data } = await httpClient.get<NotificationDto[]>('/api/v1/notifications', {
    params: limit === undefined ? undefined : { limit },
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
 * 알림 전체 읽음 처리
 *
 * 알림 목록 화면에 들어갈 때 한 번 부른다
 */
export async function readAllNotifications(): Promise<NotificationReadAllDto> {
  const { data } = await httpClient.post<NotificationReadAllDto>('/api/v1/notifications/read-all')
  return data
}
