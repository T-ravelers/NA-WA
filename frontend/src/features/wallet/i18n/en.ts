/**
 * Wallet 도메인 문구.
 *
 * 이 파일은 `en`이 원본이자 폴백이다. 한국어는 서비스 로케일이 아니다.
 *
 * `activity`와 `status`의 key는 백엔드 `TransferType`·`wallet_status` 값과 그대로 맞춘다.
 * 서버가 모르는 값을 내려도 화면이 비지 않도록 `UNKNOWN`을 함께 둔다.
 *
 * `errorCode`는 백엔드 `WalletErrorCode`와 1:1로 맞추되, 이 화면에서 실제로 도달할 수 있는
 * 코드만 둔다. 충전·Webhook 계열(WALLET-002~004, 007, 009~013)은 충전 화면(#99)에서
 * 그 화면의 문구와 함께 추가한다.
 */
export default {
  wallet: {
    home: {
      title: 'Wallet',
      accountName: 'My wallet',
      balanceLabel: 'Available points',
      /** 금액과 단위를 한 문구로 묶는다. 단위 위치는 로케일마다 다르다. */
      points: '{amount} P',
      quickActions: {
        label: 'Wallet actions',
        topUp: 'Top up',
        qr: 'QR',
        settlement: 'Settle up',
        comingSoon: 'These become available in a later release.',
      },
      recentActivity: 'Recent activity',
      viewAll: 'View all',
      activity: {
        TOPUP: 'Point top-up',
        QR_PAYMENT: 'QR payment',
        SETTLEMENT: 'Settlement',
        DEPOSIT_HOLD: 'Deposit held',
        DEPOSIT_REFUND: 'Deposit refunded',
        DEPOSIT_FORFEIT_DISTRIBUTION: 'Forfeited deposit shared out',
        REVERSAL: 'Transaction reversed',
        UNKNOWN: 'Wallet transaction',
      },
      activityStatus: {
        available: 'Available to settle',
        settled: 'Settled',
      },
      status: {
        ACTIVE: 'Active',
        SUSPENDED: 'Suspended',
        CLOSED: 'Closed',
        UNKNOWN: 'Unavailable',
      },
      empty: {
        title: 'No activity yet',
        description: 'Your top-ups and payments will show up here.',
      },
    },
    errorCode: {
      'WALLET-001': 'We could not find your wallet.',
      'WALLET-005': 'We could not find that transaction.',
      'WALLET-006': 'You can only view your own transactions.',
      'WALLET-008': 'Your wallet is not active right now.',
    },
  },
}
