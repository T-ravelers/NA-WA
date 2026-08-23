/**
 * Member 도메인 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  member: {
    profile: {
      title: 'Hồ sơ',
      account: 'Tài khoản',
      preferences: 'Tùy chọn',
      from: 'Đến từ {country}',
      tabs: {
        saved: 'Đã lưu',
        appointments: 'Cuộc hẹn',
      },
      kinds: {
        events: 'Sự kiện',
        places: 'Địa điểm',
      },
      saved: {
        emptyEvents: 'Chưa lưu gì. Chạm vào trái tim trên một sự kiện để giữ nó ở đây.',
        emptyPlaces: 'Chưa lưu gì. Chạm vào trái tim trên một địa điểm để giữ nó ở đây.',
      },
      appointments: {
        emptyEvents: 'Chưa có cuộc hẹn nào cho sự kiện.',
        emptyPlaces: 'Chưa có cuộc hẹn nào cho địa điểm.',
      },
      currency: {
        label: 'Tiền tệ',
        notSet: 'Chưa đặt',
      },
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
