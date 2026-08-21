import type { NotificationDto } from '../api/notificationApi.types'

/**
 * 화면이 아는 알림 종류.
 *
 * 서버가 정산 밖의 새 종류를 먼저 내보내도 목록이 통째로 비지 않도록 `UNKNOWN`을 둔다.
 * 모르는 알림도 시각과 약속 이름은 읽히므로, 버리는 것보다 남기는 편이 낫다.
 */
export type NotificationKind =
  'SETTLEMENT_REQUESTED' | 'SETTLEMENT_PAID' | 'SETTLEMENT_COMPLETED' | 'UNKNOWN'

const KNOWN_KINDS: NotificationKind[] = [
  'SETTLEMENT_REQUESTED',
  'SETTLEMENT_PAID',
  'SETTLEMENT_COMPLETED',
]

export interface AppNotification {
  id: string
  kind: NotificationKind
  settlementId: string
  actorName: string
  gatheringName: string
  amount: number
  currencyCode: string
  isRead: boolean
  createdAt: string
}

export function toNotificationKind(type: string): NotificationKind {
  const normalized = type.toUpperCase()

  return KNOWN_KINDS.find((kind) => kind === normalized) ?? 'UNKNOWN'
}

/**
 * 서버 DTO를 화면 모델로 옮긴다.
 *
 * 식별자는 문자열로 통일한다. 라우터 파라미터와 쿼리 키가 모두 문자열이라, 숫자로 두면
 * 같은 알림이 화면마다 다른 값으로 잡힌다.
 */
export function toAppNotification(dto: NotificationDto): AppNotification {
  return {
    id: String(dto.id),
    kind: toNotificationKind(dto.type),
    settlementId: String(dto.settlementId),
    actorName: dto.actorName,
    gatheringName: dto.gatheringName,
    amount: Number(dto.amount),
    currencyCode: dto.currencyCode,
    // 읽은 시각이 있으면 읽은 것이다. 참/거짓을 따로 받지 않아 판단이 한 곳에만 있다.
    isRead: dto.readAt !== null && dto.readAt !== undefined && dto.readAt !== '',
    createdAt: dto.createdAt,
  }
}

/** 문구 키. 모르는 종류는 약속 이름만 보여주는 문장으로 떨어진다. */
export function notificationMessageKey(kind: NotificationKind): string {
  return `notification.item.${kind}`
}

/**
 * 정산 상세로 갈 때 어느 목록에서 들어온 셈으로 칠지.
 *
 * 정산 상세는 `query.side`로 뒤로 갈 곳을 정한다. 값을 안 주면 "낼 것"으로 떨어져서,
 * 받을 정산 알림을 눌러 들어갔다 뒤로 가면 그 정산이 없는 탭이 열린다.
 *
 * 완료 알림만은 여기서 정할 수 없다. 낸 사람과 받은 사람이 같은 알림을 받는데, 알림 한 줄에
 * 그 사람이 어느 쪽이었는지가 남아 있지 않다. 이 경우에만 정산 상세의 기본값에 맡긴다.
 */
export function settlementSideFor(kind: NotificationKind): 'received' | 'sent' | undefined {
  if (kind === 'SETTLEMENT_REQUESTED') return 'received'
  if (kind === 'SETTLEMENT_PAID') return 'sent'
  return undefined
}
