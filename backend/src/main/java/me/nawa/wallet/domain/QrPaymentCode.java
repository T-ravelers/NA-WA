package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.wallet.domain.enums.QrPaymentStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QrPaymentCode {

    private Long qrPaymentCodeId;

    //결제 요청을 생성한 사람의 지갑
    private Long payeeWalletId;

    //결제 완료 후 연결되는 walelt_transfers.transfer_id
    private Long completedTransferId;

    //QR에 담기는 백엔드 발급 토큰
    private String qrToken;

    //null이면 결제자가 금액 입력
    private BigDecimal amount;

    private String memo;

    private QrPaymentStatus paymentStatus;

    private LocalDateTime expiresAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
