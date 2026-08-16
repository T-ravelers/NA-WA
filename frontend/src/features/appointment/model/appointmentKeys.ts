import type { AppointmentListFilters } from '../api/appointmentApi'

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
  mine: () => [...appointmentKeys.all, 'mine'] as const,
} as const
