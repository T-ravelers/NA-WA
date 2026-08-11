package me.nawa.wallet.mapper;

import java.time.LocalDateTime;
import me.nawa.wallet.domain.QrPaymentAppointmentMembership;
import me.nawa.wallet.domain.QrPaymentCode;
import me.nawa.wallet.domain.QrPaymentResolveTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface  QrPaymentCodeMapper {

    void insert(QrPaymentCode qrPaymentCode);

    QrPaymentResolveTarget findResolveTargetByToken(
        @Param("qrToken") String qrToken
    );

    int markExpiredIfActive(
        @Param("qrPaymentCodeId") Long qrPaymentCodeId,
        @Param("now")LocalDateTime now
        );

    QrPaymentAppointmentMembership findActiveAppointmentMembership(
        @Param(value = "memberId") Long memberId,
        @Param("appointmentId") Long appointmentId
    );

}
