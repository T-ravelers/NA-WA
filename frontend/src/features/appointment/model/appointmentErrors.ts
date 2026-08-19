import { NormalizedApiError } from '@/shared/api/apiError'

export function appointmentErrorMessageKey(
  error: unknown,
  hasMessage: (key: string) => boolean,
): string {
  if (!(error instanceof NormalizedApiError) || !hasMessage(error.messageKey)) {
    return 'error.unknown'
  }

  return error.messageKey
}
