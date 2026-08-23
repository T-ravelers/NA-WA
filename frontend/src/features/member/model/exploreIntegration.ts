import { inject, type InjectionKey, type Ref } from 'vue'

/**
 * 프로필의 찜 탭이 그리는 항목.
 *
 * Event와 Place는 필드 이름이 서로 다르지만(제목이 `title`과 `name`) 이 화면은 둘을
 * 같은 줄 모양으로 그린다. explore가 자기 요약 DTO를 이 모양으로 좁혀서 넘긴다 —
 * member가 explore를 직접 import할 수 없기도 하고, 화면이 실제로 쓰는 네 필드만
 * 받아 두면 explore의 응답이 늘어나도 이 화면이 흔들리지 않는다.
 */
export interface SavedExploreItem {
  itemId: number
  title: string
  /** 제목 아래 한 줄. 지역이나 기간처럼 항목을 구별해 주는 값이며 없을 수 있다. */
  subtitle: string | null
  thumbnailUrl: string | null
}

export interface SavedExploreItemsQuery {
  data: Ref<SavedExploreItem[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
  refetch: () => void
}

export interface MemberExploreIntegration {
  useSavedItems: (kind: Ref<'EVENT' | 'PLACE'>, enabled: Ref<boolean>) => SavedExploreItemsQuery
}

export const memberExploreIntegrationKey: InjectionKey<MemberExploreIntegration> = Symbol(
  'memberExploreIntegration',
)

export function useSavedExploreItems(
  kind: Ref<'EVENT' | 'PLACE'>,
  enabled: Ref<boolean>,
): SavedExploreItemsQuery {
  const integration = inject(memberExploreIntegrationKey)
  if (!integration) throw new Error('Member explore integration is not configured.')
  return integration.useSavedItems(kind, enabled)
}
