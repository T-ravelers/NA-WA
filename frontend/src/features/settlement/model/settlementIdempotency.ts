import type { CreateSettlementRequest } from './settlement'

type StoredAttempt = { fingerprint: string; key: string }

function resolveStoredKey(
  storageKey: string,
  fingerprint: string,
  createKey: () => string,
): string {
  try {
    const stored = sessionStorage.getItem(storageKey)
    if (stored !== null) {
      const parsed = JSON.parse(stored) as Partial<StoredAttempt>
      if (parsed.fingerprint === fingerprint && typeof parsed.key === 'string') return parsed.key
    }
    const key = createKey()
    sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, key }))
    return key
  } catch {
    return createKey()
  }
}

function clearStoredKey(storageKey: string): void {
  try {
    sessionStorage.removeItem(storageKey)
  } catch {
    // Storage access is optional; a per-attempt in-memory key still protects the request.
  }
}

function createFingerprint(appointmentId: string, request: CreateSettlementRequest): string {
  return JSON.stringify({
    appointmentId,
    sourceTransferId: request.sourceTransferId,
    type: request.type,
    participantAppointmentMemberIds: [...request.participantAppointmentMemberIds].sort(),
    items:
      request.items?.map((item) => ({
        name: item.name,
        unitPrice: item.unitPrice,
        quantity: item.quantity,
        allocations: [...item.allocations].sort((a, b) =>
          a.appointmentMemberId.localeCompare(b.appointmentMemberId),
        ),
      })) ?? null,
  })
}

export function resolveSettlementCreateIdempotencyKey(
  appointmentId: string,
  request: CreateSettlementRequest,
  createKey: () => string = () => globalThis.crypto.randomUUID(),
): string {
  return resolveStoredKey(
    `nawa:settlement:create:${request.sourceTransferId}`,
    createFingerprint(appointmentId, request),
    createKey,
  )
}

export function clearSettlementCreateIdempotencyKey(sourceTransferId: string): void {
  clearStoredKey(`nawa:settlement:create:${sourceTransferId}`)
}

export function resolveSettlementPaymentIdempotencyKey(
  settlementId: string,
  createKey: () => string = () => globalThis.crypto.randomUUID(),
): string {
  return resolveStoredKey(`nawa:settlement:pay:${settlementId}`, settlementId, createKey)
}

export function clearSettlementPaymentIdempotencyKey(settlementId: string): void {
  clearStoredKey(`nawa:settlement:pay:${settlementId}`)
}
