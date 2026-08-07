import { useQuery, type UseQueryReturnType } from '@tanstack/vue-query'

import { NormalizedApiError } from '@/shared/api/apiError'

import { fetchWalletHome, type WalletHome } from '../api/walletApi'

import { walletQueryKeys } from './walletHome'

/**
 * 401은 재시도하지 않는다.
 *
 * `httpClient`가 401을 받으면 이미 갱신을 1회 시도하고 성공하면 원 요청을 재시도한다.
 * 여기까지 401이 올라왔다는 것은 그 복구가 실패했다는 뜻이므로, 다시 부르면 같은 실패를
 * 반복하면서 refresh와 세션 만료 처리만 요청 수만큼 더 실행된다.
 * 그 밖의 실패는 일시적 네트워크 오류일 수 있어 1회만 더 시도한다.
 */
const MAX_RETRY = 1

function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof NormalizedApiError && error.status === 401) {
    return false
  }

  return failureCount < MAX_RETRY
}

/**
 * 지갑 홈 응답을 구독한다.
 *
 * 응답의 소유자는 Vue Query다. Pinia나 localStorage에 복제하지 않는다.
 */
export function useWalletHome(): UseQueryReturnType<WalletHome, Error> {
  return useQuery({
    queryKey: walletQueryKeys.home(),
    queryFn: fetchWalletHome,
    staleTime: 30_000,
    retry: shouldRetry,
  })
}
