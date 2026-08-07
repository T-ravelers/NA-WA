import { NormalizedApiError } from '@/shared/api/apiError'

export function journeyErrorMessageKey(
  error: unknown,
  hasMessage: (key: string) => boolean,
): string {
  if (!(error instanceof NormalizedApiError) || !hasMessage(error.messageKey)) {
    return 'error.unknown'
  }

  return error.messageKey
}

export function isJourneyForbidden(error: unknown): boolean {
  return (
    error instanceof NormalizedApiError && (error.code === 'JOURNEY-002' || error.status === 403)
  )
}
