import { httpClient } from '@/shared/api/httpClient'

/** `GET /api/v1/auth/me` 응답. 백엔드 AuthMeResponse와 1:1로 맞춘다. */
export interface AuthMeResponse {
  memberId: number
  displayName: string
  profileImageUrl: string | null
  preferredLanguage: string
  preferredCurrencyCode: string
  onboardingRequired: boolean
}

export async function fetchAuthMe(): Promise<AuthMeResponse> {
  const response = await httpClient.get<AuthMeResponse>('/api/v1/auth/me')

  return response.data
}

export async function requestSignOut(): Promise<void> {
  await httpClient.post('/api/v1/auth/logout')
}

/** 소셜 로그인 시작. 백엔드가 302로 provider 인증 페이지에 보낸다. */
export function buildAuthorizationUrl(provider: 'google' | 'line', returnPath?: string): string {
  const base = `${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/oauth2/authorization/${provider}`

  if (returnPath === undefined) {
    return base
  }

  return `${base}?returnPath=${encodeURIComponent(returnPath)}`
}
