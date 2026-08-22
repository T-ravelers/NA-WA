/**
 * 공통 문구 (ja).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  app: {
    name: 'NA-WA',
    tagline: '計画も、旅も、精算も、いっしょに',
  },
  action: {
    retry: '再試行',
    back: '戻る',
    goHome: 'ホームへ',
    close: '閉じる',
  },
  state: {
    loading: '読み込み中',
    empty: {
      title: 'まだ何もありません',
      description: 'この画面に表示できる内容は今のところありません。',
    },
    error: {
      title: '問題が発生しました',
      description: 'この画面を読み込めませんでした。もう一度お試しください。',
    },
  },
  error: {
    network: 'オフラインのようです。接続を確認して、もう一度お試しください。',
    timeout: 'リクエストに時間がかかりすぎました。もう一度お試しください。',
    unknown: '問題が発生しました。もう一度お試しください。',
  },
  nav: {
    label: 'メインナビゲーション',
    home: 'ホーム',
    report: 'レポート',
    profile: 'プロフィール',
    wallet: 'ウォレット',
    journey: '旅程',
    comingSoon: '近日公開',
  },
  calendar: {
    previousMonth: '前の月',
    nextMonth: '次の月',
    selectDate: '{date}を選択',
    weekdays: {
      sun: '日',
      mon: '月',
      tue: '火',
      wed: '水',
      thu: '木',
      fri: '金',
      sat: '土',
    },
  },
  spendingCategory: {
    FOOD: 'グルメ',
    SHOPPING: 'ショッピング',
    BEAUTY: 'ビューティー',
    // SHOW는 공연뿐 아니라 전시·팬미팅·애니 등 문화 전반이라 '公演'보다 넓은 'エンタメ'로 둔다.
    SHOW: 'エンタメ',
    TRANSPORT: '交通',
    STAY: '宿泊',
    OTHER: 'その他',
  },
  notFound: {
    title: 'ページが見つかりません',
    description: 'お探しのページは存在しないか、移動しました。',
  },
}
