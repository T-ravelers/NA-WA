import type { ServerDateTime, WalletHome, WalletTransaction } from '../api/walletApi'

/**
 * Wallet 홈 응답을 화면이 쓰는 형태로 옮긴다.
 *
 * 이 계층은 문구를 만들지 않는다. i18n key로 쓸 값과 숫자·시각만 정하고, 번역과 색·아이콘은
 * 화면이 고른다. 표시 문자열이 여기 굳어 있으면 로케일을 바꿔도 화면이 따라오지 못한다.
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

/** 백엔드 `wallets.wallet_status` ENUM과 1:1이다. */
export const WALLET_STATUSES = ['ACTIVE', 'SUSPENDED', 'CLOSED'] as const

export type WalletStatus = (typeof WALLET_STATUSES)[number]

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

export const walletQueryKeys = {
  all: ['wallet'] as const,
  home: () => [...walletQueryKeys.all, 'home'] as const,
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
  return {
    balance: response.balance,
    status: toWalletStatus(response.availabilityStatus),
    activities: response.recentTransactions.map((transaction) => {
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
