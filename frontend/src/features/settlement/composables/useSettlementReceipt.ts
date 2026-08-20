import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

import { NormalizedApiError } from '@/shared/api/apiError'

import { settlementGateway } from '../api/settlementGateway'
import { rejectReceiptFile } from '../model/receiptFile'

/**
 * 미리보기 주소를 만들고 되돌려주는 도우미.
 *
 * 브라우저는 이렇게 만든 주소를 스스로 정리하지 않는다. 화면을 떠날 때 반드시 풀어 줘야
 * 사진 크기만큼의 메모리가 계속 쌓이지 않는다.
 */
function useObjectUrl(): { url: Ref<string | null>; set: (blob: Blob | null) => void } {
  const url = ref<string | null>(null)

  function set(blob: Blob | null): void {
    if (url.value !== null) {
      URL.revokeObjectURL(url.value)
    }
    url.value = blob === null ? null : URL.createObjectURL(blob)
  }

  onBeforeUnmount(() => {
    set(null)
  })

  return { url, set }
}

export interface SettlementReceiptUpload {
  receiptId: Ref<string | null>
  previewUrl: Ref<string | null>
  pending: Ref<boolean>
  errorKey: Ref<string | null>
  select: (file: File) => Promise<void>
  reset: () => void
}

/**
 * 정산을 만들기 전에 영수증을 먼저 올린다.
 *
 * 고른 즉시 올려 두고 번호만 들고 있다가, 정산 생성 요청에 그 번호를 실어 보낸다. 서버가
 * 그 순서를 전제로 만들어져 있다. 미리보기는 올린 결과를 다시 받지 않고 방금 고른 파일을
 * 그대로 쓴다. 같은 사진을 두 번 내려받을 이유가 없다.
 */
export function useSettlementReceiptUpload(): SettlementReceiptUpload {
  const receiptId = ref<string | null>(null)
  const pending = ref(false)
  const errorKey = ref<string | null>(null)
  const { url: previewUrl, set: setPreview } = useObjectUrl()
  /*
   * 지금 화면이 기다리는 사진이 몇 번째인지 센다.
   *
   * 사진을 올리는 데는 시간이 걸리고, 그 사이 사용자는 다른 결제로 옮겨 가거나 다른 사진을
   * 고를 수 있다. 뒤늦게 도착한 응답을 그대로 받아 두면 이미 버린 영수증이 되살아나
   * 엉뚱한 정산에 붙는다. 한 번 붙은 영수증은 바꿀 수 없어 되돌릴 방법이 없다.
   */
  let generation = 0

  function reset(): void {
    generation += 1
    receiptId.value = null
    errorKey.value = null
    // 올리던 것을 버렸으니 기다림도 함께 끝난다. 두지 않으면 버튼이 영영 잠긴다.
    pending.value = false
    setPreview(null)
  }

  async function select(file: File): Promise<void> {
    const rejection = rejectReceiptFile(file)

    if (rejection !== null) {
      errorKey.value = `settlement.receipt.error.${rejection}`
      return
    }

    const attempt = (generation += 1)
    pending.value = true
    errorKey.value = null

    try {
      const { receiptId: uploaded } = await settlementGateway.uploadReceipt(file)

      if (attempt !== generation) return

      receiptId.value = uploaded
      setPreview(file)
    } catch (error) {
      if (attempt !== generation) return

      receiptId.value = null
      setPreview(null)
      errorKey.value = resolveUploadErrorKey(error)
    } finally {
      // 나보다 나중 것이 기다리는 중이면 그쪽이 끝낼 몫이다. 여기서 끄면 거짓으로 끝난다.
      if (attempt === generation) pending.value = false
    }
  }

  return { receiptId, previewUrl, pending, errorKey, select, reset }
}

export interface SettlementReceiptViewer {
  url: Ref<string | null>
  pending: Ref<boolean>
  errorKey: Ref<string | null>
  load: () => Promise<void>
}

/**
 * 정산에 붙은 영수증을 받아 화면에 보여줄 수 있는 주소로 바꾼다.
 *
 * img 태그에 API 주소를 그대로 박지 않는다. 로그인 정보가 쿠키로 오가는데 화면과 API가
 * 서로 다른 사이트라, 브라우저가 그런 이미지 요청에는 쿠키를 싣지 않아 거절당한다.
 */
export function useSettlementReceiptViewer(settlementId: () => string): SettlementReceiptViewer {
  const pending = ref(false)
  const errorKey = ref<string | null>(null)
  const { url, set } = useObjectUrl()

  /*
   * 보고 있는 정산이 바뀌면 받아 둔 사진을 버린다.
   *
   * 한 번 받으면 다시 받지 않는 구조라, 그대로 두면 다음 정산에서 앞 정산의 영수증이
   * 그대로 보인다.
   */
  watch(settlementId, () => {
    set(null)
    errorKey.value = null
  })

  async function load(): Promise<void> {
    if (pending.value || url.value !== null) {
      return
    }

    const requested = settlementId()
    pending.value = true
    errorKey.value = null

    try {
      const received = await settlementGateway.getReceipt(requested)

      // 받는 사이에 다른 정산으로 옮겨 갔다면 이 사진은 이제 남의 것이다.
      if (requested === settlementId()) set(received)
    } catch (error) {
      if (requested === settlementId()) {
        set(null)
        errorKey.value = resolveViewErrorKey(error)
      }
    } finally {
      pending.value = false
    }
  }

  return { url, pending, errorKey, load }
}

/**
 * 오류 코드로 문구를 고른다. 서버가 보낸 문장을 그대로 쓰지 않는다.
 *
 * 크기 초과는 공통 코드로 오고, 형식과 저장소 문제는 정산 코드로 온다.
 */
function resolveUploadErrorKey(error: unknown): string {
  if (!(error instanceof NormalizedApiError)) {
    return 'settlement.receipt.error.unknown'
  }

  switch (error.code) {
    case 'COMMON-004':
      return 'settlement.receipt.error.size'
    case 'SETTLEMENT-016':
      return 'settlement.receipt.error.type'
    case 'SETTLEMENT-019':
    case 'SETTLEMENT-021':
      return 'settlement.receipt.error.storage'
    default:
      return 'settlement.receipt.error.unknown'
  }
}

/** 보관 기한이 지나 사라진 것과 처음부터 없는 것을 구분해 알린다. */
function resolveViewErrorKey(error: unknown): string {
  if (!(error instanceof NormalizedApiError)) {
    return 'settlement.receipt.error.unknown'
  }

  switch (error.code) {
    case 'SETTLEMENT-018':
      return 'settlement.receipt.error.missing'
    case 'SETTLEMENT-020':
      return 'settlement.receipt.error.expired'
    case 'SETTLEMENT-019':
      return 'settlement.receipt.error.storage'
    default:
      return 'settlement.receipt.error.unknown'
  }
}
