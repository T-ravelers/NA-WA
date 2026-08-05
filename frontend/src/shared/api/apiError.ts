import axios from 'axios'

import { isApiResponse, resolveErrorMessageKey } from './apiResponse'

/** 백엔드 오류 코드가 없는 전송 계층 실패에 쓰는 코드. */
export const TRANSPORT_ERROR_CODE = {
  network: 'NETWORK',
  timeout: 'TIMEOUT',
  unknown: 'UNKNOWN',
} as const

const TRANSPORT_MESSAGE_KEY: Record<string, string> = {
  [TRANSPORT_ERROR_CODE.network]: 'error.network',
  [TRANSPORT_ERROR_CODE.timeout]: 'error.timeout',
  [TRANSPORT_ERROR_CODE.unknown]: 'error.unknown',
}

/**
 * 화면이 다루는 단일 오류 형태.
 *
 * 서버가 내려준 `message`는 진단용으로만 보관하고 화면에 그대로 노출하지 않는다.
 * 화면에는 `messageKey`로 번역한 문구를 쓴다.
 */
export class NormalizedApiError extends Error {
  readonly code: string
  readonly status: number | null
  readonly messageKey: string

  constructor(code: string, status: number | null, serverMessage: string) {
    super(serverMessage)
    this.name = 'NormalizedApiError'
    this.code = code
    this.status = status
    this.messageKey = TRANSPORT_MESSAGE_KEY[code] ?? resolveErrorMessageKey(code)
  }
}

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (error instanceof NormalizedApiError) {
    return error
  }

  if (!axios.isAxiosError(error)) {
    return new NormalizedApiError(
      TRANSPORT_ERROR_CODE.unknown,
      null,
      error instanceof Error ? error.message : String(error),
    )
  }

  const body: unknown = error.response?.data

  if (isApiResponse(body) && body.error !== undefined) {
    return new NormalizedApiError(
      body.error.code,
      error.response?.status ?? null,
      body.error.message,
    )
  }

  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return new NormalizedApiError(TRANSPORT_ERROR_CODE.timeout, null, error.message)
  }

  if (error.response === undefined) {
    return new NormalizedApiError(TRANSPORT_ERROR_CODE.network, null, error.message)
  }

  return new NormalizedApiError(TRANSPORT_ERROR_CODE.unknown, error.response.status, error.message)
}
