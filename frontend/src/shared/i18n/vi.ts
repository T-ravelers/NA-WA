/**
 * 공통 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  app: {
    name: 'NA-WA',
    tagline: 'Cùng lên kế hoạch, đi du lịch và chia tiền',
  },
  action: {
    retry: 'Thử lại',
    back: 'Quay lại',
    goHome: 'Về trang chủ',
    close: 'Đóng',
  },
  state: {
    loading: 'Đang tải',
    empty: {
      title: 'Chưa có gì ở đây',
      description: 'Hiện chưa có nội dung nào để hiển thị trên màn hình này.',
    },
    error: {
      title: 'Đã xảy ra lỗi',
      description: 'Chúng tôi không tải được màn hình này. Vui lòng thử lại.',
    },
  },
  error: {
    network: 'Có vẻ bạn đang ngoại tuyến. Hãy kiểm tra kết nối và thử lại.',
    timeout: 'Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.',
    unknown: 'Đã xảy ra lỗi. Vui lòng thử lại.',
  },
  nav: {
    label: 'Điều hướng chính',
    home: 'Trang chủ',
    report: 'Báo cáo',
    profile: 'Hồ sơ',
    wallet: 'Ví',
    journey: 'Hành trình',
    comingSoon: 'Sắp ra mắt',
  },
  calendar: {
    previousMonth: 'Tháng trước',
    nextMonth: 'Tháng sau',
    selectDate: 'Chọn {date}',
    // 베트남어 요일 약어는 2자다(CN=Chủ nhật, T2~T7=Thứ 2~7). 7칸이 390px에 들어간다.
    weekdays: {
      sun: 'CN',
      mon: 'T2',
      tue: 'T3',
      wed: 'T4',
      thu: 'T5',
      fri: 'T6',
      sat: 'T7',
    },
  },
  spendingCategory: {
    FOOD: 'Ẩm thực',
    SHOPPING: 'Mua sắm',
    BEAUTY: 'Làm đẹp',
    SHOW: 'Biểu diễn',
    TRANSPORT: 'Di chuyển',
    STAY: 'Lưu trú',
    OTHER: 'Khác',
  },
  notFound: {
    title: 'Không tìm thấy trang',
    description: 'Trang bạn tìm không tồn tại hoặc đã được chuyển đi.',
  },
}
