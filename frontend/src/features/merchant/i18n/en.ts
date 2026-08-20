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
      /** 되돌리는 API가 없다. 손님이 호기심에 눌러 계정을 잠그지 않도록 미리 알린다. */
      irreversible:
        'This cannot be undone. A store account cannot make payments or use traveller features.',
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
      description: 'Add what the customer is buying. The total is calculated for you.',
      /**
       * 품목·수량·단가는 서버에 저장되지 않는다. 합계를 손으로 더하지 않게 돕는 입력
       * 보조이며, QR에는 합계 금액만 실린다.
       */
      itemName: 'Item',
      itemNamePlaceholder: 'e.g. Iced americano',
      quantity: 'Qty',
      unitPrice: 'Price',
      unitPricePlaceholder: '0',
      addItem: 'Add item',
      remove: 'Remove',
      /** `{index}`는 1부터 센 줄 번호다. 화면에는 안 보이고 스크린 리더만 읽는다. */
      removeItem: 'Remove item {index}',
      decreaseQuantity: 'Decrease quantity of item {index}',
      increaseQuantity: 'Increase quantity of item {index}',
      subtotal: '{amount} P',
      total: 'Total',
      totalAmount: '{amount} P',
      totalHint: 'Add at least one item with a quantity and a price.',
      memo: 'Note (optional)',
      memoPlaceholder: 'e.g. Table 4',
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
