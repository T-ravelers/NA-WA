/**
 * Member 도메인 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    settings: {
      title: '設定',
      account: '帳號',
      preferences: '偏好設定',
      language: {
        label: '畫面語言',
        change: '變更畫面語言',
        sheetTitle: '語言',
        hint: '立即套用並儲存到您的帳號。',
        saveFailed: '語言已在這台裝置上設定完成，但無法儲存到您的帳號。',
      },
    },
    errorCode: {
      'MEMBER-001': '找不到您的帳號，請重新登入。',
      'MEMBER-002': '目前尚不支援這個語言。',
      'MEMBER-003': '目前尚不支援這個幣別。',
      'MEMBER-004': '沒有需要變更的內容。',
    },
  },
}
