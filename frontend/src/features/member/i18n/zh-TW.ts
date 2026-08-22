/**
 * Member 도메인 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    profile: {
      title: '個人檔案',
      account: '帳號',
      preferences: '偏好設定',
      from: '來自{country}',
      tabs: {
        saved: '收藏',
        appointments: '約會',
      },
      kinds: {
        events: '活動',
        places: '地點',
      },
      saved: {
        emptyEvents: '還沒有收藏。點活動上的愛心就會留在這裡。',
        emptyPlaces: '還沒有收藏。點地點上的愛心就會留在這裡。',
      },
      appointments: {
        emptyEvents: '還沒有活動的約會。',
        emptyPlaces: '還沒有地點的約會。',
      },
      currency: {
        label: '幣別',
        notSet: '尚未設定',
      },
      language: {
        label: '畫面語言',
        change: '變更畫面語言',
        sheetTitle: '語言',
        hint: '立即套用並儲存到您的帳號。',
        saveFailed: '語言已在這台裝置上設定完成，但無法儲存到您的帳號。',
      },
    },
    form: {
      editTitle: '編輯個人檔案',
      onboardingTitle: '歡迎',
      onboardingLead: '請告訴我們您的名字和來自哪個國家，之後都可以修改。',
      name: '名字',
      namePlaceholder: '其他旅客會看到的名字',
      photo: '照片網址',
      photoOptional: '選填。貼上您照片的連結。',
      photoHint: '照片會顯示在約會畫面中您的名字旁邊。',
      nationality: '國籍',
      nationalityPlaceholder: '請選擇國家',
      save: '儲存',
      start: '開始',
      cancel: '取消',
      error: {
        nameRequired: '請輸入名字。',
        nameTooLong: '請輸入 {max} 個字以內。',
        imageScheme: '網址必須以 http:// 或 https:// 開頭。',
        countryRequired: '請選擇國家。',
        saveFailed: '無法儲存您的個人檔案，請再試一次。',
      },
    },
    errorCode: {
      'MEMBER-001': '找不到您的帳號，請重新登入。',
      'MEMBER-002': '目前尚不支援這個語言。',
      'MEMBER-003': '目前尚不支援這個幣別。',
      'MEMBER-004': '沒有需要變更的內容。',
      'MEMBER-005': '目前尚不支援這個國家。',
      'MEMBER-006': '無法使用這個名字，請改用較短的名字。',
      'MEMBER-007': '照片網址不正確。',
      'MEMBER-008': '請填寫所有欄位才能完成設定。',
    },
  },
}
