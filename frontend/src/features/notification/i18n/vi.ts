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
    push: {
      title: 'Thông báo trên thiết bị này',
      off: {
        description:
          'Nhận thông báo về các yêu cầu và thanh toán chia tiền, kể cả khi đã đóng ứng dụng.',
        action: 'Bật',
      },
      on: {
        description: 'Thiết bị này sẽ nhận thông báo khi có yêu cầu hoặc thanh toán chia tiền mới.',
        action: 'Tắt',
      },
      blocked: {
        description:
          'Thông báo của NA-WA đang bị chặn. Hãy cho phép thông báo trong cài đặt trình duyệt hoặc thiết bị để bật.',
      },
      install: {
        description:
          'Trên iPhone và iPad, hãy thêm NA-WA vào Màn hình chính trước (Chia sẻ, rồi Thêm vào Màn hình chính), sau đó mở ứng dụng từ đó để bật thông báo.',
      },
    },
    item: {
      SETTLEMENT_REQUESTED: '{actor} đã yêu cầu bạn {amount} cho {gathering}',
      SETTLEMENT_PAID: '{actor} đã trả bạn {amount} cho {gathering}',
      SETTLEMENT_COMPLETED: 'Mọi người đã thanh toán xong cho {gathering}',
      UNKNOWN: 'Có cập nhật mới về {gathering}',
    },
  },
}
