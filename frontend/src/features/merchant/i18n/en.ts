/**
 * Merchant 도메인 문구.
 *
 * 이 파일은 `en`이 원본이자 폴백이다. 한국어는 서비스 로케일이 아니다.
 *
 * `errorCode`는 이 화면에서 실제로 도달할 수 있는 코드만 둔다. 등록은 `MEMBER-006`·
 * `MEMBER-009`, QR 생성은 지갑 상태 계열이다. 없는 코드는 `error.unknown`으로 폴백한다.
 */
export default {
  merchant: {
    title: 'Store',
    register: {
      heading: 'Set up your store',
      description: 'Enter your store name. Customers see this name when they scan your QR code.',
      businessName: 'Store name',
      businessNamePlaceholder: 'e.g. Blue Bottle Hongdae',
      submit: 'Create store',
      error: 'We could not set up your store. Please try again.',
    },
    income: {
      heading: "Today's income",
      amount: '{amount} P',
      count: 'No payments yet | 1 payment | {count} payments',
      empty: 'Payments appear here as customers pay.',
      error: 'We could not load your income.',
      retry: 'Try again',
    },
    qr: {
      heading: 'Charge a customer',
      description: 'Enter the amount, then show the code to your customer.',
      amount: 'Amount',
      amountPlaceholder: '0',
      memo: 'Note (optional)',
      memoPlaceholder: 'e.g. Iced americano',
      create: 'Show QR code',
      createAnother: 'New QR code',
      error: 'We could not create the QR code. Please try again.',
      /** `{time}` is mm:ss remaining before the code expires. */
      validity: 'Expires in {time}',
      expired: 'This code expired.',
      expiredAction: 'Create a new one',
      imageAlt: 'QR code for this payment',
    },
    errorCode: {
      'MEMBER-006': 'That store name is not valid.',
      'MEMBER-009': 'This account is already registered as a store.',
      'WALLET-001': 'We could not find your wallet.',
      'WALLET-017': 'Your wallet is not active right now.',
    },
  },
} as const
