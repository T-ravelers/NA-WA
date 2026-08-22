/**
 * Merchant 도메인 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  merchant: {
    title: '店家',
    register: {
      heading: '建立您的店家',
      description: '請輸入店家名稱。顧客掃描您的 QR Code 時會看到這個名稱。',
      businessName: '店家名稱',
      businessNamePlaceholder: '例如：Blue Bottle 弘大店',
      irreversible: '此操作無法復原。店家帳號無法進行付款或使用旅客功能。',
      submit: '建立店家',
      error: '無法建立店家，請再試一次。',
    },
    income: {
      heading: '今日收入',
      amount: '{amount} P',
      count: '尚無付款 | 1 筆付款 | {count} 筆付款',
      empty: '顧客付款後，紀錄會顯示在這裡。',
      error: '無法載入收入資料。',
      retry: '重試',
    },
    qr: {
      heading: '向顧客收款',
      description: '新增顧客購買的品項，總計會自動計算。',
      itemName: '第 {index} 項品項名稱',
      itemNamePlaceholder: '例如：冰美式咖啡',
      quantity: '第 {index} 項數量',
      unitPrice: '第 {index} 項單價',
      unitPricePlaceholder: '0',
      addItem: '新增品項',
      remove: '移除',
      removeItem: '移除第 {index} 項',
      decreaseQuantity: '減少第 {index} 項數量',
      increaseQuantity: '增加第 {index} 項數量',
      subtotal: '{amount} P',
      total: '總計',
      totalAmount: '{amount} P',
      totalHint: '請至少新增一個有數量和單價的品項。',
      memo: '備註（選填）',
      memoPlaceholder: '例如：4 號桌',
      create: '顯示 QR Code',
      createAnother: '建立新的 QR Code',
      error: '無法建立 QR Code，請再試一次。',
      validity: '{time} 後失效',
      expired: '這個 QR Code 已失效。',
      expiredAction: '建立新的 QR Code',
      imageAlt: '這筆付款的 QR Code',
    },
    errorCode: {
      'MEMBER-006': '店家名稱無效。',
      'MEMBER-009': '這個帳號已註冊為店家。',
      'WALLET-001': '找不到您的錢包。',
      'WALLET-017': '您的錢包目前未啟用。',
    },
  },
}
