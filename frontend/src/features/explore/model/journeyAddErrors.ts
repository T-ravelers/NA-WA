import { NormalizedApiError } from '@/shared/api/apiError'

/** 담기 실패에서 원인을 알 수 없을 때 보여 줄 문구. */
const FALLBACK_MESSAGE_KEY = 'explore.journeyDate.addItemFailed'

/**
 * 담기 실패를 원인별 문구 key로 바꾼다.
 *
 * 서버는 이미 `JOURNEY-007`(여정 기간 밖)·`JOURNEY-012`(항목 운영 기간 밖)처럼 정확한
 * 이유를 준다. 전부 "다시 시도해 주세요"로 뭉개면 **다시 시도해도 절대 성공하지 않는
 * 실패에 다시 시도하라고 말하게 된다.**
 *
 * `NormalizedApiError.messageKey`가 이미 `JOURNEY-007` → `journey.errorCode.JOURNEY-007`로
 * 풀려 있으므로 여기서는 문구가 실제로 있는지만 확인한다. journey feature가 그 문구를
 * 소유하지만 i18n 네임스페이스는 전역이라 import하지 않는다 — explore는 journey 내부를
 * import할 수 없다.
 *
 * 폴백이 `error.unknown`이 아닌 것은 의도한 것이다. 이 흐름에서는 "담지 못했다"는
 * 맥락까지 있는 기존 문구가 맨몸의 알 수 없는 오류보다 낫다.
 */
export function journeyAddErrorMessageKey(
  error: unknown,
  hasMessage: (key: string) => boolean,
): string {
  if (!(error instanceof NormalizedApiError) || !hasMessage(error.messageKey)) {
    return FALLBACK_MESSAGE_KEY
  }

  return error.messageKey
}
