import { inject, type InjectionKey } from 'vue'

/**
 * 알림이 정산 쪽에 요구하는 최소한의 것.
 *
 * 새 알림이 왔다는 것은 곧 정산 상태가 바뀌었다는 뜻이다. 벨 숫자만 올라가고 정산 화면은
 * 옛날 값을 그대로 들고 있으면, 사용자는 두 화면 중 어느 쪽을 믿어야 할지 알 수 없다.
 *
 * 그렇다고 알림 feature가 정산 feature를 직접 import하면 두 도메인이 서로를 알게 된다.
 * 지갑이 약속 정보를 받아 쓰는 방식과 똑같이 `main.ts`가 이어 준다.
 */
export interface NotificationSettlementIntegration {
  /** 정산 목록·상세 캐시를 낡은 것으로 표시한다. 다시 볼 때 새로 받아 온다. */
  invalidateSettlements: () => void
}

export const notificationSettlementIntegrationKey: InjectionKey<NotificationSettlementIntegration> =
  Symbol('notificationSettlementIntegration')

export function useNotificationSettlementIntegration(): NotificationSettlementIntegration {
  const integration = inject(notificationSettlementIntegrationKey)
  if (!integration) throw new Error('Notification settlement integration is not configured.')
  return integration
}
