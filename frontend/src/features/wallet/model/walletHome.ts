import type { ServerDateTime, WalletHome, WalletTransaction } from '../api/walletApi'

/**
 * Wallet 홈 응답을 화면이 쓰는 형태로 옮긴다.
 *
 * 이 계층은 문구를 만들지 않는다. i18n key로 쓸 값과 숫자·시각만 정하고, 번역과 색·아이콘은
 * 화면이 고른다. 표시 문자열이 여기 굳어 있으면 로케일을 바꿔도 화면이 따라오지 못한다.
 *
 * 거래 내역·거래 상세(#99)는 별도 화면이라 문구를 영어로 직접 고정해 둔다. 이 화면들이
 * i18n으로 옮겨가면 `getTransactionTypeLabel`·`getTransactionStatusLabel`도 함께 정리한다.
 */

/** 백엔드 `me.nawa.wallet.domain.enums.TransferType`과 1:1이다. */
export const TRANSFER_TYPES = [
  'TOPUP',
  'QR_PAYMENT',
  'SETTLEMENT',
  'DEPOSIT_HOLD',
  'DEPOSIT_REFUND',
  'DEPOSIT_FORFEIT_DISTRIBUTION',
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

/**
 * 오프셋 없는 서버 시각을 KST로 해석한다.
 *
 * 백엔드는 `LocalDateTime`을 타임존 없이 내려준다. 이대로 브라우저에 맡기면 로컬 타임존으로
 * 읽혀, 방한 외국인 사용자의 기기에서 거래 날짜가 하루 어긋난다. "서버 시각은 KST"라는
 * 전제를 이 한 곳에만 둔다. 백엔드가 오프셋을 포함해 내려주게 되면 이 함수를 지우면 된다.
 *
 * 형식이 두 가지인 이유는 `ServerDateTime` 주석에 있다. 배열이 현재 실제 형식이고,
 * 문자열은 백엔드가 `@JsonFormat`을 도입했을 때를 위한 것이다.
 *
 * 배열을 `new Date(년, 월, 일, ...)`로 넘기지 않는다. 그 생성자는 인자를 **로컬 타임존**의
 * 벽시계로 읽으므로 KST 전제가 깨진다. 시(hour)에서 9를 빼고 `Date.UTC`로 만든다. 자정
 * 부근에서 음수가 되어도 `Date.UTC`가 전날로 정규화한다.
 */
const KST_OFFSET = '+09:00'
const KST_OFFSET_HOURS = 9
const HAS_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/

function parseDateTimeParts(parts: number[]): Date | null {
  // 뒤쪽 0은 생략되므로 길이가 흔들린다. 날짜 세 칸만 있으면 해석할 수 있다.
  if (parts.length < 3 || parts.some((part) => !Number.isFinite(part))) {
    return null
  }

  const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = parts as [
    number,
    number,
    number,
    number?,
    number?,
    number?,
    number?,
  ]

  const parsed = new Date(
    Date.UTC(
      year,
      month - 1,
      day,
      hour - KST_OFFSET_HOURS,
      minute,
      second,
      Math.floor(nanosecond / 1_000_000),
    ),
  )

  return Number.isNaN(parsed.getTime()) ? null : parsed
}

export function parseServerDateTime(value: ServerDateTime): Date | null {
  if (Array.isArray(value)) {
    return parseDateTimeParts(value)
  }

  if (value === null || value === '') {
    return null
  }

  const parsed = new Date(HAS_OFFSET.test(value) ? value : `${value}${KST_OFFSET}`)

  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function toActivityKind(transferType: string): ActivityKind {
  const normalized = transferType.toUpperCase()

  return TRANSFER_TYPES.find((type) => type === normalized) ?? 'UNKNOWN'
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
        settled: kind === 'SETTLEMENT',
      }
    }),
  }
}

const toAmountString = (amount: string | number | null | undefined): string =>
  amount === null || amount === undefined ? '0' : String(amount)

const getAbsoluteAmount = (amount: string): string => amount.replace(/^-/, '')

export const formatPointAmount = (amount: string): string =>
  amount.replace(/\B(?=(\d{3})+(?!\d))/g, ',')

/** 거래 내역·거래 상세 화면에서 쓰는 영문 표시명. 백엔드 `TransferType`과 1:1이다. */
export const getTransactionTypeLabel = (transferType: string): string => {
  switch (transferType.toUpperCase()) {
    case 'TOPUP':
      return 'Point top-up'
    case 'QR_PAYMENT':
      return 'QR payment'
    case 'TRANSFER':
      return 'Point transfer'
    case 'SETTLEMENT':
      return 'Settlement'
    default:
      return 'Wallet transaction'
  }
}

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
  const date = parseServerDateTime(createdAt)
  if (date === null) return 'Unknown date'

  // 표시 타임존을 KST로 고정한다. 서비스는 한국에서만 쓰이므로 거래 시각은 한국 시간
  // 기준이어야 한다 — 기기 타임존에 맡기면 외국인 방문자 기기에서 날짜가 밀린다.
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(date)
}

export const formatTransactionAmount = (transaction: WalletTransactionResponse): string => {
  const amount = getAbsoluteAmount(toAmountString(transaction.amount))
  const sign = transaction.entryType.toUpperCase() === 'DEBIT' ? '-' : '+'

  return `${sign}${formatPointAmount(amount)} P`
}
