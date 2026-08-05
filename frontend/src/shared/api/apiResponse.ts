/**
 * 백엔드 공통 응답 봉투.
 *
 * 성공하면 `data`만, 실패하면 `error`만 포함한다.
 * 근거: backend `me.nawa.common.response.ApiResponse`
 */
export interface ApiErrorBody {
  code: string
  message: string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiErrorBody
}

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'success' in value &&
    typeof (value as ApiResponse<unknown>).success === 'boolean'
  )
}

/**
 * 오류 코드에서 화면 문구 key를 만든다.
 *
 * `AUTH-001` → `auth.errorCode.AUTH-001` 처럼 코드 접두사를 네임스페이스로 쓴다.
 * feature는 자기 메시지 파일에 `errorCode` 항목만 추가하면 되고, 공용 파일을
 * 수정할 필요가 없다. 대응 문구가 없으면 화면에서 `error.unknown`으로 폴백한다.
 */
export function resolveErrorMessageKey(code: string): string {
  const prefix = code.split('-')[0]

  if (prefix === undefined || prefix === code) {
    return 'error.unknown'
  }

  return `${prefix.toLowerCase()}.errorCode.${code}`
}
