import type { ServerDateTime, WalletHome, WalletTransaction } from '../api/walletApi'
import {
  formatServerDateTime,
  parseServerDateTime as parseSharedServerDateTime,
} from '@/shared/lib/datetime'
import { formatGroupedDecimal } from '@/shared/lib/money'

/**
 * Wallet 홈 응답을 화면이 쓰는 형태로 옮긴다.
 *
 * 이 계층은 문구를 만들지 않는다. i18n key로 쓸 값과 숫자·시각만 정하고, 번역과 색·아이콘은
 * 화면이 고른다. 표시 문자열이 여기 굳어 있으면 로케일을 바꿔도 화면이 따라오지 못한다.
 *
 * 거래 상태 문구는 거래 내역·거래 상세(#99)가 아직 영어로 고정해 두고 있다. 그 화면들이
 * i18n으로 옮겨갈 때 `getTransactionStatusLabel`도 함께 정리한다.
 */

/** 백엔드 `me.nawa.wallet.domain.enums.TransferType`과 1:1이다. */
export const TRANSFER_TYPES = [
  'TOPUP',
  'QR_PAYMENT',
  'SETTLEMENT',
  'DEPOSIT_HOLD',
  'DEPOSIT_REFUND',
  'DEPOSIT_NO_SHOW_DISTRIBUTION',
  'REVERSAL',
] as const

export type TransferType = (typeof TRANSFER_TYPES)[number]

/** 거래 내역 필터에서 쓰는 거래 종류. 백엔드 `TransferType`과 같은 값이다. */
export type TransactionType = TransferType

/** 백엔드 `wallets.wallet_status` ENUM과 1:1이다. */
export const WALLET_STATUSES = ['ACTIVE', 'SUSPENDED', 'CLOSED'] as const

export type WalletStatus = (typeof WALLET_STATUSES)[number]

/** 백엔드 거래 상태. 거래 내역·거래 상세 화면에서만 쓴다. */
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'REVERSED'

/**
 * 서버가 모르는 값을 내려도 화면을 비우지 않는다.
 *
 * 거래 종류는 백엔드가 먼저 늘어날 수 있다. 그때 목록이 통째로 사라지는 대신
 * `UNKNOWN`으로 떨어뜨려 금액과 시각은 그대로 보여준다.
 */
export type ActivityKind = TransferType | 'UNKNOWN'
export type WalletStatusKind = WalletStatus | 'UNKNOWN'

export interface WalletActivity {
  id: string
  kind: ActivityKind
  /** 이 지갑 기준 증감. 부호가 이미 붙어 있어 화면은 포맷만 한다. */
  signedAmount: number
  /** 서버 시각을 해석한 결과. 해석할 수 없으면 `null`이고 화면이 시각을 생략한다. */
  occurredAt: Date | null
  /** 이 지갑에서 돈이 나간 거래인지. 정산의 낸 쪽·받은 쪽을 가르는 데 쓴다. */
  outgoing: boolean
  settled: boolean
}

export interface WalletHomeData {
  balance: number
  status: WalletStatusKind
  activities: WalletActivity[]
}

/** 거래 목록·상세 API가 내려주는 거래 1건. `WalletTransaction`과 필드가 같다. */
export interface WalletTransactionResponse {
  transferId: number
  transferType: string
  entryType: string
  amount: string | number
  balanceAfter: string | number
  createdAt: ServerDateTime
}

export interface TransactionCounterpartyResponse {
  type: string
  name: string
}

export interface TransactionReceiptResponse {
  transactionNumber: string | null
  memo: string | null
  spendingCategory: string | null
}

export interface TransactionFxResponse {
  sourceAmount: string | number | null
  sourceCurrency: string | null
  displayCurrency: string | null
  exchangeRate: string | number | null
  ratedAt: ServerDateTime
}

export type ServerLocalDate = string | [number, number, number] | null

export interface TransactionDetailResponse {
  amount: string | number
  occurredAt: ServerDateTime
  counterparty: TransactionCounterpartyResponse | null
  status: string
  receipt: TransactionReceiptResponse | null
  transactionNumber: string | null
  fx: TransactionFxResponse | null
}

export interface TransactionAppliedFilters {
  type: string | null
  status: string | null
  from: ServerLocalDate
  to: ServerLocalDate
}

export interface TransactionListResponse {
  transactions: WalletTransactionResponse[]
  nextCursor: string | null
  appliedFilters: TransactionAppliedFilters
}

export interface TransactionSearchParams {
  type?: TransactionType
  status?: TransactionStatus
  from?: string
  to?: string
  cursor?: string
  size?: number
}

export const walletKeys = {
  all: ['wallet'] as const,
  home: () => [...walletKeys.all, 'home'] as const,
  transactions: () => [...walletKeys.all, 'transactions'] as const,
  transactionList: (filters: TransactionSearchParams, cursor?: string) =>
    [...walletKeys.transactions(), filters, cursor ?? null] as const,
  transactionDetail: (transactionId: number) =>
    [...walletKeys.transactions(), 'detail', transactionId] as const,
}

// 기존 wallet model을 직접 참조하던 호출부를 위해 공용 파서를 재노출한다.
export const parseServerDateTime = parseSharedServerDateTime

/** 서버가 내려준 거래 종류를 화면이 아는 값으로 좁힌다. 모르는 값은 `UNKNOWN`이다. */
export function toActivityKind(transferType: string): ActivityKind {
  const normalized = transferType.toUpperCase()

  return TRANSFER_TYPES.find((type) => type === normalized) ?? 'UNKNOWN'
}

/**
 * 이 거래를 화면에서 뭐라고 부를지 정한다.
 *
 * 정산만 낸 쪽과 받은 쪽을 갈라 부른다. 남에게 돈을 낸 것과 남에게서 받은 것은 사용자에게
 * 전혀 다른 일인데, 서버는 둘을 같은 거래 종류 하나로 보내기 때문이다. 방향은 돈이 나갔는지
 * 들어왔는지에만 남아 있으므로 그 값으로 고른다.
 *
 * 나머지 종류는 이름 자체에 이미 방향이 들어 있어(예: 보증금 잡힘 / 보증금 돌려받음) 그대로
 * 쓴다.
 */
export function activityLabelKey(kind: ActivityKind, isOutgoing: boolean): string {
  if (kind === 'SETTLEMENT') {
    return isOutgoing
      ? 'wallet.home.settlementDirection.paid'
      : 'wallet.home.settlementDirection.collected'
  }

  return `wallet.home.activity.${kind}`
}

function toWalletStatus(status: string): WalletStatusKind {
  const normalized = status.toUpperCase()

  return WALLET_STATUSES.find((value) => value === normalized) ?? 'UNKNOWN'
}

/**
 * 이 지갑 기준 증감 부호를 붙인다.
 *
 * 원장의 `amount`는 `CHECK (amount > 0)`이라 항상 양수이고, 방향은 `entry_type`에만 있다.
 * `DEBIT`을 차감으로 읽는다 — 사용자 화면 기준의 관례다. 백엔드에 이 방향을 못 박은
 * 코드가 아직 없으므로(원장 기록 경로 미구현), 지갑 도메인 담당과 확인이 필요하다.
 */
function toSignedAmount(transaction: WalletTransaction): number {
  const magnitude = Math.abs(transaction.amount)

  return transaction.entryType.toUpperCase() === 'DEBIT' ? -magnitude : magnitude
}

export function toWalletHomeData(response: WalletHome): WalletHomeData {
  // 지갑 응답 필드가 누락돼도 화면을 중단시키지 않는다. 백엔드 계약은 필수 필드지만,
  // 실제로 누락된 응답이 관측된 적이 있어 방어적으로 다룬다.
  const recentTransactions = Array.isArray(response.recentTransactions)
    ? response.recentTransactions
    : []

  return {
    balance: typeof response.balance === 'number' ? response.balance : 0,
    status:
      typeof response.availabilityStatus === 'string'
        ? toWalletStatus(response.availabilityStatus)
        : 'UNKNOWN',
    activities: recentTransactions.map((transaction) => {
      const kind = toActivityKind(transaction.transferType)

      return {
        id: String(transaction.transferId),
        kind,
        signedAmount: toSignedAmount(transaction),
        occurredAt: parseServerDateTime(transaction.createdAt),
        outgoing: transaction.entryType.toUpperCase() === 'DEBIT',
        settled: kind === 'SETTLEMENT',
      }
    }),
  }
}

const toAmountString = (amount: string | number | null | undefined): string =>
  amount === null || amount === undefined ? '0' : String(amount)

const getAbsoluteAmount = (amount: string): string => amount.replace(/^-/, '')

export const formatPointAmount = (amount: string): string =>
  formatGroupedDecimal(amount, 'en-US') || amount

export const getTransactionStatusLabel = (status: string): string => {
  switch (status.toUpperCase()) {
    case 'PENDING':
      return 'Pending'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    case 'CANCELLED':
      return 'Cancelled'
    case 'REVERSED':
      return 'Reversed'
    default:
      return status
  }
}

export const formatTransactionDateTime = (createdAt: ServerDateTime): string => {
  return (
    formatServerDateTime(createdAt, 'en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }) || 'Unknown date'
  )
}

export const formatTransactionAmount = (transaction: WalletTransactionResponse): string => {
  const amount = getAbsoluteAmount(toAmountString(transaction.amount))
  const sign = transaction.entryType.toUpperCase() === 'DEBIT' ? '-' : '+'

  return `${sign}${formatPointAmount(amount)} P`
}
