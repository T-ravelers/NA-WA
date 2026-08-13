import { inject, type InjectionKey, type Ref } from 'vue'

export interface JourneyReportSummary {
  tripId: number
  reportId: number
}

export interface JourneyReportSummariesQuery {
  data: Ref<JourneyReportSummary[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
  refetch: () => Promise<unknown>
}

export interface JourneyReportIntegration {
  useReportSummariesQuery: () => JourneyReportSummariesQuery
}

export const journeyReportIntegrationKey: InjectionKey<JourneyReportIntegration> = Symbol(
  'journeyReportIntegration',
)

export function useJourneyReportIntegration(): JourneyReportIntegration {
  const integration = inject(journeyReportIntegrationKey)
  if (!integration) throw new Error('Journey report integration is not configured.')
  return integration
}
