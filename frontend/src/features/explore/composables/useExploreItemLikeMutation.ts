import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'

import { NormalizedApiError } from '@/shared/api/apiError'
import { showToast } from '@/shared/ui/toast'

import { likeExploreItem, unlikeExploreItem } from '../api/exploreApi'
import { exploreKeys } from '../model/exploreKeys'

interface ExploreItemLikeInput {
  itemId: number
  saved: boolean
}

/**
 * 탐색 항목(EVENT·PLACE 공통) 찜 등록·취소 mutation입니다.
 *
 * 화면은 목록·상세 응답의 `saved`를 그대로 그리므로, 성공 시 서버가 확정한
 * 값을 캐시에 반영해 같은 항목을 보여주는 모든 화면(목록 카드·상세)이 함께
 * 갱신됩니다. 낙관적 갱신은 하지 않아 실패하면 하트가 그대로 남고, 실패
 * 사실은 토스트로 알립니다.
 */
export function useExploreItemLikeMutation() {
  const queryClient = useQueryClient()
  const { t } = useI18n()

  return useMutation({
    mutationFn: ({ itemId, saved }: ExploreItemLikeInput) =>
      saved ? likeExploreItem(itemId) : unlikeExploreItem(itemId),
    onError: (error) => {
      // 비로그인(401)에는 "다시 시도"가 거짓 안내가 된다 — 로그인 안내로 가른다.
      const requiresLogin = error instanceof NormalizedApiError && error.status === 401
      showToast(t(requiresLogin ? 'explore.saveRequiresLogin' : 'explore.saveFailed'))
    },
    onSuccess: (result, { itemId }) => {
      queryClient.setQueriesData({ queryKey: exploreKeys.all }, (data: unknown) =>
        applySavedToCache(data, itemId, result.saved),
      )
      // 찜만 보기 목록은 항목 구성 자체가 바뀌므로 값 패치로는 부족하다.
      void queryClient.invalidateQueries({ predicate: isSavedOnlyListQuery })
    },
  })
}

function applySavedToCache(data: unknown, itemId: number, saved: boolean): unknown {
  if (!isRecord(data)) return data

  if (Array.isArray(data.content)) {
    return {
      ...data,
      content: data.content.map((item) =>
        matchesItem(item, itemId) ? { ...(item as object), saved } : item,
      ),
    }
  }

  return matchesItem(data, itemId) ? { ...data, saved } : data
}

// Event 상세는 itemId 대신 eventId를, Place 상세는 placeId를 함께 쓴다.
// 셋 모두 explore_items의 같은 식별자다.
function matchesItem(value: unknown, itemId: number): boolean {
  if (!isRecord(value)) return false
  return value.itemId === itemId || value.eventId === itemId || value.placeId === itemId
}

function isSavedOnlyListQuery(query: { queryKey: readonly unknown[] }): boolean {
  const [root, , kind, filters] = query.queryKey
  return root === 'explore' && kind === 'list' && isRecord(filters) && filters.savedOnly === true
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
