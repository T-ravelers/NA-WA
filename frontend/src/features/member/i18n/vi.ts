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
    form: {
      editTitle: 'Chỉnh sửa hồ sơ',
      onboardingTitle: 'Chào mừng',
      onboardingLead: 'Hãy cho chúng tôi biết tên và quốc gia của bạn. Bạn có thể đổi sau.',
      name: 'Tên',
      namePlaceholder: 'Tên mà những người khác sẽ thấy',
      photo: 'Địa chỉ ảnh',
      photoOptional: 'Không bắt buộc. Dán liên kết tới ảnh của bạn.',
      photoHint: 'Ảnh của bạn hiện cạnh tên bạn trong các cuộc hẹn.',
      nationality: 'Quốc tịch',
      nationalityPlaceholder: 'Chọn một quốc gia',
      save: 'Lưu',
      start: 'Bắt đầu',
      cancel: 'Hủy',
      error: {
        nameRequired: 'Hãy nhập tên.',
        nameTooLong: 'Hãy dùng tối đa {max} ký tự.',
        imageScheme: 'Địa chỉ phải bắt đầu bằng http:// hoặc https://.',
        countryRequired: 'Hãy chọn một quốc gia.',
        saveFailed: 'Chúng tôi không lưu được hồ sơ của bạn. Vui lòng thử lại.',
      },
    },
    errorCode: {
      'MEMBER-001': 'Chúng tôi không tìm thấy tài khoản của bạn. Vui lòng đăng nhập lại.',
      'MEMBER-002': 'Ngôn ngữ này chưa được hỗ trợ.',
      'MEMBER-003': 'Loại tiền tệ này chưa được hỗ trợ.',
      'MEMBER-004': 'Không có gì để thay đổi.',
      'MEMBER-005': 'Chúng tôi chưa hỗ trợ quốc gia đó.',
      'MEMBER-006': 'Không dùng được tên đó. Hãy thử tên ngắn hơn.',
      'MEMBER-007': 'Địa chỉ ảnh đó không hợp lệ.',
      'MEMBER-008': 'Hãy điền đủ các mục để hoàn tất thiết lập.',
    },
  },
}
