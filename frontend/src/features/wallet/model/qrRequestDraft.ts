import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface QrRequestDraft {
  amount: number | null
  memo: string
  payerEntersAmount: boolean
}

/**
 * QR 생성 화면에서 입력한 결제 요청 초안을 My QR 화면과 공유한다.
 *
 * 서버가 request ID를 발급하기 전까지는 URL에 금액·메모를 남기지 않기 위해
 * 현재 SPA 세션 메모리에서만 보관한다. 새로고침하면 사라진다.
 */
export const useQrRequestDraftStore = defineStore('wallet-qr-request-draft', () => {
  const draft = ref<QrRequestDraft | null>(null)

  function setDraft(next: QrRequestDraft): void {
    draft.value = next
  }

  return { draft, setDraft }
})
