package me.nawa.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 후보 화면에서 사용하는 약속 참여자 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementParticipant {
    private Long appointmentMemberId;
    private Long memberId;
    private String displayName;
}
