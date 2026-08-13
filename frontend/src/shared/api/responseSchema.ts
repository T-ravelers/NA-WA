import type { AxiosRequestConfig } from 'axios'
import { z } from 'zod'

import { NormalizedApiError, TRANSPORT_ERROR_CODE } from './apiError'

/**
 * 요청별 성공 응답 검증 스키마.
 *
 * Zod의 parse 결과는 반환하지 않는다. 스키마는 응답이 화면 계약에 맞는지 확인하는
 * 경계로만 사용하고, 실제 요청자는 서버가 보낸 원본 data를 계속 받는다.
 */
export type ResponseSchema = z.ZodType<unknown>

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 성공 ApiResponse.data를 검증할 때만 지정한다. */
    responseSchema?: ResponseSchema
  }
}

interface SanitizedValidationIssue {
  path: Array<string | number>
  code: string
  expected?: string
}

function toSanitizedIssue(issue: z.core.$ZodIssue): SanitizedValidationIssue {
  const expected = 'expected' in issue ? issue.expected : undefined

  return {
    path: issue.path.filter(
      (segment): segment is string | number =>
        typeof segment === 'string' || typeof segment === 'number',
    ),
    code: issue.code,
    ...(typeof expected === 'string' ? { expected } : {}),
  }
}

function sanitizeRequestUrl(url: string | undefined): string | undefined {
  return url?.split(/[?#]/, 1)[0]
}

/**
 * 성공 응답의 data를 요청에 지정된 스키마로 확인한다.
 *
 * 실패 로그에는 URL·method·status와 Zod의 구조적 위치만 남긴다. 응답 본문, issue.message,
 * issue.input과 Axios 오류 전체는 절대 출력하지 않는다.
 */
export function validateResponseData(
  config: Pick<AxiosRequestConfig, 'url' | 'method' | 'responseSchema'>,
  status: number,
  data: unknown,
): void {
  const schema = config.responseSchema
  if (schema === undefined) return

  const result = schema.safeParse(data)
  if (result.success) return

  console.error('API response validation failed', {
    url: sanitizeRequestUrl(config.url),
    method: config.method?.toUpperCase(),
    status,
    issues: result.error.issues.map(toSanitizedIssue),
  })

  throw new NormalizedApiError(
    TRANSPORT_ERROR_CODE.unknown,
    status,
    'Internal response validation error',
  )
}
