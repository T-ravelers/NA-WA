package me.nawa.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// 공동 소비일 때 약속, 여행 정보를 담을 조회 모델
// 여러 태아불울 조인한 조회 결과를 받기 위한 전용 모델
public class QrPaymentAppointmentMembership {

    private Long appointmentMemberId;
    private Long appointmentId;
    private Long tripId;
    private String appointmentName;
    private String tripTitle;
}
