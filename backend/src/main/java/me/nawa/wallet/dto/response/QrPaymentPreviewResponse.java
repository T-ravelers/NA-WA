package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.enums.SpendingScope;


// 중첩 dto
public record QrPaymentPreviewResponse(
    Long qrPaymentId,
    String payeeName,
    BigDecimal amount,
    BigDecimal currentBalance,
    BigDecimal balanceAfter,
    String currencyCode,
    SpendingScope spendingScope,
    TripInfo trip,
    AppointmentInfo appointment,
    boolean canPay,
    LocalDateTime expiresAt
) {

    public record TripInfo(
        Long tripId,
        String title
    ){
    }

    public record AppointmentInfo(
        Long appointmentId,
        String appointmentName
    ){
    }
}
