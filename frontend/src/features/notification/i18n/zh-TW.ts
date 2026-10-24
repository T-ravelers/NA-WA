/**
 * 알림 화면 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  notification: {
    title: '通知',
    bell: '通知',
    unreadBadge: '{count} 則未讀',
    unreadBadgeOverflow: '9+',
    unread: '未讀',
    dismissOne: '刪除：{message}',
    dismissAll: '全部清除',
    markAllRead: '全部標為已讀',
    loadMore: '查看較舊的通知',
    loadingMore: '載入中',
    empty: {
      title: '目前沒有通知',
      description: '分帳的請款與付款會顯示在這裡。',
    },
    push: {
      title: '此裝置的通知',
      off: {
        description: '即使關閉 App，也會通知你分帳的請款與付款。',
        action: '開啟',
      },
      on: {
        description: '此裝置會收到新的分帳請款與付款通知。',
        action: '關閉',
      },
      blocked: {
        description: '已封鎖 NA-WA 的通知。請在瀏覽器或裝置設定中允許通知，才能開啟。',
      },
      install: {
        description:
          '在 iPhone 和 iPad 上，請先將 NA-WA 加入主畫面（分享 → 加入主畫面），再從主畫面開啟並開啟通知。',
      },
      failed: '發生問題，請再試一次。',
    },
    item: {
      SETTLEMENT_REQUESTED: '{actor} 向你請款「{gathering}」的 {amount}',
      SETTLEMENT_PAID: '{actor} 已支付你「{gathering}」的 {amount}',
      SETTLEMENT_COMPLETED: '「{gathering}」的款項大家都付清了',
      UNKNOWN: '「{gathering}」有新的動態',
    },
  },
}
