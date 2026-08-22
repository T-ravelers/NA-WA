/**
 * Member 도메인 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    settings: {
      title: 'Cài đặt',
      account: 'Tài khoản',
      preferences: 'Tùy chọn',
      language: {
        label: 'Ngôn ngữ màn hình',
        change: 'Đổi ngôn ngữ màn hình',
        sheetTitle: 'Ngôn ngữ',
        hint: 'Áp dụng ngay và được lưu vào tài khoản của bạn.',
        saveFailed:
          'Ngôn ngữ đã được đặt trên thiết bị này, nhưng chúng tôi không lưu được vào tài khoản của bạn.',
      },
    },
    errorCode: {
      'MEMBER-001': 'Chúng tôi không tìm thấy tài khoản của bạn. Vui lòng đăng nhập lại.',
      'MEMBER-002': 'Ngôn ngữ này chưa được hỗ trợ.',
      'MEMBER-003': 'Loại tiền tệ này chưa được hỗ trợ.',
      'MEMBER-004': 'Không có gì để thay đổi.',
    },
  },
}
