/**
 * Auth 도메인 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  auth: {
    welcome: {
      headline: 'Hành trình của bạn,\nđược ghi lại',
      body: 'Lên kế hoạch hành trình ở Hàn Quốc, chia tiền với nhóm và biết từng won đã đi đâu.',
      // 탑승권 장식 문구. 워드마크와 영문 탑승권 표기라 번역하지 않는다.
      passLabel: 'Boarding · NAWA',
      passTitle: 'Seoul & Beyond',
      passStamp: 'GO',
      start: 'Bắt đầu',
      merchantEntry: 'Bạn có cửa hàng không?',
    },
    signIn: {
      title: 'Chào mừng đến với NA-WA',
      description: 'Đăng nhập để lên kế hoạch hành trình và quyết toán cùng bạn đồng hành.',
      google: 'Tiếp tục với Google',
      line: 'Tiếp tục với LINE',
      lineNotice: 'Đăng nhập bằng LINE đang được xác minh.',
      consent: 'Khi tiếp tục, bạn đồng ý với',
      terms: 'Điều khoản dịch vụ',
      privacy: 'Chính sách quyền riêng tư',
    },
    locale: {
      open: 'Đổi ngôn ngữ màn hình',
      title: 'Ngôn ngữ',
      hint: 'Áp dụng ngay cho màn hình này. Chúng tôi sẽ lưu vào tài khoản sau khi bạn đăng nhập.',
      current: 'Ngôn ngữ màn hình · {language}',
    },
    callback: {
      pending: 'Đang kiểm tra đăng nhập',
      pendingBody:
        'Vui lòng chờ một chút — chúng tôi đang xác nhận tài khoản của bạn. Bạn không cần thao tác gì thêm trên màn hình này.',
      failed: 'Đăng nhập chưa hoàn tất',
      retry: 'Thử đăng nhập lại',
    },
    signOut: 'Đăng xuất',
    signOutBarrier: {
      title: 'Chúng tôi không xác nhận được bạn đã đăng xuất',
      description:
        'Phiên trước đó của bạn đang bị chặn trên thiết bị này. Hãy thử đăng xuất lại hoặc tiếp tục với một nhà cung cấp để đăng nhập lại.',
      retry: 'Thử đăng xuất lại',
    },
    errorCode: {
      'AUTH-001': 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
      'AUTH-002':
        'Phiên đăng nhập của bạn đã bị kết thúc vì lý do bảo mật. Vui lòng đăng nhập lại.',
      'AUTH-003': 'Vui lòng đăng nhập để tiếp tục.',
      'AUTH-004': 'Bạn không có quyền truy cập trang này.',
      'AUTH-005': 'Chúng tôi không xác minh được yêu cầu của bạn. Vui lòng thử lại.',
      'AUTH-006': 'Yêu cầu này đến từ một địa chỉ không được nhận diện.',
      'AUTH-007': 'Phương thức đăng nhập này không được hỗ trợ.',
      'AUTH-008': 'Chúng tôi không đưa bạn về trang trước được. Vui lòng bắt đầu lại.',
      'AUTH-009': 'Phương thức đăng nhập này tạm thời không khả dụng.',
      'AUTH-010': 'Đăng nhập chưa hoàn tất. Vui lòng thử lại.',
      'AUTH-011': 'Nhà cung cấp đăng nhập không phản hồi. Vui lòng thử lại sau ít phút.',
      'AUTH-012': 'Đăng nhập chưa hoàn tất. Vui lòng thử lại.',
      'AUTH-013': 'Chúng tôi không xác minh được tài khoản của bạn. Vui lòng thử lại.',
      'AUTH-014': 'Liên kết đăng nhập này không còn hiệu lực. Vui lòng bắt đầu lại.',
      'AUTH-015': 'Đăng nhập đã bị hủy hoặc từ chối.',
      'AUTH-016': 'Tài khoản này đã bị tạm khóa. Vui lòng liên hệ bộ phận hỗ trợ.',
      'AUTH-017': 'Tài khoản này đã được xóa.',
      'AUTH-018':
        'Chúng tôi không hoàn tất được việc thiết lập tài khoản của bạn. Vui lòng thử lại.',
    },
  },
}
