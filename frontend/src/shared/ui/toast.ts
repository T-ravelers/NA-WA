import { readonly, ref } from 'vue'

/**
 * 화면 하단에 잠시 보였다 사라지는 안내 문구입니다.
 *
 * 조용히 실패하면 안 되는 동작(예: 찜 저장 실패)의 최소한의 피드백 채널로,
 * 성공/실패 구분 없이 문구 하나만 받습니다. 호스트는 `AppToastHost`가
 * `AppShell`에 한 번 마운트합니다.
 */
export interface ToastMessage {
  id: number
  message: string
}

const TOAST_DURATION_MS = 3_000

const toasts = ref<ToastMessage[]>([])
let nextToastId = 1

export function showToast(message: string): void {
  const id = nextToastId++
  toasts.value = [...toasts.value, { id, message }]
  setTimeout(() => {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }, TOAST_DURATION_MS)
}

export function useToasts() {
  return readonly(toasts)
}
