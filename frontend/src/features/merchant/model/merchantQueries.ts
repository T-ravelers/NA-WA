import { computed, type Ref } from 'vue'
import { useQuery, type UseQueryReturnType } from '@tanstack/vue-query'

import { serializeCalendarDate } from '@/shared/lib/datetime'

import {
  fetchMerchantAccount,
  fetchMerchantIncome,
  type MerchantAccount,
  type MerchantIncomeResponse,
} from '../api/merchantApi'

export const merchantKeys = {
  all: ['merchant'] as const,
  account: () => [...merchantKeys.all, 'account'] as const,
  income: () => [...merchantKeys.all, 'income'] as const,
  incomeRange: (from: string, to: string) => [...merchantKeys.income(), from, to] as const,
}

/**
 * 이 화면이 쓰는 계정 정보를 구독한다.
 *
 * 라우터 guard도 같은 엔드포인트를 보지만 캐시는 서로 다르다. feature 간 import 금지
 * 때문에 guard의 회원 캐시를 여기서 직접 건드리지 않는다. 등록 후 두 캐시를 맞추는 방법은
 * 화면 쪽 주석을 참고한다.
 */
export function useMerchantAccount(): UseQueryReturnType<MerchantAccount, Error> {
  return useQuery({
    queryKey: merchantKeys.account(),
    queryFn: fetchMerchantAccount,
    staleTime: 30_000,
  })
}

/**
 * 조회 기준일. 서버 시각대(Asia/Seoul)로 오늘 하루를 뜻한다.
 *
 * `from`과 `to`는 둘 다 포함이며 백엔드가 `to`를 다음날 0시 미만으로 바꿔 처리한다.
 */
export function todayRange(now: Date = new Date()): { from: string; to: string } {
  const today = serializeCalendarDate(now)

  return { from: today, to: today }
}

/**
 * 오늘 매출을 구독한다.
 *
 * 응답의 소유자는 Vue Query다. Pinia에 복제하지 않는다.
 *
 * 아직 가맹점이 아닌 계정에서는 조회하지 않는다. 등록 전 화면은 상호명 입력만 보여주므로
 * 요청을 보내 봐야 쓰이지 않는다.
 */
export function useMerchantIncome(
  range: Ref<{ from: string; to: string }>,
  enabled: Ref<boolean>,
): UseQueryReturnType<MerchantIncomeResponse, Error> {
  return useQuery({
    queryKey: computed(() => merchantKeys.incomeRange(range.value.from, range.value.to)),
    queryFn: () => fetchMerchantIncome(range.value.from, range.value.to),
    enabled,
    staleTime: 30_000,
  })
}

/**
 * 매출 합계.
 *
 * 백엔드에 합계 API가 없어 목록을 더한다. `MAX_PAGE_SIZE`가 50이라 하루 결제가 50건을
 * 넘으면 실제보다 작게 나온다. 그 규모가 되면 요약 API를 붙여야 한다.
 */
export function sumIncome(response: MerchantIncomeResponse | undefined): number {
  if (response === undefined) return 0

  return response.transactions.reduce((total, entry) => total + entry.amount, 0)
}
