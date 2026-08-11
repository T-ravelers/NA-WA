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
public class QrPaymentResolveTarget {

    private Long qrPaymentCodeId;
    private Long payeeWalletId;
    private Long completedTransferId;
    private BigDecimal amount;
    private String memo;
    private QrPaymentStatus paymentStatus;
    private LocalDateTime expiresAt;

    private String payeeName;
    private String currencyCode;
    private String payeeWalletStatus;
}
