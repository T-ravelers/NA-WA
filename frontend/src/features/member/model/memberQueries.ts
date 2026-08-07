import { useQuery, type UseQueryReturnType } from '@tanstack/vue-query'

import { queryClient } from '@/app/query/client'

import { fetchMemberProfile, type MemberProfile } from '../api/memberApi'

export const memberQueryKeys = {
  all: ['member'] as const,
  profile: () => [...memberQueryKeys.all, 'profile'] as const,
}

/**
 * 현재 회원 프로필을 확보한다.
 *
 * 프로필의 소유자는 Vue Query다. Pinia나 localStorage에 복제하지 않는다.
 * 라우터 guard가 화면 진입마다 호출하므로 캐시된 값을 재사용한다.
 * 세션이 없거나 회원을 찾을 수 없으면 `null`을 돌려주고 오류로 취급하지 않는다.
 */
export async function ensureMemberProfile(): Promise<MemberProfile | null> {
  try {
    return await queryClient.ensureQueryData({
      queryKey: memberQueryKeys.profile(),
      queryFn: fetchMemberProfile,
      staleTime: 30_000,
    })
  } catch {
    return null
  }
}

/**
 * 화면에서 회원 프로필을 구독한다.
 *
 * guard가 이미 같은 key로 채워 둔 캐시를 재사용하므로 화면 진입에서 추가 요청이 생기지
 * 않는다. guard와 달리 여기서는 실패를 삼키지 않는다. 화면은 오류 상태를 그려야 한다.
 */
export function useMemberProfile(): UseQueryReturnType<MemberProfile, Error> {
  return useQuery({
    queryKey: memberQueryKeys.profile(),
    queryFn: fetchMemberProfile,
    staleTime: 30_000,
  })
}

/** PATCH 응답으로 캐시를 갱신한다. 재조회를 유발하지 않는다. */
export function setMemberProfile(profile: MemberProfile): void {
  queryClient.setQueryData(memberQueryKeys.profile(), profile)
}

export function clearMemberProfile(): void {
  queryClient.removeQueries({ queryKey: memberQueryKeys.all })
}
