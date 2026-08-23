export const reportKeys = {
  all: ['reports'] as const,
  journeys: () => [...reportKeys.all, 'journeys'] as const,
  list: () => [...reportKeys.all, 'list'] as const,
  candidates: (tripId: number | null) => [...reportKeys.all, 'candidates', tripId] as const,
  details: () => [...reportKeys.all, 'detail'] as const,
  detail: (reportId: number | null) => [...reportKeys.details(), reportId] as const,
  comparison: (reportId: number | null, scope: string) =>
    [...reportKeys.details(), reportId, 'comparison', scope] as const,
} as const
