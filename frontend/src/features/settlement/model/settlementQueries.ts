import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'

import { settlementGateway } from '../api/settlementGateway'

export const settlementKeys = {
  all: ['settlements'] as const,
  candidates: () => [...settlementKeys.all, 'candidates'] as const,
  lists: () => [...settlementKeys.all, 'lists'] as const,
  detail: (id: string) => [...settlementKeys.all, 'detail', id] as const,
}

export function useSettlements() {
  return useQuery({
    queryKey: settlementKeys.lists(),
    queryFn: () => settlementGateway.getSettlements(),
  })
}

export function useSettlementCandidates() {
  return useQuery({
    queryKey: settlementKeys.candidates(),
    queryFn: () => settlementGateway.getCandidates(),
  })
}

export function useSettlementDetail(id: () => string) {
  return useQuery({
    queryKey: computed(() => settlementKeys.detail(id())),
    queryFn: () => settlementGateway.getDetail(id()),
  })
}
