import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import {
  createReport,
  fetchReport,
  fetchReportExpenseCandidates,
  fetchReportJourneys,
  fetchReports,
} from '../api/reportApi'
import { reportKeys } from '../model/reportKeys'

function requireId(value: number | null, label: string): number {
  if (value === null) {
    throw new Error(`${label} must be valid before the request starts.`)
  }

  return value
}

export function useReportJourneysQuery() {
  return useQuery({
    queryKey: reportKeys.journeys(),
    queryFn: fetchReportJourneys,
    staleTime: 30_000,
  })
}

export function useReportSummariesQuery() {
  return useQuery({
    queryKey: reportKeys.list(),
    queryFn: fetchReports,
    staleTime: 30_000,
  })
}

export function useReportExpenseCandidatesQuery(tripId: MaybeRefOrGetter<number | null>) {
  return useQuery({
    queryKey: computed(() => reportKeys.candidates(toValue(tripId))),
    queryFn: () => fetchReportExpenseCandidates(requireId(toValue(tripId), 'Journey ID')),
    enabled: computed(() => toValue(tripId) !== null),
  })
}

export function useReportDetailQuery(reportId: MaybeRefOrGetter<number | null>) {
  return useQuery({
    queryKey: computed(() => reportKeys.detail(toValue(reportId))),
    queryFn: () => fetchReport(requireId(toValue(reportId), 'Report ID')),
    enabled: computed(() => toValue(reportId) !== null),
  })
}

export function useCreateReportMutation() {
  return useMutation({ mutationFn: createReport })
}
