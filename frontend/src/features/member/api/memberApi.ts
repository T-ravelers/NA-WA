import { httpClient } from '@/shared/api/httpClient'
import type { AppLocale } from '@/shared/i18n/locales'

/** `GET`·`PATCH /api/v1/members/me` 응답. 백엔드 MemberProfileResponse와 1:1로 맞춘다. */
export interface MemberProfile {
  memberId: number
  displayName: string
  profileImageUrl: string | null
  preferredLanguage: string
  preferredCurrencyCode: string | null
  onboardingRequired: boolean
}

export interface UpdateMemberProfilePayload {
  preferredLanguage?: AppLocale
  preferredCurrencyCode?: string
}

/**
 * 현재 회원 프로필.
 *
 * 인증이 없으면 401이므로 이 호출 하나가 세션 확인을 겸한다. 회원 레코드가 없으면
 * 404 `MEMBER-001`이 오는데, 그것도 서비스를 쓸 수 없는 상태이므로 호출자는 미인증과
 * 같게 다룬다.
 *
 * 401 시 갱신 후 재시도까지는 공통 인터셉터가 처리하지만, 갱신마저 실패했을 때의 화면
 * 이동은 라우터 guard가 결정한다. 여기서 전역 리다이렉트가 끼어들면 guard가 만들던
 * `returnPath` 리다이렉트를 취소해 복귀 경로가 사라진다.
 */
export async function fetchMemberProfile(): Promise<MemberProfile> {
  const response = await httpClient.get<MemberProfile>('/api/v1/members/me', {
    suppressSessionExpiredRedirect: true,
  })

  return response.data
}

export async function updateMemberProfile(
  payload: UpdateMemberProfilePayload,
): Promise<MemberProfile> {
  const response = await httpClient.patch<MemberProfile>('/api/v1/members/me', payload)

  return response.data
}
