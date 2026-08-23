import type { MaybeRefOrGetter, Ref } from 'vue'
import { inject, type InjectionKey } from 'vue'

/** QR 공동 소비 결제 화면이 필요로 하는 최소한의 약속 정보. */
export interface WalletTodayAppointment {
  appointmentId: number
  appointmentName: string
  activityStartAt: string
  activityEndAt: string
}

export interface WalletTodayAppointmentsQuery {
  data: Ref<WalletTodayAppointment[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
  refetch: () => Promise<unknown>
}

export interface WalletAppointmentIntegration {
  useMyTodayAppointmentsQuery: (enabled: MaybeRefOrGetter<boolean>) => WalletTodayAppointmentsQuery
}

export const walletAppointmentIntegrationKey: InjectionKey<WalletAppointmentIntegration> = Symbol(
  'walletAppointmentIntegration',
)

export function useWalletAppointmentIntegration(): WalletAppointmentIntegration {
  const integration = inject(walletAppointmentIntegrationKey)
  if (!integration) throw new Error('Wallet appointment integration is not configured.')
  return integration
}
