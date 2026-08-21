import { inject, type InjectionKey } from 'vue'

/**
 * 여정을 만든 뒤 돌아갈 화면. route name과 params만 나른다.
 *
 * Discover 상세는 `/explore/events/:eventId`처럼 **route param**을 쓴다. 기존
 * `returnRouteName` query는 query만 되돌려 주므로 그 화면으로는 돌아갈 수 없다.
 */
export interface JourneyExploreReturnLocation {
  name: string
  params: Record<string, string>
}

export interface JourneyExploreIntegration {
  /**
   * 돌아갈 위치를 꺼내면서 **일회성 맥락을 비운다.** 두 번째 호출은 `null`이다.
   *
   * 남겨두면 다음에 Discover로 그냥 들어왔을 때 지난 흐름의 날짜가 다시 프리필된다.
   */
  consumeReturn: () => JourneyExploreReturnLocation | null
}

export const journeyExploreIntegrationKey: InjectionKey<JourneyExploreIntegration> = Symbol(
  'journeyExploreIntegration',
)

export function useJourneyExploreIntegration(): JourneyExploreIntegration {
  const integration = inject(journeyExploreIntegrationKey)
  if (!integration) throw new Error('Journey explore integration is not configured.')
  return integration
}
