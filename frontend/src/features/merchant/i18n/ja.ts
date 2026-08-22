/**
 * Merchant 도메인 문구 (ja).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  merchant: {
    title: '店舗',
    register: {
      heading: '店舗を設定する',
      description:
        '店舗名を入力してください。お客様がQRコードを読み取ると、この名前が表示されます。',
      businessName: '店舗名',
      businessNamePlaceholder: '例：ブルーボトル 弘大',
      irreversible:
        'この操作は取り消せません。店舗アカウントでは決済や旅行者向け機能を利用できません。',
      submit: '店舗を作成',
      error: '店舗を設定できませんでした。もう一度お試しください。',
    },
    income: {
      heading: '本日の売上',
      amount: '{amount} P',
      count: '支払いはまだありません | 1件の支払い | {count}件の支払い',
      empty: 'お客様が支払うと、ここに表示されます。',
      error: '売上を読み込めませんでした。',
      retry: '再試行',
    },
    qr: {
      heading: 'お客様に請求する',
      description: 'お客様が購入する品目を追加してください。合計は自動で計算されます。',
      itemName: '品目{index}の名前',
      itemNamePlaceholder: '例：アイスアメリカーノ',
      quantity: '品目{index}の数量',
      unitPrice: '品目{index}の単価',
      unitPricePlaceholder: '0',
      addItem: '品目を追加',
      remove: '削除',
      removeItem: '品目{index}を削除',
      decreaseQuantity: '品目{index}の数量を減らす',
      increaseQuantity: '品目{index}の数量を増やす',
      subtotal: '{amount} P',
      total: '合計',
      totalAmount: '{amount} P',
      totalHint: '数量と単価を入力した品目を1つ以上追加してください。',
      memo: 'メモ（任意）',
      memoPlaceholder: '例：テーブル4',
      create: 'QRコードを表示',
      createAnother: '新しいQRコード',
      error: 'QRコードを作成できませんでした。もう一度お試しください。',
      validity: '残り {time}',
      expired: 'このコードは期限切れです。',
      expiredAction: '新しく作成',
      imageAlt: 'この支払いのQRコード',
    },
    errorCode: {
      'MEMBER-006': 'この店舗名は使用できません。',
      'MEMBER-009': 'このアカウントはすでに店舗として登録されています。',
      'WALLET-001': 'ウォレットが見つかりませんでした。',
      'WALLET-017': 'ウォレットは現在利用できない状態です。',
    },
  },
}
