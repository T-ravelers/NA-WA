import type { Ref } from 'vue'
import { inject, type InjectionKey } from 'vue'

/**
 * 지갑 홈의 벨이 알림 쪽에 요구하는 최소한의 것.
 *
 * 지갑은 안 읽은 개수 하나만 알면 배지를 그릴 수 있다. 알림 목록도, 읽음 처리도 알 필요가
 * 없어서 계약을 그만큼만 둔다. feature끼리 직접 import하지 않고 `main.ts`가 이어 주는 것은
 * 약속 정보를 받아 쓰는 기존 방식과 같다.
 */
export interface WalletUnreadNotificationCount {
  data: Ref<number | undefined>
}

export interface WalletNotificationIntegration {
  useUnreadNotificationCount: () => WalletUnreadNotificationCount
}

export const walletNotificationIntegrationKey: InjectionKey<WalletNotificationIntegration> = Symbol(
  'walletNotificationIntegration',
)

export function useWalletNotificationIntegration(): WalletNotificationIntegration {
  const integration = inject(walletNotificationIntegrationKey)
  if (!integration) throw new Error('Wallet notification integration is not configured.')
  return integration
}
