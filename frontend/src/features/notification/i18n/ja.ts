/**
 * 알림 화면 문구 (ja).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  notification: {
    title: 'お知らせ',
    bell: 'お知らせ',
    unreadBadge: '未読 {count} 件',
    unreadBadgeOverflow: '9+',
    unread: '未読',
    dismissOne: '削除: {message}',
    dismissAll: 'すべて削除',
    markAllRead: 'すべて既読にする',
    loadMore: '以前のお知らせを見る',
    loadingMore: '読み込み中',
    empty: {
      title: 'お知らせはまだありません',
      description: '割り勘のリクエストや支払いがここに表示されます。',
    },
    item: {
      SETTLEMENT_REQUESTED: '{actor}さんが「{gathering}」の {amount} を請求しました',
      SETTLEMENT_PAID: '{actor}さんが「{gathering}」の {amount} を支払いました',
      SETTLEMENT_COMPLETED: '「{gathering}」の支払いが全員完了しました',
      UNKNOWN: '「{gathering}」に更新がありました',
    },
  },
}
