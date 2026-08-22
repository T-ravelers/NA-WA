import type { AppointmentListFilters, MyAppointmentScope } from '../api/appointmentApi'

export const appointmentKeys = {
  all: ['appointments'] as const,
  lists: () => [...appointmentKeys.all, 'list'] as const,
  list: (filters: AppointmentListFilters) => [...appointmentKeys.lists(), filters] as const,
  details: () => [...appointmentKeys.all, 'detail'] as const,
  detail: (appointmentId: number | null) => [...appointmentKeys.details(), appointmentId] as const,
  members: (appointmentId: number | null) =>
    [...appointmentKeys.all, 'members', appointmentId] as const,
  participation: (appointmentId: number | null) =>
    [...appointmentKeys.all, 'participation', appointmentId] as const,
  reviewStatus: (appointmentId: number | null) =>
    [...appointmentKeys.all, 'reviewStatus', appointmentId] as const,
  /** 범위별 key의 접두사. 무효화는 이 key로 걸어 두 범위를 함께 지운다. */
  mine: () => [...appointmentKeys.all, 'mine'] as const,
  myScope: (scope: MyAppointmentScope) => [...appointmentKeys.mine(), scope] as const,
} as const
