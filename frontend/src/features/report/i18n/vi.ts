/**
 * Report 도메인 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  report: {
    list: {
      title: 'Báo cáo',
      description: 'Chọn một hành trình đã kết thúc để tạo hoặc xem báo cáo tổng kết.',
      loadFailed: 'Chúng tôi không tải được báo cáo của bạn. Vui lòng thử lại.',
      emptyTitle: 'Chưa có hành trình nào kết thúc',
      emptyDescription: 'Báo cáo tổng kết sẽ khả dụng sau khi hành trình kết thúc.',
      ended: 'Đã kết thúc',
      eventCount: '{count} sự kiện',
      placeCount: '{count} địa điểm',
      ready: 'Đã có báo cáo tổng kết',
      notCreated: 'Chưa có báo cáo tổng kết',
      view: 'Xem báo cáo tổng kết',
      chooseExpenses: 'Chọn chi tiêu',
      choosingExpenses: 'Đang chọn chi tiêu',
    },
    generate: {
      title: 'Chọn chi tiêu cho báo cáo',
      description:
        'Chọn các khoản chi KRW đã hoàn tất để đưa vào bản báo cáo không thể thay đổi này.',
      loading: 'Đang tải các khoản chi hợp lệ',
      loadFailed: 'Chúng tôi không tải được các khoản chi hợp lệ. Vui lòng thử lại.',
      emptyTitle: 'Không có khoản chi hợp lệ',
      emptyDescription: 'Bạn vẫn có thể tạo báo cáo không chi tiêu cho hành trình này.',
      selectionLabel: 'Khoản chi hợp lệ',
      memoUnavailable: 'Không có ghi chú',
      submit: 'Tạo báo cáo tổng kết',
      submitEmpty: 'Tạo báo cáo không chi tiêu',
      pending: 'Đang tạo báo cáo tổng kết',
      failed: 'Chúng tôi không tạo được báo cáo này. Hãy kiểm tra lại lựa chọn và thử lại.',
      conflictTitle: 'Báo cáo tổng kết đã tồn tại',
      conflictDescription:
        'Làm mới danh sách báo cáo để mở bản hiện có. Báo cáo không thể tạo lại.',
      refresh: 'Làm mới báo cáo',
    },
    detail: {
      title: 'Báo cáo',
      back: 'Về danh sách báo cáo',
      invalidTitle: 'Liên kết báo cáo không hợp lệ',
      invalidDescription: 'Số báo cáo trong liên kết này không hợp lệ.',
      forbiddenTitle: 'Báo cáo này ở chế độ riêng tư',
      forbiddenDescription: 'Bạn không có quyền xem báo cáo này.',
      notFoundTitle: 'Không tìm thấy báo cáo',
      notFoundDescription: 'Báo cáo này không tồn tại hoặc không còn khả dụng.',
      loadFailed: 'Chúng tôi không tải được báo cáo này. Vui lòng thử lại.',
      journeySnapshot: 'Tóm tắt hành trình',
      itinerary: 'Lịch trình đã lưu',
      itineraryEmpty: 'Không có mục lịch trình nào được lưu trong báo cáo này.',
      analysis: 'Phân tích',
      totalSpent: 'Tổng chi tiêu',
      dailyAverage: 'Trung bình mỗi ngày',
      zeroTitle: 'Không có chi tiêu được chọn',
      zeroDescription: 'Báo cáo này được tạo mà không liên kết khoản chi nào.',
      legacyTitle: 'Không có phân tích chi tiêu',
      legacyDescription:
        'Báo cáo này được tạo trước khi có tính năng phân tích chi tiêu. Bạn vẫn xem được tóm tắt hành trình.',
      persona: {
        heading: 'Phong cách chi tiêu',
        FOOD: {
          title: 'Tín đồ ẩm thực',
          description: 'Bạn đi theo vị giác — {share} của hành trình này dành cho ẩm thực.',
        },
        SHOPPING: {
          title: 'Thợ săn quà lưu niệm',
          description: 'Bạn mang cả hành trình về nhà — {share} dành cho mua sắm.',
        },
        BEAUTY: {
          title: 'Người tỏa sáng',
          description:
            'Bạn rời đi với diện mạo rạng rỡ nhất — {share} của hành trình này dành cho làm đẹp.',
        },
        SHOW: {
          title: 'Du khách hàng ghế đầu',
          description: 'Bạn sống trọn khoảnh khắc — {share} của hành trình này dành cho biểu diễn.',
        },
        TRANSPORT: {
          title: 'Người đi khắp nơi',
          description: 'Bạn luôn di chuyển — {share} của hành trình này dành cho đi lại.',
        },
        STAY: {
          title: 'Du khách chậm rãi',
          description: 'Bạn tận hưởng nơi ở — {share} của hành trình này dành cho lưu trú.',
        },
        OTHER: {
          title: 'Người chi tiêu tự do',
          description:
            'Chi tiêu của bạn không gói gọn trong một ô — {share} nằm ngoài các danh mục thường gặp.',
        },
      },
      categoryTitle: 'Theo danh mục',
      categoryEmpty: 'Không có chi tiêu theo danh mục được ghi nhận.',
      categoryDescription: 'Chi tiêu gộp theo danh mục kèm số tiền và tỷ trọng.',
      category: 'Danh mục',
      amount: 'Số tiền',
      // 여기의 share는 정산의 몫이 아니라 비중(%)이다.
      share: 'Tỷ trọng',
      trendTitle: 'Xu hướng chi tiêu',
      trendEmpty: 'Không có dữ liệu chi tiêu theo ngày được ghi nhận.',
      trendDescription: 'Chi tiêu theo ngày trong suốt hành trình.',
      date: 'Ngày',
      status: 'Trạng thái báo cáo: {status}',
    },
    errorCode: {
      'REPORT-001': 'Không tìm thấy báo cáo này.',
      'REPORT-002': 'Bạn không có quyền xem báo cáo này.',
      'REPORT-003': 'Hãy kiểm tra yêu cầu báo cáo và thử lại.',
      'REPORT-004': 'Hành trình này chưa kết thúc.',
      'REPORT-005': 'Hành trình này đã có báo cáo tổng kết.',
      'REPORT-006': 'Không tìm thấy hành trình này.',
      'REPORT-007':
        'Chúng tôi không đưa được một khoản chi đã chọn vào báo cáo. Hãy làm mới danh sách và chọn lại.',
      'REPORT-008':
        'Một khoản chi đã chọn đang thuộc về hành trình khác. Hãy làm mới danh sách và chọn lại.',
    },
  },
}
