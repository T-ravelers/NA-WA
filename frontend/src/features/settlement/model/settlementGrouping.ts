import type { SettlementCandidate } from './settlement'

/** 여정에 묶이지 않은 결제를 모으는 버킷. */
export const UNASSIGNED_JOURNEY_KEY = '__unassigned__'

/**
 * 여정 그룹을 식별하는 키.
 *
 * TODO: 후보 응답에 `journeyId`가 추가되면 그 값을 그대로 돌려준다. 지금은 이름밖에
 * 내려오지 않아 동명 여정이 한 그룹으로 합쳐진다. 키 계산을 이 함수 하나에 가둬 두었으니
 * 필드가 생기면 여기만 고치면 된다.
 */
export function resolveJourneyKey(candidate: SettlementCandidate): string {
  const name = candidate.journeyName.trim()
  return name === '' ? UNASSIGNED_JOURNEY_KEY : name
}

export interface AppointmentGroup {
  appointmentId: string
  gatheringName: string
  candidates: SettlementCandidate[]
}

export interface JourneyGroup {
  key: string
  journeyName: string
  appointments: AppointmentGroup[]
  paymentCount: number
}

/**
 * 후보 결제를 여정 → 약속 → 거래 세 단계로 묶는다.
 *
 * 서버가 내려준 순서를 그대로 유지한다. 후보 목록은 내가 결제자이고, 완료됐고, 아직
 * 정산되지 않은 거래만 담고 있어서 이미 정산한 약속과 여정은 애초에 나타나지 않는다.
 */
export function groupCandidates(candidates: SettlementCandidate[]): JourneyGroup[] {
  const journeys = new Map<string, JourneyGroup>()

  for (const candidate of candidates) {
    const key = resolveJourneyKey(candidate)
    let journey = journeys.get(key)
    if (journey === undefined) {
      journey = {
        key,
        journeyName: key === UNASSIGNED_JOURNEY_KEY ? '' : candidate.journeyName,
        appointments: [],
        paymentCount: 0,
      }
      journeys.set(key, journey)
    }

    let appointment = journey.appointments.find(
      (entry) => entry.appointmentId === candidate.appointmentId,
    )
    if (appointment === undefined) {
      appointment = {
        appointmentId: candidate.appointmentId,
        gatheringName: candidate.gatheringName,
        candidates: [],
      }
      journey.appointments.push(appointment)
    }

    appointment.candidates.push(candidate)
    journey.paymentCount += 1
  }

  return [...journeys.values()]
}
