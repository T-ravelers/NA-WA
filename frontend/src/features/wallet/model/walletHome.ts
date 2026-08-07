export type WalletActivityStatus = 'available' | 'settled'

export interface WalletTransactionResponse {
  transferId: number
  transferType: string
  entryType: string
  amount: string | number
  balanceAfter: string | number
  createdAt: string | null
}

export interface WalletHomeResponse {
  balance: string | number
  availabilityStatus: string
  recentTransactions: WalletTransactionResponse[]
}

export interface WalletActivity {
  id: string
  initial: string
  color: string
  title: string
  meta: string
  amount: string
  status: WalletActivityStatus
}

export interface WalletHomeData {
  accountName: string
  balance: string
  status: string
  activities: WalletActivity[]
}

export const walletKeys = {
  all: ['wallet'] as const,
  home: () => [...walletKeys.all, 'home'] as const,
}

const ACTIVITY_COLORS = ['#ff6b1a', '#f5362b', '#7c6af2', '#32a852', '#e5a629']
const DEFAULT_ACTIVITY_COLOR = '#ff6b1a'
const DEFAULT_ACCOUNT_NAME = 'NAWA 테스트 지갑'

const toAmountString = (amount: string | number): string => String(amount)

const getAbsoluteAmount = (amount: string): string => amount.replace(/^-/, '')

const getActivityTitle = (transferType: string): string => {
  switch (transferType.toUpperCase()) {
    case 'TOPUP':
      return '포인트 충전'
    case 'QR_PAYMENT':
      return 'QR 결제'
    case 'TRANSFER':
      return '포인트 송금'
    case 'SETTLEMENT':
      return '정산'
    default:
      return '지갑 거래'
  }
}

const getActivityInitial = (title: string): string => title.slice(0, 1)

const getActivityMeta = (createdAt: string | null): string => {
  if (!createdAt) return '최근 거래'

  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '최근 거래'

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
  }).format(date)
}

const getActivityAmount = (transaction: WalletTransactionResponse): string => {
  const amount = toAmountString(transaction.amount)
  const isDebit = transaction.entryType.toUpperCase() === 'DEBIT'

  return isDebit ? `-${getAbsoluteAmount(amount)}` : getAbsoluteAmount(amount)
}

export const toWalletHomeData = (response: WalletHomeResponse): WalletHomeData => ({
  accountName: DEFAULT_ACCOUNT_NAME,
  balance: toAmountString(response.balance),
  status: response.availabilityStatus,
  activities: response.recentTransactions.map((transaction, index) => {
    const title = getActivityTitle(transaction.transferType)

    return {
      id: String(transaction.transferId),
      initial: getActivityInitial(title),
      color: ACTIVITY_COLORS[index % ACTIVITY_COLORS.length] ?? DEFAULT_ACTIVITY_COLOR,
      title,
      meta: getActivityMeta(transaction.createdAt),
      amount: getActivityAmount(transaction),
      status: transaction.transferType.toUpperCase() === 'SETTLEMENT' ? 'settled' : 'available',
    }
  }),
})

export const formatPointAmount = (amount: string): string =>
  amount.replace(/\B(?=(\d{3})+(?!\d))/g, ',')

export const getWalletStatusLabel = (status: string): string => {
  switch (status.toUpperCase()) {
    case 'ACTIVE':
      return '사용중'
    case 'SUSPENDED':
      return '일시정지'
    case 'CLOSED':
      return '종료'
    default:
      return status
  }
}
