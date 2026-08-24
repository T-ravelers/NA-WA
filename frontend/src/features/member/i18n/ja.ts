/**
 * Member 도메인 문구 (ja).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    profile: {
      title: 'プロフィール',
      account: 'アカウント',
      preferences: '環境設定',
      from: '{country}出身',
      tabs: {
        saved: 'お気に入り',
        appointments: '約束',
      },
      kinds: {
        label: '種類',
        events: 'イベント',
        places: 'スポット',
      },
      saved: {
        emptyEvents: 'まだお気に入りがありません。イベントのハートを押すとここに残ります。',
        emptyPlaces: 'まだお気に入りがありません。スポットのハートを押すとここに残ります。',
      },
      appointments: {
        emptyEvents: 'イベントの約束はまだありません。',
        emptyPlaces: 'スポットの約束はまだありません。',
      },
      currency: {
        label: '通貨',
        notSet: '未設定',
      },
      language: {
        label: '表示言語',
        change: '表示言語を変更',
        sheetTitle: '言語',
        hint: 'すぐに反映され、アカウントに保存されます。',
        saveFailed: 'この端末では言語を設定しましたが、アカウントへ保存できませんでした。',
      },
    },
    form: {
      editTitle: 'プロフィールを編集',
      onboardingTitle: 'ようこそ',
      onboardingLead: 'お名前と出身国を教えてください。あとから変更できます。',
      name: '名前',
      namePlaceholder: '他の旅行者に表示される名前',
      photoHint: '写真はログインに使ったアカウントから取得します。',
      nationality: '国籍',
      nationalityPlaceholder: '国を選んでください',
      save: '保存',
      start: 'はじめる',
      cancel: 'キャンセル',
      error: {
        nameRequired: '名前を入力してください。',
        nameTooLong: '{max}文字以内で入力してください。',
        countryRequired: '国を選んでください。',
        saveFailed: 'プロフィールを保存できませんでした。もう一度お試しください。',
      },
    },
    errorCode: {
      'MEMBER-001': 'アカウントが見つかりませんでした。もう一度ログインしてください。',
      'MEMBER-002': 'この言語にはまだ対応していません。',
      'MEMBER-003': 'この通貨にはまだ対応していません。',
      'MEMBER-004': '変更する内容がありませんでした。',
      'MEMBER-005': 'この国にはまだ対応していません。',
      'MEMBER-006': 'この名前は使用できません。短い名前をお試しください。',
      'MEMBER-007': '写真のURLが正しくありません。',
      'MEMBER-008': 'すべての項目を入力すると設定が完了します。',
    },
  },
}
