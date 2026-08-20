package me.nawa.wallet.dto.request;

import java.math.BigDecimal;
import me.nawa.wallet.domain.enums.SpendingScope;

/**
 * QR 결제 실행 요청.
 *
 * `spendingCategory`는 결제자가 결제 직전에 고르는 소비 카테고리다. 가맹점 데이터에
 * 업종이 없어서 QR 생성 쪽에서는 값을 알 수 없고, 리포트가 쓰는 것도 가맹점 분류가
 * 아니라 결제자의 소비 성향이다. 고르지 않은 결제도 받아야 하므로 `null`을 허용하며
 * 서버가 `OTHER`로 접는다. 값 집합은 `SpendingCategory`가 정한다.
 */
public record QrPaymentExecuteRequest(
    String qrToken,
    BigDecimal amount,
    SpendingScope spendingScope,
    Long appointmentId,
    String spendingCategory
) {
}
