import { NormalizedApiError } from '@/shared/api/apiError'

export function reportErrorMessageKey(
  error: unknown,
  hasMessage: (key: string) => boolean,
): string {
  if (!(error instanceof NormalizedApiError) || !hasMessage(error.messageKey)) {
    return 'error.unknown'
  }

  return error.messageKey
}

export function isReportForbidden(error: unknown): boolean {
  return (
    error instanceof NormalizedApiError && (error.code === 'REPORT-002' || error.status === 403)
  )
}

export function isReportNotFound(error: unknown): boolean {
  return (
    error instanceof NormalizedApiError && (error.code === 'REPORT-001' || error.status === 404)
  )
}

export function isReportConflict(error: unknown): boolean {
  return error instanceof NormalizedApiError && error.code === 'REPORT-005'
}
