export type ShareResult = 'shared' | 'copied' | 'dismissed' | 'unavailable' | 'failed'

function isDismissedShare(error: unknown): boolean {
  const name = (error as { name?: unknown } | null)?.name
  return name === 'AbortError' || name === 'InvalidStateError'
}

/**
 * 네이티브 공유를 먼저 시도하고, 취소가 아닌 거절이면 클립보드로 폴백한다.
 *
 * 무엇을 공유하고 복사할지는 호출부가 정한다. 이 함수는 화면별 안내 방식도 소유하지 않는다.
 */
export async function shareWithFallback(
  data: ShareData,
  clipboardText: string,
): Promise<ShareResult> {
  if (navigator.share) {
    try {
      await navigator.share(data)
      return 'shared'
    } catch (error) {
      /*
       * `instanceof`를 쓰지 않는다. 일부 인앱 브라우저의 JS 브리지는 DOMException이 아닌
       * 이름만 가진 값을 던지고, jsdom의 DOMException도 Error를 상속하지 않는다.
       */
      if (isDismissedShare(error)) {
        return 'dismissed'
      }
    }
  }

  if (!navigator.clipboard) {
    return 'unavailable'
  }

  try {
    await navigator.clipboard.writeText(clipboardText)
    return 'copied'
  } catch {
    return 'failed'
  }
}
