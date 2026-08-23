/**
 * 알림 화면 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  notification: {
    title: 'Thông báo',
    bell: 'Thông báo',
    unreadBadge: '{count} chưa đọc',
    unreadBadgeOverflow: '9+',
    unread: 'Chưa đọc',
    dismissOne: 'Xoá: {message}',
    dismissAll: 'Xoá tất cả',
    markAllRead: 'Đánh dấu đã đọc tất cả',
    loadMore: 'Xem thông báo cũ hơn',
    loadingMore: 'Đang tải',
    empty: {
      title: 'Chưa có thông báo nào',
      description: 'Các yêu cầu và thanh toán chia tiền sẽ hiện ở đây.',
    },
    item: {
      SETTLEMENT_REQUESTED: '{actor} đã yêu cầu bạn {amount} cho {gathering}',
      SETTLEMENT_PAID: '{actor} đã trả bạn {amount} cho {gathering}',
      SETTLEMENT_COMPLETED: 'Mọi người đã thanh toán xong cho {gathering}',
      UNKNOWN: 'Có cập nhật mới về {gathering}',
    },
  },
}
