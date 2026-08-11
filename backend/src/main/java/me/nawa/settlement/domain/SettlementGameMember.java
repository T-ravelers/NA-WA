package me.nawa.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 게임형 정산의 참여자별 동의와 최종 부담자 여부를 표현한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementGameMember {
    private Long settlementId;
    private Long appointmentMemberId;
    private Long memberId;
    private String consentStatus;
    private Boolean liable;
}
