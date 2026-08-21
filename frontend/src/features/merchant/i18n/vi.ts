/**
 * Merchant 도메인 문구 (vi).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  merchant: {
    title: 'Cửa hàng',
    register: {
      heading: 'Thiết lập cửa hàng của bạn',
      description: 'Nhập tên cửa hàng. Khách hàng sẽ thấy tên này khi quét mã QR của bạn.',
      businessName: 'Tên cửa hàng',
      businessNamePlaceholder: 'VD: Blue Bottle Hongdae',
      irreversible:
        'Thao tác này không thể hoàn tác. Tài khoản cửa hàng không thể thanh toán hoặc sử dụng các tính năng dành cho du khách.',
      submit: 'Tạo cửa hàng',
      error: 'Chúng tôi không thiết lập được cửa hàng của bạn. Vui lòng thử lại.',
    },
    income: {
      heading: 'Thu nhập hôm nay',
      amount: '{amount} P',
      count: 'Chưa có thanh toán | 1 thanh toán | {count} thanh toán',
      empty: 'Các khoản thanh toán sẽ hiện ở đây khi khách hàng thanh toán.',
      error: 'Chúng tôi không tải được thu nhập của bạn.',
      retry: 'Thử lại',
    },
    qr: {
      heading: 'Thu tiền khách hàng',
      description: 'Thêm các mặt hàng khách mua. Tổng tiền sẽ được tính tự động.',
      itemName: 'Tên mặt hàng {index}',
      itemNamePlaceholder: 'VD: Americano đá',
      quantity: 'Số lượng mặt hàng {index}',
      unitPrice: 'Giá mặt hàng {index}',
      unitPricePlaceholder: '0',
      addItem: 'Thêm mặt hàng',
      remove: 'Xóa',
      removeItem: 'Xóa mặt hàng {index}',
      decreaseQuantity: 'Giảm số lượng mặt hàng {index}',
      increaseQuantity: 'Tăng số lượng mặt hàng {index}',
      subtotal: '{amount} P',
      total: 'Tổng cộng',
      totalAmount: '{amount} P',
      totalHint: 'Thêm ít nhất một mặt hàng có số lượng và giá.',
      memo: 'Ghi chú (không bắt buộc)',
      memoPlaceholder: 'VD: Bàn số 4',
      create: 'Hiện mã QR',
      createAnother: 'Mã QR mới',
      error: 'Chúng tôi không tạo được mã QR. Vui lòng thử lại.',
      validity: 'Hết hạn sau {time}',
      expired: 'Mã này đã hết hạn.',
      expiredAction: 'Tạo mã mới',
      imageAlt: 'Mã QR cho thanh toán này',
    },
    errorCode: {
      'MEMBER-006': 'Tên cửa hàng này không hợp lệ.',
      'MEMBER-009': 'Tài khoản này đã được đăng ký là cửa hàng.',
      'WALLET-001': 'Chúng tôi không tìm thấy ví của bạn.',
      'WALLET-017': 'Ví của bạn hiện không hoạt động.',
    },
  },
}
