import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { QrPaymentResolveResponse } from './qrPayment'

export interface QrPaymentSession {
  qrToken: string
  resolved: QrPaymentResolveResponse
}

/**
 * 스캔한 QR을 해석한 결과를 결제 미리보기 화면과 공유한다.
 *
 * qrToken은 결제로 바로 이어지는 값이라 URL에 남기지 않고 세션 메모리에서만 보관한다.
 * 새로고침하면 사라지고, 사용자는 다시 스캔해야 한다.
 */
export const useQrPaymentSessionStore = defineStore('wallet-qr-payment-session', () => {
  const session = ref<QrPaymentSession | null>(null)

  function setSession(next: QrPaymentSession): void {
    session.value = next
  }

  function clearSession(): void {
    session.value = null
  }

  return { session, setSession, clearSession }
})
