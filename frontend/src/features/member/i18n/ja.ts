/**
 * Member 도메인 문구 (ja).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    settings: {
      title: '設定',
      account: 'アカウント',
      preferences: '環境設定',
      language: {
        label: '表示言語',
        change: '表示言語を変更',
        sheetTitle: '言語',
        hint: 'すぐに反映され、アカウントに保存されます。',
        saveFailed: 'この端末では言語を設定しましたが、アカウントへ保存できませんでした。',
      },
    },
    errorCode: {
      'MEMBER-001': 'アカウントが見つかりませんでした。もう一度ログインしてください。',
      'MEMBER-002': 'この言語にはまだ対応していません。',
      'MEMBER-003': 'この通貨にはまだ対応していません。',
      'MEMBER-004': '変更する内容がありませんでした。',
    },
  },
}
