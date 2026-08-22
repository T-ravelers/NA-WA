/**
 * Auth 도메인 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  auth: {
    welcome: {
      headline: '您的旅程，\n留下紀錄',
      body: '規劃韓國旅程、與旅伴分帳，清楚掌握每一筆花費。',
      passLabel: '登機 · NAWA',
      passTitle: '首爾與遠方',
      passStamp: '出發',
      start: '開始使用',
      merchantEntry: '您是店家嗎？',
    },
    signIn: {
      title: '歡迎使用 NA-WA',
      description: '登入後即可規劃旅程，並與旅伴分帳。',
      google: '使用 Google 繼續',
      line: '使用 LINE 繼續',
      lineNotice: 'LINE 登入功能仍在審核中。',
      consent: '繼續即表示您同意',
      terms: '服務條款',
      privacy: '隱私權政策',
    },
    locale: {
      open: '變更畫面語言',
      title: '語言',
      hint: '立即套用到這個畫面。登入後會儲存到您的帳號。',
      current: '畫面語言 · {language}',
    },
    callback: {
      pending: '正在確認登入',
      pendingBody: '請稍候，我們正在確認您的帳號。這個畫面不需要任何操作。',
      failed: '登入未完成',
      retry: '重新登入',
    },
    signOut: '登出',
    signOutBarrier: {
      title: '無法確認您已登出',
      description: '這台裝置上先前的登入狀態已被封鎖。請再次嘗試登出，或選擇登入方式重新登入。',
      retry: '重新嘗試登出',
    },
    errorCode: {
      'AUTH-001': '您的登入已過期，請重新登入。',
      'AUTH-002': '基於安全考量，您的登入已被中止，請重新登入。',
      'AUTH-003': '請先登入再繼續。',
      'AUTH-004': '您沒有權限存取這個頁面。',
      'AUTH-005': '無法驗證您的要求，請再試一次。',
      'AUTH-006': '這個要求來自無法辨識的位址。',
      'AUTH-007': '不支援這種登入方式。',
      'AUTH-008': '無法返回上一頁，請重新開始。',
      'AUTH-009': '這種登入方式暫時無法使用。',
      'AUTH-010': '登入未完成，請再試一次。',
      'AUTH-011': '登入服務提供者沒有回應，請稍後再試。',
      'AUTH-012': '登入未完成，請再試一次。',
      'AUTH-013': '無法驗證您的帳號，請再試一次。',
      'AUTH-014': '這個登入連結已失效，請重新開始。',
      'AUTH-015': '登入已取消或遭到拒絕。',
      'AUTH-016': '這個帳號已被停權，請聯絡客服。',
      'AUTH-017': '這個帳號已經註銷。',
      'AUTH-018': '無法完成帳號設定，請再試一次。',
    },
  },
}
